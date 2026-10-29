package com.utng.pagosservicios.proveedor.gestopago;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Plomeria HTTP comun a todas las llamadas a PuntoRed: encabezados
 * (Authorization, X-API-Key), cuerpo form-urlencoded, limite de tamano de la
 * respuesta y conversion de los 5xx en excepcion (para que el circuit breaker
 * los cuente como fallo).
 *
 * <p>No decide nada de negocio: devuelve el estado y los bytes y cada cliente
 * interpreta.
 */
final class ConectorGestopago {

    static final String ENCABEZADO_API_KEY = "X-API-Key";

    /** Una respuesta de transaccion mayor no es una respuesta legitima del proveedor. */
    static final int MAX_BYTES_RESPUESTA = 64 * 1024;

    private static final String ACEPTA = "application/xml, text/xml, application/json;q=0.9, */*;q=0.1";

    private final RestClient http;
    private final String urlBase;
    private final String apiKey;

    ConectorGestopago(RestClient http, GestopagoProperties propiedades) {
        this.http = http;
        this.urlBase = quitarDiagonalFinal(propiedades.urlBase());
        this.apiKey = propiedades.tieneApiKey() ? propiedades.apiKey().strip() : null;
    }

    /** POST application/x-www-form-urlencoded. token puede ser null (autenticacion). */
    RespuestaHttp postFormulario(String ruta, String formulario, String token) {
        RestClient.RequestBodySpec solicitud = http.post()
                .uri(ruta)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header(HttpHeaders.ACCEPT, ACEPTA);
        encabezados(solicitud, token);
        return ejecutar(solicitud.body(formulario), MAX_BYTES_RESPUESTA);
    }

    /** POST con los parametros en la URL, como el ejemplo de autenticacion de la documentacion. */
    RespuestaHttp postConParametrosEnUrl(String ruta, String formulario) {
        RestClient.RequestBodySpec solicitud = http.post()
                .uri(URI.create(urlBase + ruta + "?" + formulario))
                .header(HttpHeaders.ACCEPT, ACEPTA);
        encabezados(solicitud, null);
        return ejecutar(solicitud, MAX_BYTES_RESPUESTA);
    }

    RespuestaHttp get(String ruta, String token, int maxBytes) {
        RestClient.RequestHeadersSpec<?> solicitud = http.get()
                .uri(ruta)
                .header(HttpHeaders.ACCEPT, ACEPTA);
        encabezados(solicitud, token);
        return ejecutar(solicitud, maxBytes);
    }

    private void encabezados(RestClient.RequestHeadersSpec<?> solicitud, String token) {
        if (token != null) {
            solicitud.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        if (apiKey != null) {
            solicitud.header(ENCABEZADO_API_KEY, apiKey);
        }
    }

    private static RespuestaHttp ejecutar(RestClient.RequestHeadersSpec<?> solicitud, int maxBytes) {
        RespuestaHttp respuesta = solicitud.exchangeForRequiredValue((peticion, respuestaHttp) -> new RespuestaHttp(
                respuestaHttp.getStatusCode().value(), leerCuerpo(respuestaHttp.getBody(), maxBytes)));
        if (respuesta.status() >= 500) {
            throw new FalloServidorProveedorException(respuesta.status());
        }
        return respuesta;
    }

    static byte[] leerCuerpo(InputStream entrada, int maxBytes) throws IOException {
        if (entrada == null) {
            return new byte[0];
        }
        byte[] bytes = entrada.readNBytes(maxBytes + 1);
        if (bytes.length > maxBytes) {
            throw new IOException("La respuesta del proveedor excede " + maxBytes + " bytes");
        }
        return bytes;
    }

    /** Codifica los campos como application/x-www-form-urlencoded, en el orden dado. */
    static String formulario(Map<String, String> campos) {
        StringJoiner cuerpo = new StringJoiner("&");
        campos.forEach((nombre, valor) -> cuerpo.add(URLEncoder.encode(nombre, StandardCharsets.UTF_8) + "="
                + URLEncoder.encode(valor == null ? "" : valor, StandardCharsets.UTF_8)));
        return cuerpo.toString();
    }

    private static String quitarDiagonalFinal(String url) {
        String limpia = url.strip();
        return limpia.endsWith("/") ? limpia.substring(0, limpia.length() - 1) : limpia;
    }
}
