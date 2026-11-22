package com.utng.pagosservicios.servicio.excepcion;

/** El segundo factor no se pudo comprobar (401): codigo incorrecto, bloqueado o desafio vencido. */
public class DosPasosException extends PagoException {

    public DosPasosException(CodigoError codigo, String detalle) {
        super(codigo, detalle);
    }
}
