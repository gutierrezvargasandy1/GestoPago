package com.utng.pagosservicios.servicio.excepcion;

/** El cliente no tiene permiso sobre la operacion o el recurso (403). */
public class AccesoDenegadoException extends PagoException {

    public AccesoDenegadoException(CodigoError codigo, String detalle) {
        super(codigo, detalle);
    }
}
