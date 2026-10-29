package com.utng.pagosservicios.integracion;

import com.utng.pagosservicios.dominio.pago.SolicitudPago;
import com.utng.pagosservicios.proveedor.gestopago.XmlPuntoRed;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IdempotenciaIntegracionTest extends IntegracionBase {

    private String token;
    private long clienteId;

    @BeforeEach
    void cliente() {
        clienteId = datos.crearCliente("ana");
        datos.crearCuenta(clienteId, CUENTA, "1000.00");
        token = tokenDe("ana");
    }

    @Test
    void laMismaClaveDosVeces_cobraUnaSolaVez() throws Exception {
        proveedorAprueba();
        long id = idDe(pagar(token, "idem-doble-0001", telcel100()).andExpect(status().isCreated()).andReturn());

        pagar(token, "idem-doble-0001", telcel100())
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.transaccionId").value(id))
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"));

        assertThat(datos.saldo(CUENTA)).isEqualByComparingTo("900.00");
        assertThat(datos.contarMovimientos(CUENTA, "CARGO")).isEqualTo(1);
        GESTOPAGO.verify(1, postRequestedFor(urlEqualTo(RUTA_ENVIO)));
    }

    @Test
    void laMismaClaveConOtroMonto_esRechazada() throws Exception {
        proveedorAprueba();
        pagar(token, "idem-otros-0001", telcel100()).andExpect(status().isCreated());

        pagar(token, "idem-otros-0001", solicitud(CUENTA, "TAE_TELCEL", "TEL_200", "5512345678", "200.00"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CLAVE_IDEMPOTENCIA_REUTILIZADA"));

        assertThat(datos.contarMovimientos(CUENTA, "CARGO")).isEqualTo(1);
    }

    @Test
    void sinClaveDeIdempotencia_400() throws Exception {
        pagar(token, null, telcel100())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
        assertThat(datos.contarTransacciones()).isZero();
    }

    @Test
    void claveConFormatoInvalido_400() throws Exception {
        pagar(token, "corta", telcel100())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CLAVE_IDEMPOTENCIA_INVALIDA"));
    }

    @Test
    void reintentarUnPagoEnConciliacion_noLoReenviaAlProveedor() throws Exception {
        proveedorResponde(xml(XmlPuntoRed.exito("1", "A")).withFixedDelay(1500));
        pagar(token, "idem-timeout-001", telcel100()).andExpect(status().isAccepted());

        pagar(token, "idem-timeout-001", telcel100())
                .andExpect(status().isAccepted())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.estado").value("EN_CONCILIACION"));

        GESTOPAGO.verify(1, postRequestedFor(urlEqualTo(RUTA_ENVIO)));
        assertThat(datos.contarMovimientos(CUENTA, "CARGO")).isEqualTo(1);
    }

    @Test
    void reintentarMientrasLaOriginalSigueEnCurso_409() throws Exception {
        String huella = new SolicitudPago(CUENTA, "TAE_TELCEL", "TEL_100", "5512345678",
                new BigDecimal("100.00")).huella();
        long id = datos.insertarTransaccion(clienteId, CUENTA, "idem-encurso-001", huella, "ENVIADA", true);

        pagar(token, "idem-encurso-001", telcel100())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PAGO_EN_PROCESO"))
                .andExpect(jsonPath("$.transaccionId").value(id));

        GESTOPAGO.verify(0, postRequestedFor(urlEqualTo(RUTA_ENVIO)));
    }

    @Test
    void unaValidacionFallidaNoConsumeLaClave() throws Exception {
        datos.fijarSaldo(CUENTA, "50.00");
        pagar(token, "idem-saldo-0001", telcel100())
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.codigo").value("SALDO_INSUFICIENTE"));

        datos.fijarSaldo(CUENTA, "500.00");
        proveedorAprueba();
        pagar(token, "idem-saldo-0001", telcel100()).andExpect(status().isCreated());

        assertThat(datos.saldo(CUENTA)).isEqualByComparingTo("400.00");
    }
}
