package com.utng.pagosservicios.servicio.excepcion;

/** Datos de entrada con formato invalido (400). */
public class SolicitudInvalidaException extends PagoException {

    public SolicitudInvalidaException(CodigoError codigo, String detalle) {
        super(codigo, detalle);
    }
}
