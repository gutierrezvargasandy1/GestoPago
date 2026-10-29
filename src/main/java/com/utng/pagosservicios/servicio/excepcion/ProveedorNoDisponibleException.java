package com.utng.pagosservicios.servicio.excepcion;

/**
 * El proveedor no proceso la operacion (circuito abierto, credencial rechazada,
 * conexion imposible). Si habia cargo, ya fue devuelto (503).
 */
public class ProveedorNoDisponibleException extends PagoException {

    public ProveedorNoDisponibleException(Long transaccionId) {
        super(CodigoError.PROVEEDOR_NO_DISPONIBLE,
                transaccionId == null
                        ? "El proveedor no está disponible en este momento. No se realizó ningún cargo."
                        : "El proveedor no procesó la operación. No se realizó ningún cargo: el monto fue devuelto a la cuenta.",
                transaccionId);
    }
}
