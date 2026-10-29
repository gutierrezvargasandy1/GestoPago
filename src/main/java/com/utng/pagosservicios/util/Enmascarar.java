package com.utng.pagosservicios.util;

/**
 * Enmascara datos para los logs. Un numero de cuenta o de telefono completo en
 * un log es informacion sensible que termina en sistemas con mas accesos que la
 * base de datos.
 */
public final class Enmascarar {

    private static final int VISIBLES = 4;

    private Enmascarar() {
    }

    /** "1000000001" -> "******0001". */
    public static String ultimos(String valor) {
        if (valor == null || valor.isEmpty()) {
            return "";
        }
        if (valor.length() <= VISIBLES) {
            return "*".repeat(valor.length());
        }
        int ocultos = valor.length() - VISIBLES;
        return "*".repeat(ocultos) + valor.substring(ocultos);
    }
}
