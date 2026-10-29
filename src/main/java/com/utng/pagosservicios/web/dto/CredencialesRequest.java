package com.utng.pagosservicios.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CredencialesRequest(
        @NotBlank(message = "El usuario es obligatorio") @Size(max = 100, message = "Usuario demasiado largo")
        String usuario,
        @NotBlank(message = "La contraseña es obligatoria") @Size(max = 200, message = "Contraseña demasiado larga")
        String password) {

    /** El toString generado de un record imprimiria la contrasena si alguien registrara este objeto. */
    @Override
    public String toString() {
        return "CredencialesRequest[usuario=" + usuario + ", password=****]";
    }
}
