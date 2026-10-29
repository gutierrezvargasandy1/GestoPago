package com.utng.pagosservicios.servicio.excepcion;

/**
 * El proveedor rechazo el pago por ser identico a otro aplicado hace poco
 * (codigo 06). Este no se aplico y el monto ya fue devuelto (409).
 */
public class PagoDuplicadoException extends PagoException {

    public PagoDuplicadoException(Long transaccionId) {
        super(CodigoError.PAGO_DUPLICADO,
                "El proveedor ya aplicó recientemente un pago igual (mismo servicio, referencia y monto) y no "
                        + "aplicó este. No se realizó un segundo cargo: el monto fue devuelto a la cuenta. "
                        + "Si necesita repetirlo, espere 15 minutos.",
                transaccionId);
    }
}
