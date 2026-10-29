package com.utng.pagosservicios.dominio.pago;

import com.utng.pagosservicios.Fixtures;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static com.utng.pagosservicios.Fixtures.AHORA;
import static com.utng.pagosservicios.Fixtures.MARGEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransaccionPagoTest {

    @Test
    void alReservarQuedaDebitadaConTotalYReferenciaExterna() {
        TransaccionPago transaccion = TransaccionPago.reservar(Fixtures.solicitudTelcel(), "clave-0001",
                Fixtures.cuenta(1L, 7L, "500.00"), Fixtures.cfe(), "traza", AHORA, MARGEN);

        assertThat(transaccion.getEstado()).isEqualTo(EstadoTransaccion.DEBITADA);
        assertThat(transaccion.getMontoTotal()).isEqualByComparingTo("108.00"); // 100 + comision 8
        // UPC: lo que identifica la operacion ante PuntoRed (3 a 15 caracteres).
        assertThat(transaccion.getReferenciaExterna()).matches("^[1-9][0-9]{14}$");
        assertThat(transaccion.getProximoIntentoEn()).isEqualTo(AHORA.plus(MARGEN));
        assertThat(transaccion.getClienteId()).isEqualTo(7L);
    }

    @Test
    void cadaTransaccionTieneUnaReferenciaExternaDistinta() {
        TransaccionPago a = Fixtures.transaccion(1L, EstadoTransaccion.DEBITADA);
        TransaccionPago b = Fixtures.transaccion(2L, EstadoTransaccion.DEBITADA);
        assertThat(a.getReferenciaExterna()).isNotEqualTo(b.getReferenciaExterna());
    }

    @Test
    void noSePuedeReversarUnPagoConfirmado() {
        TransaccionPago confirmada = Fixtures.transaccion(1L, EstadoTransaccion.CONFIRMADA);
        assertThatThrownBy(() -> confirmada.reversar(MotivoResolucion.RECHAZADA_POR_PROVEEDOR, "03", AHORA))
                .isInstanceOf(IllegalStateException.class);
        assertThat(confirmada.getEstado()).isEqualTo(EstadoTransaccion.CONFIRMADA);
    }

    @Test
    void noSePuedeReversarDosVeces() {
        TransaccionPago reversada = Fixtures.transaccion(1L, EstadoTransaccion.REVERSADA);
        assertThatThrownBy(() -> reversada.reversar(MotivoResolucion.NUNCA_ENVIADA, null, AHORA))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void confirmarCierraLaTransaccionYGuardaElFolio() {
        TransaccionPago transaccion = Fixtures.transaccion(1L, EstadoTransaccion.ENVIADA);
        transaccion.confirmar("01", "F-9", "A-9", AHORA);

        assertThat(transaccion.getEstado()).isEqualTo(EstadoTransaccion.CONFIRMADA);
        assertThat(transaccion.getFolioProveedor()).isEqualTo("F-9");
        assertThat(transaccion.getResueltoEn()).isEqualTo(AHORA);
        assertThat(transaccion.getProximoIntentoEn()).isNull();
    }

    @Test
    void confirmarGuardaLosDatosDeCanje() {
        TransaccionPago transaccion = Fixtures.transaccion(1L, EstadoTransaccion.ENVIADA);
        transaccion.confirmar("01", "F-9", "A-9", "1234-5678", "Canjea en la app", AHORA);

        assertThat(transaccion.getPinCanje()).isEqualTo("1234-5678");
        assertThat(transaccion.getInstruccionesCanje()).isEqualTo("Canjea en la app");
    }

    /** La hora de envio se vuelve a mandar en confirmTx: debe sobrevivir intacta al guardarse. */
    @Test
    void laHoraDeEnvioSeGuardaConLaPrecisionDeLaBase() {
        TransaccionPago transaccion = Fixtures.transaccion(1L, EstadoTransaccion.DEBITADA);
        transaccion.marcarEnviada(AHORA.plusNanos(123_456_789), MARGEN);

        assertThat(transaccion.getEnviadoEn()).isEqualTo(AHORA.plusNanos(123_456_000));
    }

    @Test
    void soloEstaListaParaConciliarCuandoVenceSuProximoIntento() {
        TransaccionPago transaccion = Fixtures.transaccion(1L, EstadoTransaccion.EN_CONCILIACION);
        assertThat(transaccion.listaParaConciliar(AHORA)).isFalse();
        assertThat(transaccion.listaParaConciliar(AHORA.plus(MARGEN))).isTrue();
    }

    @Test
    void laRevisionManualNoVuelveALaConciliacionAutomatica() {
        TransaccionPago transaccion = Fixtures.transaccion(1L, EstadoTransaccion.REVISION_MANUAL);
        assertThat(transaccion.listaParaConciliar(AHORA.plus(Duration.ofDays(30)))).isFalse();
    }

    @Test
    void reclamarAumentaIntentosYApartaLaTransaccion() {
        TransaccionPago transaccion = Fixtures.transaccion(1L, EstadoTransaccion.EN_CONCILIACION);
        transaccion.registrarIntentoConsulta(AHORA, Duration.ofMinutes(5));

        assertThat(transaccion.getIntentosConsulta()).isEqualTo(1);
        assertThat(transaccion.listaParaConciliar(AHORA.plus(Duration.ofMinutes(4)))).isFalse();
    }

    @Test
    void unaEnviadaSinRespuestaPasaAConciliacionAlReprogramar() {
        TransaccionPago transaccion = Fixtures.transaccion(1L, EstadoTransaccion.ENVIADA);
        transaccion.registrarIntentoConsulta(AHORA, Duration.ofMinutes(5));
        transaccion.programarSiguienteConsulta("HTTP_500", AHORA, Duration.ofMinutes(1), Duration.ofHours(1));

        assertThat(transaccion.getEstado()).isEqualTo(EstadoTransaccion.EN_CONCILIACION);
        assertThat(transaccion.getCodigoResultado()).isEqualTo("HTTP_500");
        assertThat(transaccion.getProximoIntentoEn()).isEqualTo(AHORA.plus(Duration.ofMinutes(1)));
    }

    @Test
    void laEsperaCreceExponencialmenteHastaElMaximo() {
        Duration base = Duration.ofMinutes(1);
        Duration maxima = Duration.ofMinutes(30);
        assertThat(TransaccionPago.esperaExponencial(1, base, maxima)).isEqualTo(Duration.ofMinutes(1));
        assertThat(TransaccionPago.esperaExponencial(2, base, maxima)).isEqualTo(Duration.ofMinutes(2));
        assertThat(TransaccionPago.esperaExponencial(4, base, maxima)).isEqualTo(Duration.ofMinutes(8));
        assertThat(TransaccionPago.esperaExponencial(6, base, maxima)).isEqualTo(maxima);
        assertThat(TransaccionPago.esperaExponencial(1000, base, maxima)).isEqualTo(maxima);
        assertThat(TransaccionPago.esperaExponencial(3, Duration.ZERO, maxima)).isEqualTo(Duration.ZERO);
    }
}
