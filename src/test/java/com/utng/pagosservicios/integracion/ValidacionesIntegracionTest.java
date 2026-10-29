package com.utng.pagosservicios.integracion;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ninguna solicitud invalida debe mover dinero ni llegar al proveedor: se
 * verifica despues de cada prueba que el saldo sigue intacto y que Gestopago
 * no recibio nada.
 */
class ValidacionesIntegracionTest extends IntegracionBase {

    private String token;

    @BeforeEach
    void cliente() {
        token = clienteConCuenta("ana", CUENTA, "1000.00");
        proveedorAprueba();
    }

    @AfterEach
    void nadaSeMovio() {
        assertThat(datos.saldo(CUENTA)).isEqualByComparingTo("1000.00");
        assertThat(datos.contarTransacciones()).isZero();
        GESTOPAGO.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void productoQueNoExisteEnElCatalogo() throws Exception {
        pagar(token, "val-producto-01", solicitud(CUENTA, "TAE_TELCEL", "TEL_999", "5512345678", "100.00"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.codigo").value("PRODUCTO_NO_DISPONIBLE"));
    }

    @Test
    void productoDesactivado() throws Exception {
        pagar(token, "val-inactivo-01", solicitud(CUENTA, "TAE_TELCEL", "TEL_500", "5512345678", "500.00"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.codigo").value("PRODUCTO_NO_DISPONIBLE"));
    }

    @Test
    void montoManipuladoEnUnProductoDeMontoFijo() throws Exception {
        pagar(token, "val-monto-0001", solicitud(CUENTA, "TAE_TELCEL", "TEL_100", "5512345678", "1.00"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.codigo").value("MONTO_NO_PERMITIDO"));
    }

    @Test
    void montoFueraDeRangoONoMultiplo() throws Exception {
        pagar(token, "val-rango-0001", solicitud(CUENTA, "CFE", "CFE_PAGO", "123456789012", "5.00"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.codigo").value("MONTO_NO_PERMITIDO"));
        pagar(token, "val-multiplo-01", solicitud(CUENTA, "CFE", "CFE_PAGO", "123456789012", "100.25"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.codigo").value("MONTO_NO_PERMITIDO"));
    }

    @Test
    void referenciaConFormatoIncorrectoParaElServicio() throws Exception {
        pagar(token, "val-referencia1", solicitud(CUENTA, "TAE_TELCEL", "TEL_100", "12345", "100.00"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.codigo").value("REFERENCIA_INVALIDA"));
    }

    @Test
    void camposFaltantesOConFormatoIncorrecto() throws Exception {
        pagar(token, "val-campos-0001", "{\"numeroCuenta\":\"abc\",\"monto\":-5}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"))
                .andExpect(jsonPath("$.errores[*].campo", hasItem("numeroCuenta")))
                .andExpect(jsonPath("$.errores[*].campo", hasItem("idServicio")))
                .andExpect(jsonPath("$.errores[*].campo", hasItem("monto")));
    }

    @Test
    void montoConMasDeDosDecimales() throws Exception {
        pagar(token, "val-decimal-001", solicitud(CUENTA, "CFE", "CFE_PAGO", "123456789012", "100.005"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[*].campo", hasItem("monto")));
    }

    /** Un cliente no puede fijar la comision ni ningun otro campo que la API no espera. */
    @Test
    void campoNoPermitidoEnElCuerpo() throws Exception {
        String conComision = """
                {"numeroCuenta":"1000000001","idServicio":"CFE","idProducto":"CFE_PAGO",
                 "referencia":"123456789012","monto":100.00,"comision":0}
                """;
        pagar(token, "val-extra-00001", conComision)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
    }

    @Test
    void jsonMalformado() throws Exception {
        pagar(token, "val-json-000001", "{\"numeroCuenta\": ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
    }

    @Test
    void cuentaBloqueada() throws Exception {
        long otro = datos.crearCliente("bruno");
        datos.crearCuenta(otro, "2000000001", "1000.00", "BLOQUEADA", "10000.00");
        pagar(tokenDe("bruno"), "val-bloqueada-1", solicitud("2000000001", "TAE_TELCEL", "TEL_100", "5512345678", "100.00"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.codigo").value("CUENTA_INACTIVA"));
    }

    @Test
    void cuentaDeOtroClienteYCuentaInexistenteRespondenIgual() throws Exception {
        long otro = datos.crearCliente("bruno");
        datos.crearCuenta(otro, "2000000001", "1000.00");

        pagar(token, "val-ajena-00001", solicitud("2000000001", "TAE_TELCEL", "TEL_100", "5512345678", "100.00"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("CUENTA_NO_AUTORIZADA"));
        pagar(token, "val-noexiste-01", solicitud("9999999999", "TAE_TELCEL", "TEL_100", "5512345678", "100.00"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("CUENTA_NO_AUTORIZADA"));

        assertThat(datos.saldo("2000000001")).isEqualByComparingTo("1000.00");
    }

    @Test
    void saldoInsuficienteConsiderandoLaComision() throws Exception {
        datos.fijarSaldo(CUENTA, "255.00");
        // 250.00 + 8.00 de comision = 258.00 > 255.00
        pagar(token, "val-saldo-00001", solicitud(CUENTA, "CFE", "CFE_PAGO", "123456789012", "250.00"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.codigo").value("SALDO_INSUFICIENTE"));
        datos.fijarSaldo(CUENTA, "1000.00");
    }

    @Test
    void limiteDiarioDeLaCuenta() throws Exception {
        long otro = datos.crearCliente("bruno");
        datos.crearCuenta(otro, "2000000001", "5000.00", "ACTIVA", "150.00");
        pagar(tokenDe("bruno"), "val-limite-0001", solicitud("2000000001", "TAE_TELCEL", "TEL_200", "5512345678", "200.00"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.codigo").value("LIMITE_DIARIO_EXCEDIDO"));
    }

    @Test
    void permisoDePagosRevocadoDespuesDeEmitirElToken() throws Exception {
        datos.crearCliente("carla", true, false);
        // El token se emite con permiso, pero en base de datos ya no lo tiene.
        pagar(tokenDe("carla"), "val-revocado-01", telcel100())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("CLIENTE_NO_HABILITADO"));
    }
}
