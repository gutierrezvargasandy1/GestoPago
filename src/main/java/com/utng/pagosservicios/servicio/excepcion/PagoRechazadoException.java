package com.utng.pagosservicios.servicio.excepcion;

/** El proveedor rechazo el pago; el monto ya fue devuelto a la cuenta (422). */
public class PagoRechazadoException extends PagoException {

    public PagoRechazadoException(Long transaccionId, String codigoProveedor) {
        super(CodigoError.PAGO_RECHAZADO,
                "El proveedor rechazó la operación (código " + codigoProveedor
                        + "). No se realizó ningún cargo: el monto fue devuelto a la cuenta.",
                transaccionId);
    }
}
