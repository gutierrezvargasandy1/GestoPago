package com.utng.pagosservicios.web.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Paso 1 del inicio de sesion. Las apps mandan "correo"; "usuario" se sigue
 * aceptando para las cuentas creadas antes de que el usuario fuera el correo.
 */
public record CredencialesRequest(
        @JsonAlias("correo")
        @NotBlank(message = "El correo es obligatorio") @Size(max = 100, message = "Correo demasiado largo")
        String usuario,
        @NotBlank(message = "La contraseña es obligatoria") @Size(max = 200, message = "Contraseña demasiado larga")
        String password) {

    /** El toString generado de un record imprimiria la contrasena si alguien registrara este objeto. */
    @Override
    public String toString() {
        return "CredencialesRequest[usuario=" + usuario + ", password=****]";
    }
}
