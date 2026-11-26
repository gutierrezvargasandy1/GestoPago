package com.utng.pagosservicios.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Nuevo correo (es el usuario). Se confirma con la contrasena actual. */
public record CambioCorreoRequest(
        @NotBlank(message = "El correo es obligatorio")
        @Size(max = 100, message = "El correo es demasiado largo (máximo 100 caracteres)")
        @Email(message = "Escribe un correo electrónico válido")
        @Pattern(regexp = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$", message = "Escribe un correo electrónico válido")
        String correo,

        @NotBlank(message = "Escribe tu contraseña actual")
        @Size(max = 200, message = "Contraseña demasiado larga")
        String password) {

    @Override
    public String toString() {
        return "CambioCorreoRequest[correo=" + correo + ", password=****]";
    }
}
