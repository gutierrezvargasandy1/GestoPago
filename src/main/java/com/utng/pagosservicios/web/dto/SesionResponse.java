package com.utng.pagosservicios.web.dto;

import com.utng.pagosservicios.servicio.AutenticacionService.SesionIniciada;

import java.time.Instant;

public record SesionResponse(String accessToken, String tokenType, long expiraEnSegundos, String plataforma,
                             Instant expiraEn) {

    public static SesionResponse de(SesionIniciada sesion) {
        return new SesionResponse(sesion.token().valor(), "Bearer", sesion.token().vigencia().toSeconds(),
                sesion.plataforma().name(), sesion.expiraEn());
    }

    @Override
    public String toString() {
        return "SesionResponse[****, plataforma=" + plataforma + ", expiraEn=" + expiraEn + "]";
    }
}
