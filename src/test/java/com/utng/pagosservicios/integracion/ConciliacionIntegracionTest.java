package com.utng.pagosservicios.integracion;

import com.utng.pagosservicios.proveedor.gestopago.MensajeCifrado;
import com.utng.pagosservicios.proveedor.gestopago.XmlPuntoRed;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Resolucion de operaciones cuyo resultado quedo indeterminado. En ningun caso
 * se reenvia el cargo al proveedor: solo se le pregunta con confirmTx.
 */
class ConciliacionIntegracionTest extends IntegracionBase {

    private String token;
    private long clienteId;

    @BeforeEach
    void cliente() {
        clienteId = datos.crearCliente("ana");
        datos.crearCuenta(clienteId, CUENTA, "1000.00");
        token = tokenDe("ana");
    }

    private long pagoConTimeout(String clave) throws Exception {
        proveedorResponde(xml(XmlPuntoRed.exito("1", "A")).withFixedDelay(1500));
        return idDe(pagar(token, clave, telcel100()).andExpect(status().isAccepted()).andReturn());
    }

    private void ejecutarConciliacion(long id) {
        datos.adelantarConciliacion(id);
        conciliacion.ejecutarLote();
    }

    @Test
    void siElProveedorSiLaAplico_seConfirmaSinDevolverNiReenviar() throws Exception {
        long id = pagoConTimeout("conc-aprobada-1");
        consultaResponde(xml(XmlPuntoRed.exito("F-CONC", "A-CONC")));

        ejecutarConciliacion(id);

        assertThat(datos.estado(id)).isEqualTo("CONFIRMADA");
        assertThat(datos.saldo(CUENTA)).isEqualByComparingTo("900.00");
        assertThat(datos.contarMovimientos(CUENTA, "ABONO")).isZero();
        GESTOPAGO.verify(1, postRequestedFor(urlEqualTo(RUTA_ENVIO)));
        GESTOPAGO.verify(postRequestedFor(urlEqualTo(RUTA_CONSULTA))
                .withHeader("Authorization", equalTo("Bearer token-de-pruebas")));
    }

    /** confirmTx recibe la misma operacion: mismo UPC y misma hora local del envio original. */
    @Test
    void laConsultaLlevaElMismoUpcYHoraDelEnvio() throws Exception {
        long id = pagoConTimeout("conc-mismo-upc1");
        consultaResponde(xml(XmlPuntoRed.exito("F-CONC", "A-CONC")));

        ejecutarConciliacion(id);

        MensajeCifrado envio = mensajesRecibidos(RUTA_ENVIO).get(0);
        MensajeCifrado consulta = mensajesRecibidos(RUTA_CONSULTA).get(0);
        assertThat((String) consulta.dato("$.upc")).isEqualTo(datos.referenciaExterna(id));
        assertThat((String) consulta.dato("$.horaLocal")).isEqualTo(envio.dato("$.horaLocal"));
        assertThat(consulta.campo("random")).isNotEqualTo(envio.campo("random"));
    }

    @Test
    void siElProveedorNoLaAplico_seDevuelveSoloDespuesDeConfirmarloDosVeces() throws Exception {
        long id = pagoConTimeout("conc-noaplica-1");
        consultaResponde(xml(XmlPuntoRed.codigo("04", "Transaccion no aplicada")));

        ejecutarConciliacion(id);
        assertThat(datos.estado(id)).isEqualTo("EN_CONCILIACION");
        assertThat(datos.saldo(CUENTA)).isEqualByComparingTo("900.00");

        ejecutarConciliacion(id);
        assertThat(datos.estado(id)).isEqualTo("REVERSADA");
        assertThat(datos.motivo(id)).isEqualTo("NO_ENCONTRADA_EN_PROVEEDOR");
        assertThat(datos.saldo(CUENTA)).isEqualByComparingTo("1000.00");
        assertThat(datos.contarMovimientos(CUENTA, "ABONO")).isEqualTo(1);
    }

    @Test
    void siLaConsultaSigueFallando_terminaEnRevisionManualConElDineroRetenido() throws Exception {
        long id = pagoConTimeout("conc-revision-1");
        consultaResponde(aResponse().withStatus(500));

        for (int intento = 0; intento < 3; intento++) {
            ejecutarConciliacion(id);
        }

        assertThat(datos.estado(id)).isEqualTo("REVISION_MANUAL");
        assertThat(datos.motivo(id)).isEqualTo("INTENTOS_AGOTADOS");
        assertThat(datos.saldo(CUENTA)).isEqualByComparingTo("900.00");

        // Una vez en revision manual, el job ya no la toca.
        ejecutarConciliacion(id);
        assertThat(datos.estado(id)).isEqualTo("REVISION_MANUAL");
    }

    /** confirmTx solo responde 24 h despues del envio: pasado el plazo decide una persona. */
    @Test
    void pasadoElPlazoDeConsulta_vaARevisionManualSinPreguntar() throws Exception {
        long id = pagoConTimeout("conc-vencida-01");
        consultaResponde(xml(XmlPuntoRed.exito("F", "A")));
        datos.enviadaHace(id, 25);

        ejecutarConciliacion(id);

        assertThat(datos.estado(id)).isEqualTo("REVISION_MANUAL");
        assertThat(datos.motivo(id)).isEqualTo("CONSULTA_VENCIDA");
        assertThat(datos.saldo(CUENTA)).isEqualByComparingTo("900.00");
        GESTOPAGO.verify(0, postRequestedFor(urlEqualTo(RUTA_CONSULTA)));
    }

    @Test
    void unaTransaccionQueQuedoDebitadaSeDevuelveSinConsultarAlProveedor() {
        long id = datos.insertarTransaccion(clienteId, CUENTA, "conc-huerfana-1", "0".repeat(64), "DEBITADA", true);
        assertThat(datos.saldo(CUENTA)).isEqualByComparingTo("900.00");

        ejecutarConciliacion(id);

        assertThat(datos.estado(id)).isEqualTo("REVERSADA");
        assertThat(datos.motivo(id)).isEqualTo("NUNCA_ENVIADA");
        assertThat(datos.saldo(CUENTA)).isEqualByComparingTo("1000.00");
        GESTOPAGO.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void unaTransaccionAunNoVencidaNoSeConsulta() throws Exception {
        long id = pagoConTimeout("conc-novencida1");
        consultaResponde(xml(XmlPuntoRed.exito("F", "A")));

        conciliacion.ejecutarLote(); // sin adelantar el reloj: el margen inicial no ha pasado

        assertThat(datos.estado(id)).isEqualTo("EN_CONCILIACION");
        GESTOPAGO.verify(0, postRequestedFor(urlEqualTo(RUTA_CONSULTA)));
    }
}
