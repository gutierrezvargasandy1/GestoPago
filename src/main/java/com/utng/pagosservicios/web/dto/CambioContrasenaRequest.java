package com.utng.pagosservicios.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Cambio de contrasena: se exige la actual. La nueva sigue las mismas reglas que al registrarse. */
public record CambioContrasenaRequest(
        @NotBlank(message = "Escribe tu contraseña actual")
        @Size(max = 200, message = "Contraseña demasiado larga")
        String actual,

        @NotBlank(message = "Escribe la nueva contraseña")
        @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                message = "La contraseña debe incluir al menos una letra y un número")
        String nueva) {

    @Override
    public String toString() {
        return "CambioContrasenaRequest[actual=****, nueva=****]";
    }
}
