package com.utng.pagosservicios.web.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Alta de un cliente. Solo trae lo que el cliente puede decidir: activo,
 * pagos habilitados, saldo y limite los fija el servidor. Cualquier campo extra
 * ("activo", "saldo"...) se rechaza con 400 (fail-on-unknown-properties).
 *
 * <p>El usuario es el correo electronico. Sin aceptar los terminos y
 * condiciones no se crea la cuenta.
 */
public record RegistroRequest(
        @JsonAlias("usuario")
        @NotBlank(message = "El correo es obligatorio")
        @Size(max = 100, message = "El correo es demasiado largo (máximo 100 caracteres)")
        @Email(message = "Escribe un correo electrónico válido")
        @Pattern(regexp = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$", message = "Escribe un correo electrónico válido")
        String correo,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                message = "La contraseña debe incluir al menos una letra y un número")
        String password,

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 200, message = "Nombre demasiado largo")
        String nombre,

        @NotNull(message = "Debes aceptar los términos y condiciones")
        @AssertTrue(message = "Debes aceptar los términos y condiciones")
        Boolean aceptaTerminos) {

    /** El toString generado de un record imprimiria la contrasena. */
    @Override
    public String toString() {
        return "RegistroRequest[correo=" + correo + ", password=****, nombre=" + nombre
                + ", aceptaTerminos=" + aceptaTerminos + "]";
    }
}
