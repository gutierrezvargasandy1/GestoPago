package com.utng.pagosservicios.servicio.excepcion;

/** La solicitud es valida pero viola una regla de negocio (422). */
public class ReglaNegocioException extends PagoException {

    public ReglaNegocioException(CodigoError codigo, String detalle) {
        super(codigo, detalle);
    }
}
