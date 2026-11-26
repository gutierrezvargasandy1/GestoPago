package com.utng.pagosservicios.util;

import java.util.Locale;

/** El correo es el usuario: se compara sin espacios y en minusculas. */
public final class Correos {

    private Correos() {
    }

    public static String normalizar(String valor) {
        return valor == null ? null : valor.strip().toLowerCase(Locale.ROOT);
    }
}
