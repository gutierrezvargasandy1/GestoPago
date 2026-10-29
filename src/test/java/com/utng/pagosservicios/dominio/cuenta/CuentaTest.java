package com.utng.pagosservicios.dominio.cuenta;

import com.utng.pagosservicios.Fixtures;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CuentaTest {

    @Test
    void debitarDescuentaElMonto() {
        Cuenta cuenta = Fixtures.cuenta(1L, 7L, "500.00");
        cuenta.debitar(new BigDecimal("120.50"));
        assertThat(cuenta.getSaldo()).isEqualByComparingTo("379.50");
    }

    @Test
    void permiteDejarElSaldoEnCeroExacto() {
        Cuenta cuenta = Fixtures.cuenta(1L, 7L, "100.00");
        cuenta.debitar(new BigDecimal("100.00"));
        assertThat(cuenta.getSaldo()).isEqualByComparingTo("0");
    }

    @Test
    void noPermiteSobregiro() {
        Cuenta cuenta = Fixtures.cuenta(1L, 7L, "99.99");
        assertThatThrownBy(() -> cuenta.debitar(new BigDecimal("100.00")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(cuenta.getSaldo()).isEqualByComparingTo("99.99");
    }

    @Test
    void rechazaMontosNoPositivos() {
        Cuenta cuenta = Fixtures.cuenta(1L, 7L, "100.00");
        assertThatThrownBy(() -> cuenta.debitar(BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cuenta.abonar(new BigDecimal("-1"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cuenta.debitar(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void abonarDevuelveElMonto() {
        Cuenta cuenta = Fixtures.cuenta(1L, 7L, "0.00");
        cuenta.abonar(new BigDecimal("108.00"));
        assertThat(cuenta.getSaldo()).isEqualByComparingTo("108.00");
    }

    @Test
    void soloLaCuentaActivaPermiteOperar() {
        Cuenta cuenta = Fixtures.cuenta(1L, 7L, "100.00");
        assertThat(cuenta.permiteOperar()).isTrue();
        cuenta.bloquear();
        assertThat(cuenta.permiteOperar()).isFalse();
    }

    @Test
    void reconoceASuTitular() {
        Cuenta cuenta = Fixtures.cuenta(1L, 7L, "100.00");
        assertThat(cuenta.perteneceA(7L)).isTrue();
        assertThat(cuenta.perteneceA(8L)).isFalse();
    }
}
