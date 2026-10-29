package com.utng.pagosservicios.dominio.cuenta;

public enum EstadoCuenta {
    ACTIVA,
    BLOQUEADA,
    CANCELADA;

    public boolean permiteOperar() {
        return this == ACTIVA;
    }
}
