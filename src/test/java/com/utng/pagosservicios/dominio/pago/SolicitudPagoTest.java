package com.utng.pagosservicios.dominio.pago;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class SolicitudPagoTest {

    private static SolicitudPago solicitud(String referencia, String monto) {
        return new SolicitudPago("1000000001", "TAE_TELCEL", "TEL_100", referencia, new BigDecimal(monto));
    }

    @Test
    void laMismaSolicitudProduceLaMismaHuella() {
        assertThat(solicitud("5512345678", "100.00").huella())
                .isEqualTo(solicitud("5512345678", "100.00").huella())
                .hasSize(64);
    }

    @Test
    void laEscalaDelMontoNoCambiaLaHuella() {
        assertThat(solicitud("5512345678", "100").huella()).isEqualTo(solicitud("5512345678", "100.00").huella());
    }

    @Test
    void cualquierCambioDeDatosCambiaLaHuella() {
        String original = solicitud("5512345678", "100.00").huella();
        assertThat(solicitud("5512345679", "100.00").huella()).isNotEqualTo(original);
        assertThat(solicitud("5512345678", "100.50").huella()).isNotEqualTo(original);
    }
}
