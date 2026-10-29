package com.utng.pagosservicios.util;

import java.sql.SQLException;
import java.util.StringJoiner;

/**
 * Describe una excepcion para los logs sin incluir su mensaje. Los mensajes de
 * la base de datos pueden contener datos de la fila (PostgreSQL reporta
 * "Failing row contains (...)" al violar un CHECK); se registra la cadena de
 * clases y el SQLState, que basta para diagnosticar.
 */
public final class DescripcionError {

    private static final int PROFUNDIDAD_MAXIMA = 8;

    private DescripcionError() {
    }

    public static String de(Throwable error) {
        StringJoiner cadena = new StringJoiner(" <- ");
        Throwable actual = error;
        for (int i = 0; actual != null && i < PROFUNDIDAD_MAXIMA; i++) {
            String nombre = actual.getClass().getSimpleName();
            if (actual instanceof SQLException sql && sql.getSQLState() != null) {
                nombre += "[sqlState=" + sql.getSQLState() + "]";
            }
            cadena.add(nombre);
            actual = actual.getCause() == actual ? null : actual.getCause();
        }
        return cadena.toString();
    }
}
