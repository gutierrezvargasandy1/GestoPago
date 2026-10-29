package com.utng.pagosservicios.dominio.cuenta;

public enum TipoMovimiento {
    /** Salida de dinero: el debito del pago. */
    CARGO,
    /** Entrada de dinero: la devolucion cuando el pago no se concreta. */
    ABONO
}
