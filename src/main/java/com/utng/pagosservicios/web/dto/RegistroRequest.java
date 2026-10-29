package com.utng.pagosservicios.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Alta de un cliente. Solo trae lo que el cliente puede decidir: activo,
 * pagos habilitados, saldo y limite los fija el servidor. Cualquier campo extra
 * ("activo", "saldo"...) se rechaza con 400 (fail-on-unknown-properties).
 */
public record RegistroRequest(
        @NotBlank(message = "El usuario es obligatorio")
        @Pattern(regexp = "^[a-z0-9._-]{4,50}$",
                message = "El usuario debe tener de 4 a 50 caracteres: minúsculas, números, punto, guion o guion bajo")
        String usuario,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                message = "La contraseña debe incluir al menos una letra y un número")
        String password,

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 200, message = "Nombre demasiado largo")
        String nombre) {

    /** El toString generado de un record imprimiria la contrasena. */
    @Override
    public String toString() {
        return "RegistroRequest[usuario=" + usuario + ", password=****, nombre=" + nombre + "]";
    }
}