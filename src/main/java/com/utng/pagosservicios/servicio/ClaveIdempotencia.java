package com.utng.pagosservicios.servicio;

import com.utng.pagosservicios.servicio.excepcion.CodigoError;
import com.utng.pagosservicios.servicio.excepcion.SolicitudInvalidaException;

import java.util.regex.Pattern;

/**
 * Clave que el cliente genera (idealmente un UUID) y repite si reintenta la
 * misma operacion. Una clave identifica un intento de pago: reenviarla no
 * vuelve a cobrar, devuelve la operacion original.
 */
public record ClaveIdempotencia(String valor) {

    private static final Pattern FORMATO = Pattern.compile("^[A-Za-z0-9_-]{8,80}$");

    public ClaveIdempotencia {
        if (valor == null || !FORMATO.matcher(valor).matches()) {
            throw new SolicitudInvalidaException(CodigoError.CLAVE_IDEMPOTENCIA_INVALIDA,
                    "El encabezado Idempotency-Key debe tener entre 8 y 80 caracteres "
                            + "(letras, números, guion o guion bajo). Se recomienda un UUID.");
        }
    }

    public static ClaveIdempotencia de(String valor) {
        return new ClaveIdempotencia(valor);
    }
}
