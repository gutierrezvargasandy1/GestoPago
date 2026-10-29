package com.utng.pagosservicios.servicio.excepcion;

/** El recurso no existe o no pertenece al cliente (404). */
public class RecursoNoEncontradoException extends PagoException {

    public RecursoNoEncontradoException(CodigoError codigo, String detalle) {
        super(codigo, detalle);
    }
}
