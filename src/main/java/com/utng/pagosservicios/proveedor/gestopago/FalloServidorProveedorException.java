package com.utng.pagosservicios.proveedor.gestopago;

/**
 * Respuesta 5xx. Se lanza dentro del circuit breaker para que cuente como fallo
 * de disponibilidad; los rechazos de negocio y los 4xx no cuentan.
 */
final class FalloServidorProveedorException extends RuntimeException {

    private final int status;

    FalloServidorProveedorException(int status) {
        super("El proveedor respondio HTTP " + status, null, false, false);
        this.status = status;
    }

    int status() {
        return status;
    }
}
