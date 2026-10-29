package com.utng.pagosservicios.seguridad;

import java.time.Duration;

public record TokenEmitido(String valor, Duration vigencia) {

    @Override
    public String toString() {
        return "TokenEmitido[****, vigencia=" + vigencia + "]";
    }
}
