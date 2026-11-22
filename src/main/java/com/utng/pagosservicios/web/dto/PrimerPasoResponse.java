package com.utng.pagosservicios.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.utng.pagosservicios.servicio.AutenticacionService.PrimerPaso;

/**
 * Respuesta a usuario y contrasena correctos. No da acceso: falta el codigo.
 * secreto y otpauthUri solo vienen la primera vez, para registrar la app autenticadora.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PrimerPasoResponse(String desafio, long expiraEnSegundos, boolean requiereConfiguracion,
                                 String secreto, String otpauthUri) {

    public static PrimerPasoResponse de(PrimerPaso paso) {
        return new PrimerPasoResponse(paso.desafio().valor(), paso.desafio().vigencia().toSeconds(),
                paso.requiereConfiguracion(), paso.secreto(), paso.otpauthUri());
    }

    @Override
    public String toString() {
        return "PrimerPasoResponse[desafio=****, requiereConfiguracion=" + requiereConfiguracion + "]";
    }
}
