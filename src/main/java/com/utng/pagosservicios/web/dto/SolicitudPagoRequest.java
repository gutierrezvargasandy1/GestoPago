package com.utng.pagosservicios.web.dto;

import com.utng.pagosservicios.dominio.pago.SolicitudPago;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Validacion de FORMATO de la peticion (primera barrera). Las reglas de negocio
 * (si el producto existe, si el monto es el del catalogo, si hay saldo) se
 * validan despues en el servicio, contra la base de datos.
 */
public record SolicitudPagoRequest(

        @NotBlank(message = "El número de cuenta es obligatorio")
        @Pattern(regexp = "^[0-9]{10,20}$", message = "El número de cuenta debe tener entre 10 y 20 dígitos")
        String numeroCuenta,

        // Los codigos del catalogo sincronizado son los numeros del proveedor ("17", "235").
        @NotBlank(message = "El servicio es obligatorio")
        @Pattern(regexp = "^[A-Z0-9_]{1,50}$", message = "El identificador de servicio no tiene un formato válido")
        String idServicio,

        @NotBlank(message = "El producto es obligatorio")
        @Pattern(regexp = "^[A-Z0-9_]{1,50}$", message = "El identificador de producto no tiene un formato válido")
        String idProducto,

        // El formato exacto (p. ej. 10 digitos para un telefono) lo valida despues el producto.
        @NotBlank(message = "La referencia es obligatoria")
        @Pattern(regexp = "^[A-Za-z0-9]{1,40}$", message = "La referencia debe ser alfanumérica, de 1 a 40 caracteres")
        String referencia,

        @NotNull(message = "El monto es obligatorio")
        @DecimalMin(value = "0.01", message = "El monto debe ser mayor a cero")
        @DecimalMax(value = "999999.99", message = "El monto excede el máximo permitido")
        @Digits(integer = 6, fraction = 2, message = "El monto admite como máximo dos decimales")
        BigDecimal monto) {

    public SolicitudPago aSolicitud() {
        return new SolicitudPago(numeroCuenta, idServicio, idProducto, referencia,
                monto.setScale(2, RoundingMode.UNNECESSARY));
    }
}
