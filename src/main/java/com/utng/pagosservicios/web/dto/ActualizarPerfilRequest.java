package com.utng.pagosservicios.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Datos del perfil que el cliente puede editar sin volver a iniciar sesion. */
public record ActualizarPerfilRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 200, message = "Nombre demasiado largo")
        String nombre) {
}
