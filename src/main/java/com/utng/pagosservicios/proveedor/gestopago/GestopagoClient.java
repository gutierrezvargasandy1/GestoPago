package com.utng.pagosservicios.proveedor.gestopago;

import com.utng.pagosservicios.proveedor.BitacoraIntercambios;
import com.utng.pagosservicios.proveedor.BitacoraIntercambios.Intercambio;
import com.utng.pagosservicios.proveedor.BitacoraIntercambios.Operacion;
import com.utng.pagosservicios.proveedor.DatosCanje;
import com.utng.pagosservicios.proveedor.OrdenPago;
import com.utng.pagosservicios.proveedor.ProveedorPagos;
import com.utng.pagosservicios.proveedor.ResultadoConsulta;
import com.utng.pagosservicios.proveedor.ResultadoEnvio;
import com.utng.pagosservicios.util.Trazabilidad;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import tools.jackson.databind.json.JsonMapper;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Adaptador hacia PuntoRed (GestoPago). Toda la incertidumbre de la red se
 * resuelve aqui y se entrega al servicio como un desenlace explicito: el
 * servicio no sabe que existen HTTP, AES, XML, Resilience4j ni los codigos del
 * proveedor.
 *
 * <p>Contrato del proveedor: POST form-urlencoded con dos campos, "random" (una
 * cadena distinta en cada peticion) y "signed" (el JSON de la operacion cifrado
 * con AES-256-CBC y codificado en base64). La respuesta es XML.
 *
 * <p>Reglas:
 * <ul>
 *   <li>sendTx nunca se reintenta tras una respuesta o un fallo que pudo
 *       procesarse: reintentar un cargo cuyo resultado se desconoce es la forma
 *       clasica de cobrar dos veces. La unica repeticion es tras un 401/403 (el
 *       proveedor rechazo la credencial antes de procesar), con token nuevo y el
 *       mismo UPC.</li>
 *   <li>01 = aprobada; 02 = rechazada; 06 = duplicada (ver {@link #interpretarEnvio}).</li>
 *   <li>82, 5xx, timeout de lectura, conexion cortada, respuesta ilegible o
 *       codigo desconocido = INDETERMINADA: se concilia con confirmTx.</li>
 *   <li>Circuito abierto, conexion imposible o 4xx = NO_PROCESADA.</li>
 * </ul>
 */
public class GestopagoClient implements ProveedorPagos {

    private static final Logger log = LoggerFactory.getLogger(GestopagoClient.class);

    /** El segundo intento solo existe tras un rechazo de credencial con token renovable. */
    private static final int INTENTOS_MAXIMOS = 2;
    private static final int MAX_CARACTERES_BITACORA = 8_000;

    private final ConectorGestopago conector;
    private final JsonMapper json;
    private final GestopagoProperties propiedades;
    private final CircuitBreaker circuito;
    private final CredencialGestopago credencial;
    private final CifradorAes cifrador;
    private final CodigosGestopago codigos;
    private final BitacoraIntercambios bitacora;
    private final Clock reloj;
    private final AtomicInteger secuencia = new AtomicInteger(new SecureRandom().nextInt(10_000));

    GestopagoClient(ConectorGestopago conector, JsonMapper json, GestopagoProperties propiedades,
                    CircuitBreaker circuito, CredencialGestopago credencial, CifradorAes cifrador,
                    BitacoraIntercambios bitacora, Clock reloj) {
        this.conector = conector;
        this.json = json;
        this.propiedades = propiedades;
        this.circuito = circuito;
        this.credencial = credencial;
        this.cifrador = cifrador;
        this.codigos = new CodigosGestopago(propiedades.codigos());
        this.bitacora = bitacora;
        this.reloj = reloj;
    }

    @Override
    public boolean disponible() {
        CircuitBreaker.State estado = circuito.getState();
        return estado != CircuitBreaker.State.OPEN && estado != CircuitBreaker.State.FORCED_OPEN;
    }

    @Override
    public ResultadoEnvio enviar(OrdenPago orden) {
        Optional<Mensaje> mensaje = preparar(orden);
        if (mensaje.isEmpty()) {
            // No se llego a enviar nada: es seguro devolver el dinero.
            return ResultadoEnvio.noProcesada("SOLICITUD_INVALIDA");
        }
        for (int intento = 1; ; intento++) {
            Llamada llamada = llamar(Operacion.ENVIO, propiedades.endpoints().envio(), mensaje.get(), orden, intento);
            if (llamada.sinCredencial()) {
                // Sin token no se llamo a sendTx (y si es el segundo intento, el primero fue un 401/403).
                return ResultadoEnvio.noProcesada("SIN_CREDENCIAL");
            }
            if (llamada.fallo() != null) {
                return clasificarFalloDeEnvio(orden, llamada.fallo());
            }
            RespuestaHttp respuesta = llamada.respuesta();
            if (respuesta.credencialRechazada()) {
                credencial.invalidar();
                if (intento < INTENTOS_MAXIMOS && credencial.renovable()) {
                    log.warn("Gestopago rechazo la credencial (HTTP {}) en la orden {}: se repite con token nuevo",
                            respuesta.status(), orden.referenciaExterna());
                    continue;
                }
                log.error("Gestopago rechazo la credencial (HTTP {}); la orden {} no se proceso",
                        respuesta.status(), orden.referenciaExterna());
                return ResultadoEnvio.noProcesada("HTTP_" + respuesta.status());
            }
            return interpretarEnvio(respuesta, llamada.leida(), intento > 1);
        }
    }

    @Override
    public ResultadoConsulta consultar(OrdenPago orden) {
        if (!propiedades.endpoints().tieneConsulta()) {
            return ResultadoConsulta.noSoportada();
        }
        Optional<Mensaje> mensaje = preparar(orden);
        if (mensaje.isEmpty()) {
            return ResultadoConsulta.indeterminada("SOLICITUD_INVALIDA");
        }
        for (int intento = 1; ; intento++) {
            Llamada llamada = llamar(Operacion.CONSULTA, propiedades.endpoints().consulta(), mensaje.get(), orden,
                    intento);
            if (llamada.sinCredencial()) {
                return ResultadoConsulta.indeterminada("SIN_CREDENCIAL");
            }
            if (llamada.fallo() != null) {
                return ResultadoConsulta.indeterminada(ClasificadorFallos.describir(llamada.fallo()));
            }
            RespuestaHttp respuesta = llamada.respuesta();
            if (respuesta.credencialRechazada()) {
                credencial.invalidar();
                if (intento < INTENTOS_MAXIMOS && credencial.renovable()) {
                    continue;
                }
                return ResultadoConsulta.indeterminada("HTTP_" + respuesta.status());
            }
            return interpretarConsulta(respuesta, llamada.leida());
        }
    }

    /** JSON de la operacion, ya cifrado. Se prepara una vez: un reintento envia exactamente lo mismo. */
    private Optional<Mensaje> preparar(OrdenPago orden) {
        try {
            SolicitudTransaccion solicitud = SolicitudTransaccion.de(orden, propiedades.unidad(), propiedades.zona());
            String firmado = cifrador.cifrar(json.writeValueAsString(solicitud));
            return Optional.of(new Mensaje(firmado, json.writeValueAsString(solicitud.enmascarada())));
        } catch (RuntimeException e) {
            log.error("No se pudo preparar la orden {} para Gestopago ({})", orden.referenciaExterna(),
                    ClasificadorFallos.describir(e));
            return Optional.empty();
        }
    }

    private Llamada llamar(Operacion operacion, String ruta, Mensaje mensaje, OrdenPago orden, int intento) {
        String token;
        try {
            token = credencial.token();
        } catch (RuntimeException e) {
            log.error("No se obtuvo la credencial de Gestopago para la orden {} ({})",
                    orden.referenciaExterna(), ClasificadorFallos.describir(e));
            return Llamada.sinToken();
        }
        Map<String, String> campos = new LinkedHashMap<>();
        campos.put("random", nuevoRandom());
        campos.put("signed", mensaje.firmado());
        String formulario = ConectorGestopago.formulario(campos);

        Instant iniciado = reloj.instant();
        long inicio = System.nanoTime();
        RespuestaHttp respuesta;
        try {
            respuesta = circuito.executeSupplier(() -> conector.postFormulario(ruta, formulario, token));
        } catch (RuntimeException e) {
            registrar(operacion, orden, intento, mensaje, null, null, null, ClasificadorFallos.describir(e),
                    inicio, iniciado);
            return Llamada.conFallo(e);
        }
        // Fuera del try: un problema al leer o registrar no debe confundirse con un fallo de red.
        Optional<RespuestaPuntoRed> leida = respuesta.exitosa()
                ? RespuestaPuntoRed.leer(respuesta.cuerpo())
                : Optional.empty();
        registrar(operacion, orden, intento, mensaje, respuesta.status(),
                leida.map(RespuestaPuntoRed::codigo).orElse(null), cuerpoParaBitacora(respuesta), null,
                inicio, iniciado);
        return Llamada.con(respuesta, leida);
    }

    private ResultadoEnvio clasificarFalloDeEnvio(OrdenPago orden, RuntimeException error) {
        String detalle = ClasificadorFallos.describir(error);
        if (ClasificadorFallos.noLlegoAlProveedor(error)) {
            log.warn("La orden {} no llego al proveedor ({})", orden.referenciaExterna(), detalle);
            return ResultadoEnvio.noProcesada(detalle);
        }
        log.warn("Resultado indeterminado para la orden {} ({}): se conciliara", orden.referenciaExterna(), detalle);
        return ResultadoEnvio.indeterminada(detalle);
    }

    /**
     * Sobre el 06 (duplicada): el proveedor rechaza una operacion identica a otra
     * aplicada en los ultimos 15 min (recargas) o 24 h (servicios). Como cada
     * operacion propia lleva un UPC nuevo que nunca se reenvia, el 06 significa
     * que ESTA no se aplico (la otra es un pago distinto) y se devuelve el dinero.
     * La excepcion es un 06 en la repeticion tras un 401/403: ahi podria referirse
     * al primer envio de este mismo UPC, asi que se concilia.
     */
    private ResultadoEnvio interpretarEnvio(RespuestaHttp respuesta, Optional<RespuestaPuntoRed> leida,
                                            boolean reintentado) {
        int status = respuesta.status();
        if (status == 409) {
            // Un conflicto puede significar "ya tengo una operacion asi": hay que consultar.
            return ResultadoEnvio.indeterminada("HTTP_409");
        }
        if (status >= 400 && status < 500) {
            return ResultadoEnvio.noProcesada("HTTP_" + status);
        }
        if (!respuesta.exitosa()) {
            return ResultadoEnvio.indeterminada("HTTP_" + status);
        }
        if (leida.isEmpty()) {
            return ResultadoEnvio.indeterminada("RESPUESTA_ILEGIBLE");
        }
        RespuestaPuntoRed transaccion = leida.get();
        String codigo = codigoSeguro(transaccion.codigo());
        return switch (codigos.clasificarEnvio(transaccion.codigo())) {
            case APROBADA -> ResultadoEnvio.aprobada(codigo, sanear(transaccion.idTransaccion(), 80),
                    sanear(transaccion.autorizacion(), 80), canje(transaccion));
            case RECHAZADA -> ResultadoEnvio.rechazada(codigo);
            case DUPLICADA -> reintentado
                    ? ResultadoEnvio.indeterminada("CODIGO_" + codigo + "_TRAS_REINTENTO")
                    : ResultadoEnvio.duplicada(codigo);
            case SIN_RESPUESTA, DESCONOCIDA -> ResultadoEnvio.indeterminada("CODIGO_" + codigo);
        };
    }

    private ResultadoConsulta interpretarConsulta(RespuestaHttp respuesta, Optional<RespuestaPuntoRed> leida) {
        // En la consulta un 404 NO se toma como "no aplicada": tambien podria ser
        // una ruta mal configurada, y reversar con esa base podria perder dinero.
        if (!respuesta.exitosa()) {
            return ResultadoConsulta.indeterminada("HTTP_" + respuesta.status());
        }
        if (leida.isEmpty()) {
            return ResultadoConsulta.indeterminada("RESPUESTA_ILEGIBLE");
        }
        RespuestaPuntoRed transaccion = leida.get();
        String codigo = codigoSeguro(transaccion.codigo());
        return switch (codigos.clasificarConsulta(transaccion.codigo())) {
            case APLICADA -> ResultadoConsulta.aprobada(codigo, sanear(transaccion.idTransaccion(), 80),
                    sanear(transaccion.autorizacion(), 80), canje(transaccion));
            case NO_APLICADA -> ResultadoConsulta.noEncontrada(codigo);
            case DESCONOCIDA -> ResultadoConsulta.indeterminada("CODIGO_" + codigo);
        };
    }

    private static DatosCanje canje(RespuestaPuntoRed respuesta) {
        return DatosCanje.de(sanear(respuesta.pin(), 100), textoLibre(respuesta.leyenda(), 1000));
    }

    /** La bitacora nunca interrumpe el cobro, aunque una implementacion no cumpla su contrato y lance. */
    private void registrar(Operacion operacion, OrdenPago orden, int intento, Mensaje mensaje, Integer status,
                           String codigo, String respuesta, String error, long inicio, Instant iniciado) {
        try {
            bitacora.registrar(new Intercambio(operacion, orden.referenciaExterna(), intento, mensaje.paraBitacora(),
                    status, codigo == null ? null : codigoSeguro(codigo), respuesta, error,
                    (System.nanoTime() - inicio) / 1_000_000, iniciado, MDC.get(Trazabilidad.CLAVE_MDC)));
        } catch (RuntimeException e) {
            log.warn("No se pudo registrar en la bitacora la orden {} ({})", orden.referenciaExterna(),
                    ClasificadorFallos.describir(e));
        }
    }

    private static String cuerpoParaBitacora(RespuestaHttp respuesta) {
        String texto = RespuestaPuntoRed.paraBitacora(respuesta.texto());
        if (texto == null || texto.isBlank()) {
            return null;
        }
        return texto.length() > MAX_CARACTERES_BITACORA ? texto.substring(0, MAX_CARACTERES_BITACORA) : texto;
    }

    /**
     * "random": cadena distinta en cada peticion. La documentacion sugiere una
     * marca de tiempo; se le agrega un consecutivo para que dos peticiones en el
     * mismo milisegundo tampoco coincidan.
     */
    private String nuevoRandom() {
        return String.format(Locale.ROOT, "%d%04d", reloj.millis(), Math.floorMod(secuencia.getAndIncrement(), 10_000));
    }

    private static String codigoSeguro(String codigo) {
        return Objects.requireNonNullElse(sanear(codigo, 20), "?");
    }

    /**
     * Los datos que vienen del proveedor se limpian antes de guardarlos o
     * registrarlos: se evita desbordar las columnas y la inyeccion en logs.
     */
    static String sanear(String valor, int longitudMaxima) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.strip().replaceAll("[^A-Za-z0-9_./-]", "");
        if (limpio.isEmpty()) {
            return null;
        }
        return limpio.length() > longitudMaxima ? limpio.substring(0, longitudMaxima) : limpio;
    }

    /** Texto libre (leyendas): sin caracteres de control y recortado. */
    static String textoLibre(String valor, int longitudMaxima) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.replaceAll("\\p{Cntrl}+", " ").strip();
        if (limpio.isEmpty()) {
            return null;
        }
        return limpio.length() > longitudMaxima ? limpio.substring(0, longitudMaxima) : limpio;
    }

    /** Lo que se envia: el campo cifrado y su version enmascarada para la bitacora. */
    private record Mensaje(String firmado, String paraBitacora) {
        @Override
        public String toString() {
            return "Mensaje[****]";
        }
    }

    /** Resultado de una llamada: respuesta HTTP, fallo de red o falta de token. */
    private record Llamada(RespuestaHttp respuesta, Optional<RespuestaPuntoRed> leida, RuntimeException fallo,
                           boolean sinCredencial) {

        static Llamada con(RespuestaHttp respuesta, Optional<RespuestaPuntoRed> leida) {
            return new Llamada(respuesta, leida, null, false);
        }

        static Llamada conFallo(RuntimeException fallo) {
            return new Llamada(null, Optional.empty(), fallo, false);
        }

        static Llamada sinToken() {
            return new Llamada(null, Optional.empty(), null, true);
        }
    }
}
