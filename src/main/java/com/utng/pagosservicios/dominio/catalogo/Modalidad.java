package com.utng.pagosservicios.dominio.catalogo;

/** Forma en que el proveedor recibe la operacion. */
public enum Modalidad {

    /** Tiempo aire o PIN: la referencia es el telefono y el precio lo fija el producto. */
    RECARGA,

    /** Pago de un recibo: se envian la referencia del servicio y el monto a pagar. */
    PAGO_SERVICIO
}
