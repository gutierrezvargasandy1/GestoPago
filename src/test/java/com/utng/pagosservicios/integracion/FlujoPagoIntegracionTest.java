package com.utng.pagosservicios.integracion;

import com.jayway.jsonpath.JsonPath;
import com.utng.pagosservicios.proveedor.gestopago.MensajeCifrado;
import com.utng.pagosservicios.proveedor.gestopago.XmlPuntoRed;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Flujo completo: autenticacion -> validacion -> debito -> PuntoRed -> resultado. */
class FlujoPagoIntegracionTest extends IntegracionBase {

    private String token;

    @BeforeEach
    void cliente() {
        token = clienteConCuenta("ana", CUENTA, "1000.00");
    }

    @Test
    void pagoAprobado_descuentaElSaldoYQuedaConfirmado() throws Exception {
        proveedorAprueba();

        MvcResult resultado = pagar(token, "pago-aprobado-01", telcel100())
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/v1/pagos/")))
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"))
                .andExpect(jsonPath("$.montoTotal").value("100.00"))
                .andExpect(jsonPath("$.folioProveedor").value("F-100"))
                .andExpect(jsonPath("$.autorizacion").value("A-200"))
                .andReturn();

        long id = idDe(resultado);
        assertThat(datos.saldo(CUENTA)).isEqualByComparingTo("900.00");
        assertThat(datos.contarMovimientos(CUENTA, "CARGO")).isEqualTo(1);
        assertThat(datos.contarMovimientos(CUENTA, "ABONO")).isZero();
        GESTOPAGO.verify(1, postRequestedFor(urlEqualTo(RUTA_ENVIO))
                .withHeader("Authorization", equalTo("Bearer token-de-pruebas"))
                .withHeader("X-API-Key", equalTo("clave-api-de-pruebas"))
                .withHeader("Content-Type", containing("application/x-www-form-urlencoded")));
    }

    /** Lo que recibe el proveedor, una vez descifrado, es exactamente la operacion pedida. */
    @Test
    void alProveedorLeLlegaLaOperacionCifradaConSusIdentificadoresYElUpc() throws Exception {
        proveedorAprueba();

        long id = idDe(pagar(token, "pago-contenido-1", telcel100()).andExpect(status().isCreated()).andReturn());

        MensajeCifrado mensaje = mensajesRecibidos(RUTA_ENVIO).get(0);
        assertThat((String) mensaje.dato("$.upc")).isEqualTo(datos.referenciaExterna(id)).hasSize(15);
        assertThat((Integer) mensaje.dato("$.idServicio")).isEqualTo(1001);
        assertThat((Integer) mensaje.dato("$.idProducto")).isEqualTo(2003);
        assertThat((String) mensaje.dato("$.telefono")).isEqualTo("5512345678");
        assertThat((String) mensaje.dato("$.horaLocal")).matches("^20[0-9]{12}$");
        assertThat(mensaje.tiene("montoPago")).isFalse();
    }

