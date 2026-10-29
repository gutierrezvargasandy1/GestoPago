package com.utng.pagosservicios.servicio;

import com.utng.pagosservicios.servicio.excepcion.CodigoError;
import com.utng.pagosservicios.servicio.excepcion.SolicitudInvalidaException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClaveIdempotenciaTest {

    @Test
    void aceptaUnUuid() {
        String uuid = UUID.randomUUID().toString();
        assertThat(ClaveIdempotencia.de(uuid).valor()).isEqualTo(uuid);
    }

    @ParameterizedTest
    @ValueSource(strings = {"corta", "con espacios 123", "inyeccion\nlinea", "caracter$raro123"})
    void rechazaFormatosInvalidos(String valor) {
        assertThatThrownBy(() -> ClaveIdempotencia.de(valor))
                .isInstanceOfSatisfying(SolicitudInvalidaException.class,
                        e -> assertThat(e.getCodigo()).isEqualTo(CodigoError.CLAVE_IDEMPOTENCIA_INVALIDA));
    }

    @Test
    void rechazaNuloYDemasiadoLarga() {
        assertThatThrownBy(() -> ClaveIdempotencia.de(null)).isInstanceOf(SolicitudInvalidaException.class);
        assertThatThrownBy(() -> ClaveIdempotencia.de("a".repeat(81))).isInstanceOf(SolicitudInvalidaException.class);
    }
}
