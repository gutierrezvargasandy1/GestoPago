package com.utng.pagosservicios.proveedor.gestopago;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CodigosGestopagoTest {

    private final CodigosGestopago codigos = new CodigosGestopago(new GestopagoProperties.Codigos(
            List.of("01"), List.of("02", "05"), List.of("06"), List.of("82"), List.of("01"), List.of("04")));

    @Test
    void clasificaLosCodigosDeEnvio() {
        assertThat(codigos.clasificarEnvio("01")).isEqualTo(CodigosGestopago.Envio.APROBADA);
        assertThat(codigos.clasificarEnvio(" 05 ")).isEqualTo(CodigosGestopago.Envio.RECHAZADA);
        assertThat(codigos.clasificarEnvio("06")).isEqualTo(CodigosGestopago.Envio.DUPLICADA);
        assertThat(codigos.clasificarEnvio("82")).isEqualTo(CodigosGestopago.Envio.SIN_RESPUESTA);
    }

    @Test
    void clasificaLosCodigosDeConsultaPorSeparado() {
        assertThat(codigos.clasificarConsulta("01")).isEqualTo(CodigosGestopago.Consulta.APLICADA);
        assertThat(codigos.clasificarConsulta("04")).isEqualTo(CodigosGestopago.Consulta.NO_APLICADA);
        // 02 rechaza un envio, pero no esta configurado como "no aplicada" en la consulta.
        assertThat(codigos.clasificarConsulta("02")).isEqualTo(CodigosGestopago.Consulta.DESCONOCIDA);
    }

    @Test
    void loQueNoEstaConfiguradoEsDesconocido() {
        assertThat(codigos.clasificarEnvio("1")).isEqualTo(CodigosGestopago.Envio.DESCONOCIDA);
        assertThat(codigos.clasificarEnvio("99")).isEqualTo(CodigosGestopago.Envio.DESCONOCIDA);
        assertThat(codigos.clasificarEnvio(null)).isEqualTo(CodigosGestopago.Envio.DESCONOCIDA);
        assertThat(codigos.clasificarEnvio("  ")).isEqualTo(CodigosGestopago.Envio.DESCONOCIDA);
        assertThat(codigos.clasificarConsulta(null)).isEqualTo(CodigosGestopago.Consulta.DESCONOCIDA);
    }

    @Test
    void unCodigoNoPuedeEstarEnDosListasDeLaMismaOperacion() {
        assertThatThrownBy(() -> new CodigosGestopago(new GestopagoProperties.Codigos(
                List.of("01"), List.of("01"), List.of(), List.of(), List.of(), List.of())))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new CodigosGestopago(new GestopagoProperties.Codigos(
                List.of("01"), List.of(), List.of(), List.of(), List.of("04"), List.of("04"))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void elMismoCodigoPuedeSignificarAlgoEnCadaOperacion() {
        // "01" es aprobada en sendTx y aplicada en confirmTx: no es un conflicto.
        new CodigosGestopago(new GestopagoProperties.Codigos(
                List.of("01"), List.of(), List.of(), List.of(), List.of("01"), List.of()));
    }
}
