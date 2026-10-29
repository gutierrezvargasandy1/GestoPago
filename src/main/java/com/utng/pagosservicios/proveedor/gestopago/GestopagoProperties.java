package com.utng.pagosservicios.proveedor.gestopago;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;

/**
 * Configuracion de PuntoRed (GestoPago). Los secretos llegan por variables de
 * entorno y todos los {@code toString()} estan sobrescritos: el toString
 * generado de un record imprimiria el token, la API key o la llave AES completos
 * si alguien registrara este objeto en un log.
 */
@Validated
@ConfigurationProperties(prefix = "gestopago")
public record GestopagoProperties(
        @NotBlank String urlBase,
        String token,
        String apiKey,
        @NotNull @Valid Autenticacion autenticacion,
        @NotNull @Valid Cifrado cifrado,
        @NotBlank @Size(min = 3, max = 25) String unidad,
        @NotBlank String zonaHoraria,
        @NotNull @Valid Endpoints endpoints,
        @NotNull @Valid Timeouts timeout,
        @NotNull Codigos codigos,
        Catalogo catalogo) {

    private static final String OCULTO = "****";

    public GestopagoProperties {
        if (zonaHoraria != null) {
            ZoneId.of(zonaHoraria); // falla al arrancar si la zona no existe
        }
        // Sin configuracion de catalogo, la modalidad la decide solo el precio.
        catalogo = catalogo == null ? new Catalogo(List.of()) : catalogo;
    }

    public boolean usaTokenEstatico() {
        return tieneTexto(token);
    }

    public boolean tieneApiKey() {
        return tieneTexto(apiKey);
    }

    /** Zona en la que se expresa horaLocal (yyyyMMddHHmmss) en las operaciones. */
    public ZoneId zona() {
        return ZoneId.of(zonaHoraria);
    }

    @Override
    public String toString() {
        return "GestopagoProperties[urlBase=" + urlBase
                + ", token=" + (usaTokenEstatico() ? OCULTO : "(no configurado)")
                + ", apiKey=" + (tieneApiKey() ? OCULTO : "(no configurada)")
                + ", autenticacion=" + autenticacion
                + ", cifrado=" + cifrado
                + ", unidad=" + unidad
                + ", zonaHoraria=" + zonaHoraria
                + ", endpoints=" + endpoints
                + ", timeout=" + timeout
                + ", codigos=" + codigos
                + ", catalogo=" + catalogo + "]";
    }

    static boolean tieneTexto(String valor) {
        return valor != null && !valor.isBlank();
    }

    /**
     * Credenciales de distribuidor para obtener el token (vigencia de 24 h segun
     * el proveedor, que pide tomar uno por dia).
     *
     * @param credencialesEnUrl el ejemplo de la documentacion las manda en la URL;
     *                          por defecto viajan en el cuerpo (form-urlencoded)
     *                          para que no queden en logs de servidores intermedios
     */
    public record Autenticacion(String idDistribuidor, String codigoDispositivo, String password,
                                @NotNull Duration vigenciaToken, boolean credencialesEnUrl) {

        public boolean configurada() {
            return tieneTexto(idDistribuidor) && tieneTexto(codigoDispositivo) && tieneTexto(password);
        }

        @Override
        public String toString() {
            return "Autenticacion[configurada=" + configurada() + ", idDistribuidor=" + OCULTO
                    + ", codigoDispositivo=" + OCULTO + ", password=" + OCULTO
                    + ", vigenciaToken=" + vigenciaToken + ", credencialesEnUrl=" + credencialesEnUrl + "]";
        }
    }

    /** Llave AES-256 y vector de inicializacion, en base64, que entrega PuntoRed. */
    public record Cifrado(String llave, String iv) {

        public boolean configurado() {
            return tieneTexto(llave) && tieneTexto(iv);
        }

        @Override
        public String toString() {
            return "Cifrado[configurado=" + configurado() + ", llave=" + OCULTO + ", iv=" + OCULTO + "]";
        }
    }

    /**
     * @param consulta ruta de confirmTx. Vacia: las operaciones dudosas pasan a
     *                 revision manual en lugar de consultarse.
     */
    public record Endpoints(@NotBlank String envio, @NotBlank String autenticacion, @NotBlank String catalogo,
                            String consulta) {

        public boolean tieneConsulta() {
            return tieneTexto(consulta);
        }
    }

    public record Timeouts(@NotNull Duration conexion, @NotNull Duration lectura) {
    }

    /**
     * Clasificacion de los codigos del proveedor (MENSAJE/CODIGO). Lo que no este
     * en ninguna lista es DESCONOCIDO y se trata como resultado indeterminado.
     *
     * @param aprobada           sendTx aplicada (01)
     * @param rechazada          sendTx no aplicada; se devuelve el dinero (02, saldo insuficiente)
     * @param duplicada          sendTx rechazada por duplicidad (06)
     * @param sinRespuesta       el proveedor no obtuvo respuesta del carrier (82); se concilia
     * @param consultaAplicada   confirmTx: la operacion si se aplico
     * @param consultaNoAplicada confirmTx: la operacion no se aplico. Vacia hasta conocer el codigo real.
     */
    public record Codigos(List<String> aprobada, List<String> rechazada, List<String> duplicada,
                          List<String> sinRespuesta, List<String> consultaAplicada,
                          List<String> consultaNoAplicada) {

        public Codigos {
            aprobada = copia(aprobada);
            rechazada = copia(rechazada);
            duplicada = copia(duplicada);
            sinRespuesta = copia(sinRespuesta);
            consultaAplicada = copia(consultaAplicada);
            consultaNoAplicada = copia(consultaNoAplicada);
        }

        private static List<String> copia(List<String> valores) {
            return valores == null ? List.of() : List.copyOf(valores);
        }
    }

    /**
     * @param tiposFrontPagoServicio valores de tipoFront que identifican un pago de
     *                               servicio aunque traigan precio. Vacia: decide el precio
     *                               (precio > 0 = recarga de monto fijo; 0 = pago de servicio).
     */
    public record Catalogo(List<Integer> tiposFrontPagoServicio) {

        public Catalogo {
            tiposFrontPagoServicio = tiposFrontPagoServicio == null ? List.of() : List.copyOf(tiposFrontPagoServicio);
        }
    }
}
