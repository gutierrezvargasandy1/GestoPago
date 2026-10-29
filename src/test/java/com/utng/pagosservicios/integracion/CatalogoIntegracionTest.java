package com.utng.pagosservicios.integracion;

import com.utng.pagosservicios.proveedor.gestopago.MensajeCifrado;
import com.utng.pagosservicios.proveedor.gestopago.XmlPuntoRed;
import com.utng.pagosservicios.servicio.SincronizacionCatalogoService;
import com.utng.pagosservicios.servicio.SincronizacionCatalogoService.Resultado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sincronizacion con getProductList: el catalogo del proveedor se copia a la
 * base, se expone en la API y sus identificadores son los que viajan en sendTx.
 */
class CatalogoIntegracionTest extends IntegracionBase {

    @Autowired
    private SincronizacionCatalogoService sincronizacion;

    private String token;

    @BeforeEach
    void preparar() {
        token = clienteConCuenta("ana", CUENTA, "1000.00");
        GESTOPAGO.stubFor(get(urlEqualTo(RUTA_CATALOGO)).willReturn(xml(XmlPuntoRed.catalogo(
                XmlPuntoRed.producto(1, "Telcel", 10, "Telcel $20", "20.00", 1),
                XmlPuntoRed.producto(1, "Telcel", 11, "Telcel $50", "50.00", 1),
                XmlPuntoRed.producto(7, "CFE", 70, "Pago de recibo CFE", "0", 2)))));
    }

    @Test
    void copiaElCatalogoDelProveedorYLoExponeEnLaApi() throws Exception {
        assertThat(sincronizacion.sincronizarSiCorresponde()).isEqualTo(Resultado.EXITOSA);

        mvc.perform(MockMvcRequestBuilders.get("/api/v1/catalogo/servicios").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].idServicio", hasItem("1")))
                .andExpect(jsonPath("$[*].idServicio", hasItem("7")));
        mvc.perform(MockMvcRequestBuilders.get("/api/v1/catalogo/servicios/1/productos").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].idProducto").value("10"))
                .andExpect(jsonPath("$[0].modalidad").value("RECARGA"))
                .andExpect(jsonPath("$[0].montoFijo").value("20.00"));
        mvc.perform(MockMvcRequestBuilders.get("/api/v1/catalogo/servicios/7/productos").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$[0].modalidad").value("PAGO_SERVICIO"))
                .andExpect(jsonPath("$[0].tipoMonto").value("ABIERTO"));
    }

    /** El proveedor pide consultarlo una vez al dia: una segunda llamada el mismo dia no sale. */
    @Test
    void noVuelveAConsultarAlProveedorSiYaSincronizoHoy() {
        sincronizacion.sincronizarSiCorresponde();

        assertThat(sincronizacion.sincronizarSiCorresponde()).isEqualTo(Resultado.OMITIDA_RECIENTE);
        GESTOPAGO.verify(1, getRequestedFor(urlEqualTo(RUTA_CATALOGO)));
    }

    @Test
    void siElProveedorFallaSeConservaElCatalogoLocal() {
        sincronizacion.sincronizarSiCorresponde();
        datos.sincronizacionHace(25);
        GESTOPAGO.stubFor(get(urlEqualTo(RUTA_CATALOGO)).willReturn(aResponse().withStatus(503)));

        assertThat(sincronizacion.sincronizarSiCorresponde()).isEqualTo(Resultado.FALLIDA);
        assertThat(datos.productoActivo("1", "10")).isTrue();
    }

    @Test
    void loQueElProveedorDejaDePublicarSeDesactiva() {
        sincronizacion.sincronizarSiCorresponde();
        datos.sincronizacionHace(25);
        GESTOPAGO.stubFor(get(urlEqualTo(RUTA_CATALOGO)).willReturn(xml(XmlPuntoRed.catalogo(
                XmlPuntoRed.producto(1, "Telcel", 10, "Telcel $20", "20.00", 1)))));

        assertThat(sincronizacion.sincronizarSiCorresponde()).isEqualTo(Resultado.EXITOSA);
        assertThat(datos.productoActivo("1", "10")).isTrue();
        assertThat(datos.productoActivo("1", "11")).isFalse();
    }

    @Test
    void pagarUnProductoSincronizado_enviaLosIdentificadoresDelProveedor() throws Exception {
        sincronizacion.sincronizarSiCorresponde();
        proveedorAprueba();

        pagar(token, "catalogo-pago-01", solicitud(CUENTA, "1", "11", "5512345678", "50.00"))
                .andExpect(status().isCreated());

        MensajeCifrado mensaje = mensajesRecibidos(RUTA_ENVIO).get(0);
        assertThat((Integer) mensaje.dato("$.idServicio")).isEqualTo(1);
        assertThat((Integer) mensaje.dato("$.idProducto")).isEqualTo(11);
        assertThat(datos.saldo(CUENTA)).isEqualByComparingTo("950.00");
    }
}
