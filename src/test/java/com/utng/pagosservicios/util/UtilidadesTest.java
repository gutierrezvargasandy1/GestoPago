package com.utng.pagosservicios.util;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class UtilidadesTest {

    @Test
    void enmascaraTodoMenosLosUltimosCuatro() {
        assertThat(Enmascarar.ultimos("1000000001")).isEqualTo("******0001");
        assertThat(Enmascarar.ultimos("123")).isEqualTo("***");
        assertThat(Enmascarar.ultimos(null)).isEmpty();
    }

    @Test
    void laDescripcionDeUnErrorNoIncluyeSuMensaje() {
        RuntimeException error = new RuntimeException("Failing row contains (1000000001, secreto)",
                new SQLException("detalle con datos", "23514"));

        String descripcion = DescripcionError.de(error);

        assertThat(descripcion).isEqualTo("RuntimeException <- SQLException[sqlState=23514]");
        assertThat(descripcion).doesNotContain("1000000001").doesNotContain("secreto");
    }
}
