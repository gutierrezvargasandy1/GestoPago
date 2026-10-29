package com.utng.pagosservicios.servicio.excepcion;

/** La solicitud choca con el estado actual de otra operacion (409). */
public class ConflictoException extends PagoException {

    public ConflictoException(CodigoError codigo, String detalle) {
        super(codigo, detalle);
    }

    public ConflictoException(CodigoError codigo, String detalle, Long transaccionId) {
        super(codigo, detalle, transaccionId);
    }
}