    @Test
    void pagoConComision_descuentaPagoMasComisionYAlProveedorSoloLeEnviaElPago() throws Exception {
        proveedorAprueba();

        pagar(token, "pago-cfe-0001", solicitud(CUENTA, "CFE", "CFE_PAGO", "123456789012", "250.50"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.monto").value("250.50"))
                .andExpect(jsonPath("$.comision").value("8.00"))
                .andExpect(jsonPath("$.montoTotal").value("258.50"));

        assertThat(datos.saldo(CUENTA)).isEqualByComparingTo("741.50");
        MensajeCifrado mensaje = mensajesRecibidos(RUTA_ENVIO).get(0);
        assertThat(new BigDecimal(mensaje.dato("$.montoPago").toString())).isEqualByComparingTo("250.50");
        assertThat((String) mensaje.dato("$.referencia")).isEqualTo("123456789012");
        assertThat(mensaje.tiene("telefono")).isFalse();
    }

    @Test
    void saldoInsuficienteDelDistribuidor_devuelveElDineroYLoRegistraEnElLedger() throws Exception {
        proveedorRespondeCodigo("02", "Saldo insuficiente");

        MvcResult resultado = pagar(token, "pago-rechazo-01", telcel100())
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.codigo").value("PAGO_RECHAZADO"))
                .andExpect(jsonPath("$.transaccionId").isNumber())
                .andReturn();

        long id = ((Number) JsonPath.read(resultado.getResponse().getContentAsString(StandardCharsets.UTF_8),
                "$.transaccionId")).longValue();
        assertThat(datos.saldo(CUENTA)).isEqualByComparingTo("1000.00");
        assertThat(datos.contarMovimientos(CUENTA, "CARGO")).isEqualTo(1);
        assertThat(datos.contarMovimientos(CUENTA, "ABONO")).isEqualTo(1);
        assertThat(datos.estado(id)).isEqualTo("REVERSADA");
        assertThat(datos.motivo(id)).isEqualTo("RECHAZADA_POR_PROVEEDOR");
    }

    @Test
    void duplicadaEnElProveedor_devuelveElDineroYResponde409() throws Exception {
        proveedorRespondeCodigo("06", "Transaccion duplicada, intente en 15 minutos");

        MvcResult resultado = pagar(token, "pago-duplicado-1", telcel100())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PAGO_DUPLICADO"))
                .andReturn();

        long id = idDe(resultado);
        assertThat(datos.saldo(CUENTA)).isEqualByComparingTo("1000.00");
        assertThat(datos.estado(id)).isEqualTo("REVERSADA");
        assertThat(datos.motivo(id)).isEqualTo("DUPLICADA_EN_PROVEEDOR");
    }

    /** El requisito central de la actividad. */
    @Test
    void timeout_noDevuelveElDineroYQuedaEnConciliacion() throws Exception {
        proveedorResponde(xml(XmlPuntoRed.exito("1", "A")).withFixedDelay(1500));

        MvcResult resultado = pagar(token, "pago-timeout-01", telcel100())
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.estado").value("EN_CONCILIACION"))
                .andReturn();

        assertThat(datos.estado(idDe(resultado))).isEqualTo("EN_CONCILIACION");
        assertThat(datos.saldo(CUENTA)).isEqualByComparingTo("900.00");
        assertThat(datos.contarMovimientos(CUENTA, "ABONO")).isZero();
    }

    /** 82: el carrier no respondio a PuntoRed. Tampoco prueba que no se aplico. */
    @Test
    void sinRespuestaDelCarrier82_quedaEnConciliacion() throws Exception {
        proveedorRespondeCodigo("82", "Tiempo de espera agotado");
        pagar(token, "pago-codigo82-1", telcel100())
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.estado").value("EN_CONCILIACION"));
        assertThat(datos.saldo(CUENTA)).isEqualByComparingTo("900.00");
    }

    @Test
    void error500DelProveedor_quedaEnConciliacion() throws Exception {
        proveedorResponde(aResponse().withStatus(500));
        pagar(token, "pago-error500-1", telcel100())
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.estado").value("EN_CONCILIACION"));
        assertThat(datos.saldo(CUENTA)).isEqualByComparingTo("900.00");
    }

    @Test
    void codigoDesconocido_quedaEnConciliacionEnLugarDeReversar() throws Exception {
        proveedorRespondeCodigo("99", "En proceso");
        pagar(token, "pago-codigo99-1", telcel100()).andExpect(status().isAccepted());
        assertThat(datos.saldo(CUENTA)).isEqualByComparingTo("900.00");
    }

    @Test
    void tokenRechazadoPorElProveedor_devuelveElDineroYResponde503() throws Exception {
        proveedorResponde(aResponse().withStatus(403).withBody("{\"token\":\"EXPIRED\"}"));

        pagar(token, "pago-cred-0001", telcel100())
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.codigo").value("PROVEEDOR_NO_DISPONIBLE"))
                .andExpect(jsonPath("$.transaccionId").isNumber());

        assertThat(datos.saldo(CUENTA)).isEqualByComparingTo("1000.00");
    }

    @Test
    void circuitoAbierto_rechazaSinTocarElSaldoNiLlamarAlProveedor() throws Exception {
        circuitos.circuitBreaker("gestopago").transitionToOpenState();

        pagar(token, "pago-circuito-1", telcel100())
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("PROVEEDOR_NO_DISPONIBLE"));

        assertThat(datos.contarTransacciones()).isZero();
        assertThat(datos.saldo(CUENTA)).isEqualByComparingTo("1000.00");
        GESTOPAGO.verify(0, postRequestedFor(urlEqualTo(RUTA_ENVIO)));
    }

    /** PuntoRed exige entregar al cliente el PIN de una tarjeta de regalo. */
    @Test
    void tarjetaDeRegalo_elPinLlegaAlDuenoDelPago() throws Exception {
        proveedorResponde(xml(XmlPuntoRed.exitoConPin("7788", "1234-5678-9012", "Canjea en la tienda en linea")));

        long id = idDe(pagar(token, "pago-pin-00001", telcel100())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pin").value("1234-5678-9012"))
                .andExpect(jsonPath("$.instrucciones").value("Canjea en la tienda en linea"))
                .andReturn());

        consultarPago(token, id).andExpect(jsonPath("$.pin").value("1234-5678-9012"));
    }

    @Test
    void cadaIntercambioQuedaEnLaBitacoraSinSecretos() throws Exception {
        proveedorResponde(xml(XmlPuntoRed.exitoConPin("7788", "1234-5678-9012", "Canje")));

        long id = idDe(pagar(token, "pago-bitacora-1", telcel100()).andExpect(status().isCreated()).andReturn());

        List<Map<String, Object>> bitacora = datos.bitacora();
        assertThat(bitacora).hasSize(1);
        Map<String, Object> registro = bitacora.get(0);
        assertThat(registro).containsEntry("operacion", "ENVIO")
                .containsEntry("referencia_externa", datos.referenciaExterna(id))
                .containsEntry("status_http", 200)
                .containsEntry("codigo", "01");
        assertThat((String) registro.get("solicitud")).contains("******5678").doesNotContain("5512345678");
        assertThat((String) registro.get("respuesta")).contains("<PIN>****</PIN>").doesNotContain("1234-5678-9012");
        assertThat(bitacora.toString()).doesNotContain("token-de-pruebas").doesNotContain("clave-api-de-pruebas");
    }

    @Test
    void unPagoSoloLoPuedeConsultarSuDueno() throws Exception {
        proveedorAprueba();
        long id = idDe(pagar(token, "pago-consulta-1", telcel100()).andReturn());

        consultarPago(token, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"));

        datos.crearCliente("bruno");
        consultarPago(tokenDe("bruno"), id)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("PAGO_NO_ENCONTRADO"));
    }
}
