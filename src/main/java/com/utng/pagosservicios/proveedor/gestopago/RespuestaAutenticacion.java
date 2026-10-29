package com.utng.pagosservicios.proveedor.gestopago;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Respuesta de /authenticate/. PuntoRed documenta {"token": "...", "success": true};
 * tambien se aceptan las formas habituales (access_token, jwt, expires_in) por si
 * el proveedor cambia de formato.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RespuestaAutenticacion(
        String token,
        @JsonProperty("access_token") String accessToken,
        String jwt,
        @JsonProperty("expires_in") Long expiresIn,
        Boolean success) {

    String tokenDisponible() {
        if (conTexto(token)) {
            return token.strip();
        }
        if (conTexto(accessToken)) {
            return accessToken.strip();
        }
        return conTexto(jwt) ? jwt.strip() : null;
    }

    private static boolean conTexto(String valor) {
        return valor != null && !valor.isBlank();
    }

    @Override
    public String toString() {
        return "RespuestaAutenticacion[****, expiresIn=" + expiresIn + ", success=" + success + "]";
    }
}
