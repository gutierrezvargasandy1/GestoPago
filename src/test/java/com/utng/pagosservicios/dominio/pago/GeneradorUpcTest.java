package com.utng.pagosservicios.dominio.pago;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GeneradorUpcTest {

    @Test
    void tieneQuinceDigitosYNoEmpiezaConCero() {
        for (int i = 0; i < 1_000; i++) {
            assertThat(GeneradorUpc.generar()).matches("^[1-9][0-9]{14}$");
        }
    }

    @Test
    void noSeRepiteEnMuchasGeneraciones() {
        Set<String> generados = new HashSet<>();
        for (int i = 0; i < 50_000; i++) {
            generados.add(GeneradorUpc.generar());
        }
        assertThat(generados).hasSize(50_000);
    }
}
