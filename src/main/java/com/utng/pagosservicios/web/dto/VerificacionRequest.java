package com.utng.pagosservicios.web.dto;

import com.utng.pagosservicios.dominio.cliente.Plataforma;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Segundo paso del inicio de sesion.
 *
 * @param forzar true si el cliente acepto cerrar la sesion abierta en la otra plataforma
 */
public record VerificacionRequest(
        @NotBlank(message = "Falta la verificación del primer paso") @Size(max = 2000) String desafio,
        @NotBlank(message = "El código es obligatorio")
        @Pattern(regexp = "^[0-9]{6}$", message = "El código debe tener 6 dígitos") String codigo,
        @NotNull(message = "La plataforma es obligatoria (WEB o MOVIL)") Plataforma plataforma,
        boolean forzar) {

    @Override
    public String toString() {
        return "VerificacionRequest[desafio=****, codigo=****, plataforma=" + plataforma + ", forzar=" + forzar + "]";
    }
}
