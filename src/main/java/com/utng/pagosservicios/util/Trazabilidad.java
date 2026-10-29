package com.utng.pagosservicios.util;

/** Clave con la que se guarda en el MDC el identificador de la peticion en curso. */
public final class Trazabilidad {

    public static final String CLAVE_MDC = "traceId";
    public static final String ENCABEZADO = "X-Request-Id";

    private Trazabilidad() {
    }
}
