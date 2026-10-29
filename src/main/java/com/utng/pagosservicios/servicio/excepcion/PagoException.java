package com.utng.pagosservicios.servicio.excepcion;

/**
 * Base de las excepciones de negocio. El mensaje es apto para mostrarse al
 * cliente: nunca debe incluir datos sensibles (saldos, numeros completos de
 * cuenta, credenciales) ni detalles internos.
 */
public abstract class PagoException extends RuntimeException {

    private final CodigoError codigo;
    private final Long transaccionId;

    protected PagoException(CodigoError codigo, String detalle) {
        this(codigo, detalle, null);
    }

    protected PagoException(CodigoError codigo, String detalle, Long transaccionId) {
        super(detalle);
        this.codigo = codigo;
        this.transaccionId = transaccionId;
    }

    public CodigoError getCodigo() {
        return codigo;
    }

    /** Transaccion relacionada, para que el cliente pueda consultarla. Puede ser null. */
    public Long getTransaccionId() {
        return transaccionId;
    }
}
