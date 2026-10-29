package com.utng.pagosservicios.web.dto;

public record TokenResponse(String accessToken, String tokenType, long expiraEnSegundos) {

    @Override
    public String toString() {
        return "TokenResponse[****, tokenType=" + tokenType + ", expiraEnSegundos=" + expiraEnSegundos + "]";
    }
}
