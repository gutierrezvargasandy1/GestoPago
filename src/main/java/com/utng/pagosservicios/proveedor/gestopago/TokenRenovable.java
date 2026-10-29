package com.utng.pagosservicios.proveedor.gestopago;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.utng.pagosservicios.proveedor.BitacoraIntercambios;
import com.utng.pagosservicios.proveedor.BitacoraIntercambios.Intercambio;
import com.utng.pagosservicios.proveedor.BitacoraIntercambios.Operacion;
import com.utng.pagosservicios.util.Trazabilidad;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

/**
 * Obtiene el token con las credenciales de distribuidor (POST
 * /sistema/app/jwt-gp/authenticate/) y lo reutiliza hasta poco antes de que
 * venza. PuntoRed emite tokens de 24 h y pide tomar uno por dia: pedir uno por
 * llamada no esta permitido.
 *
 * <p>El token solo vive en memoria: nunca se escribe a disco, a la bitacora ni
 * a los logs. Si el proveedor rechaza las credenciales, no se reintenta en
 * rafaga: entre dos autenticaciones pasan al menos {@link #ESPERA_ENTRE_AUTENTICACIONES}.
 */
final class TokenRenovable implements CredencialGestopago {

    private static final Logger log = LoggerFactory.getLogger(TokenRenovable.class);
    private static final Duration MARGEN_RENOVACION = Duration.ofMinutes(5);
    static final Duration ESPERA_ENTRE_AUTENTICACIONES = Duration.ofSeconds(30);
    private static final Pattern FORMATO_JWT = Pattern.compile("^[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]*$");

    private final ConectorGestopago conector;
    private final JsonMapper json;
    private final String ruta;
    private final GestopagoProperties.Autenticacion credenciales;
    private final BitacoraIntercambios bitacora;
    private final Clock reloj;
    private final ReentrantLock candado = new ReentrantLock();

    private volatile TokenVigente vigente;
    private volatile Instant ultimoIntento;

    private record TokenVigente(String valor, Instant expira) {
        @Override
        public String toString() {
            return "TokenVigente[****, expira=" + expira + "]";
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CargaJwt(Long exp) {
    }

    TokenRenovable(ConectorGestopago conector, JsonMapper json, String ruta,
                   GestopagoProperties.Autenticacion credenciales, BitacoraIntercambios bitacora, Clock reloj) {
        this.conector = conector;
        this.json = json;
        this.ruta = ruta;
        this.credenciales = credenciales;
        this.bitacora = bitacora;
        this.reloj = reloj;
    }

    @Override
    public String token() {
        TokenVigente actual = vigente;
        if (actual != null && sigueVigente(actual)) {
            return actual.valor();
        }
        candado.lock();
        try {
            // Otro hilo pudo renovarlo mientras se esperaba el candado.
            actual = vigente;
            if (actual == null || !sigueVigente(actual)) {
                actual = solicitarToken();
                vigente = actual;
            }
            return actual.valor();
        } finally {
            candado.unlock();
        }
    }

    @Override
    public void invalidar() {
        vigente = null;
    }

    @Override
    public boolean renovable() {
        return true;
    }

    private boolean sigueVigente(TokenVigente token) {
        return reloj.instant().plus(MARGEN_RENOVACION).isBefore(token.expira());
    }

    private TokenVigente solicitarToken() {
        Instant ahora = reloj.instant();
        Instant anterior = ultimoIntento;
        if (anterior != null && ahora.isBefore(anterior.plus(ESPERA_ENTRE_AUTENTICACIONES))) {
            throw new IllegalStateException("Hubo un intento de autenticacion con Gestopago hace menos de "
                    + ESPERA_ENTRE_AUTENTICACIONES.toSeconds() + " s; no se repite todavia");
        }
        ultimoIntento = ahora;

        Map<String, String> campos = new LinkedHashMap<>();
        campos.put("idDistribuidor", credenciales.idDistribuidor().strip());
        campos.put("codigoDispositivo", credenciales.codigoDispositivo().strip());
        campos.put("password", credenciales.password());
        String formulario = ConectorGestopago.formulario(campos);

        long inicio = System.nanoTime();
        RespuestaHttp respuesta;
        try {
            respuesta = credenciales.credencialesEnUrl()
                    ? conector.postConParametrosEnUrl(ruta, formulario)
                    : conector.postFormulario(ruta, formulario, null);
        } catch (RuntimeException e) {
            registrar(null, ClasificadorFallos.describir(e), inicio, ahora);
            throw new IllegalStateException("No se pudo contactar el servicio de autenticacion de Gestopago ("
                    + ClasificadorFallos.describir(e) + ")");
        }
        // La respuesta trae el token: en la bitacora solo queda el estado HTTP.
        registrar(respuesta.status(), null, inicio, ahora);

        if (!respuesta.exitosa()) {
            throw new IllegalStateException("La autenticacion con Gestopago respondio HTTP " + respuesta.status());
        }
        String cuerpo = respuesta.texto().strip();
        RespuestaAutenticacion datos = leer(cuerpo);
        if (datos != null && Boolean.FALSE.equals(datos.success())) {
            throw new IllegalStateException("Gestopago rechazo las credenciales de distribuidor (success=false)");
        }
        String token = datos != null ? datos.tokenDisponible() : null;
        if (token == null && FORMATO_JWT.matcher(cuerpo).matches()) {
            token = cuerpo; // el proveedor devolvio el JWT como texto plano
        }
        if (token == null) {
            throw new IllegalStateException("La respuesta de autenticacion de Gestopago no contiene un token reconocible");
        }
        Instant expira = calcularExpiracion(token, datos, ahora);
        log.info("Token de Gestopago obtenido; se renovara antes de {}", expira);
        return new TokenVigente(token, expira);
    }

    private void registrar(Integer status, String error, long inicio, Instant ahora) {
        try {
            bitacora.registrar(new Intercambio(Operacion.AUTENTICACION, null, 1, null, status, null, null, error,
                    (System.nanoTime() - inicio) / 1_000_000, ahora, MDC.get(Trazabilidad.CLAVE_MDC)));
        } catch (RuntimeException e) {
            log.warn("No se pudo registrar en la bitacora la autenticacion ({})", ClasificadorFallos.describir(e));
        }
    }

    private RespuestaAutenticacion leer(String cuerpo) {
        try {
            return json.readValue(cuerpo, RespuestaAutenticacion.class);
        } catch (RuntimeException e) { // en Jackson 3 las excepciones de lectura son unchecked
            return null;
        }
    }

    /**
     * Se usa la expiracion mas cercana entre expires_in, el claim exp del JWT y
     * la vigencia configurada: el token se renueva antes de que el proveedor lo
     * considere vencido.
     */
    private Instant calcularExpiracion(String token, RespuestaAutenticacion datos, Instant ahora) {
        Instant expira = ahora.plus(credenciales.vigenciaToken());
        if (datos != null && datos.expiresIn() != null && datos.expiresIn() > 0) {
            expira = min(expira, ahora.plusSeconds(datos.expiresIn()));
        }
        String[] partes = token.split("\\.");
        if (partes.length >= 2) {
            try {
                byte[] carga = Base64.getUrlDecoder().decode(partes[1]);
                CargaJwt claims = json.readValue(new String(carga, StandardCharsets.UTF_8), CargaJwt.class);
                if (claims != null && claims.exp() != null) {
                    expira = min(expira, Instant.ofEpochSecond(claims.exp()));
                }
            } catch (RuntimeException e) {
                log.debug("El token de Gestopago no trae un claim exp legible; se usa la vigencia configurada");
            }
        }
        return expira;
    }

    private static Instant min(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }
}
