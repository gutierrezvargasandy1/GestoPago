package com.utng.pagosservicios.dominio.pago;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class EstadoTransaccionTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "DEBITADA, ENVIADA",
            "DEBITADA, REVERSADA",
            "ENVIADA, CONFIRMADA",
            "ENVIADA, REVERSADA",
            "ENVIADA, EN_CONCILIACION",
            "ENVIADA, REVISION_MANUAL",
            "EN_CONCILIACION, CONFIRMADA",
            "EN_CONCILIACION, REVERSADA",
            "EN_CONCILIACION, REVISION_MANUAL",
            "REVISION_MANUAL, CONFIRMADA",
            "REVISION_MANUAL, REVERSADA"
    })
    void transicionesPermitidas(EstadoTransaccion origen, EstadoTransaccion destino) {
        assertThat(origen.puedeTransicionarA(destino)).isTrue();
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            // Devolver el dinero de un pago ya confirmado seria una perdida.
            "CONFIRMADA, REVERSADA",
            // Un reverso no se puede repetir ni deshacer.
            "REVERSADA, REVERSADA",
            "REVERSADA, CONFIRMADA",
            // No se confirma algo que nunca se envio.
            "DEBITADA, CONFIRMADA",
            "DEBITADA, EN_CONCILIACION",
            "EN_CONCILIACION, ENVIADA",
            "REVISION_MANUAL, EN_CONCILIACION"
    })
    void transicionesProhibidas(EstadoTransaccion origen, EstadoTransaccion destino) {
        assertThat(origen.puedeTransicionarA(destino)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = EstadoTransaccion.class, names = {"CONFIRMADA", "REVERSADA"})
    void losEstadosTerminalesNoTienenSalida(EstadoTransaccion terminal) {
        assertThat(terminal.esTerminal()).isTrue();
        for (EstadoTransaccion destino : EstadoTransaccion.values()) {
            assertThat(terminal.puedeTransicionarA(destino)).isFalse();
        }
    }

    @Test
    void laRevisionManualNoSeConciliaAutomaticamente() {
        assertThat(EstadoTransaccion.conciliables())
                .containsExactlyInAnyOrder(EstadoTransaccion.DEBITADA, EstadoTransaccion.ENVIADA,
                        EstadoTransaccion.EN_CONCILIACION);
        assertThat(EstadoTransaccion.REVISION_MANUAL.conciliableAutomaticamente()).isFalse();
    }
}
