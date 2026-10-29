package com.utng.pagosservicios.proveedor.gestopago;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.utng.pagosservicios.dominio.catalogo.Modalidad;
import com.utng.pagosservicios.proveedor.BitacoraIntercambios;
import com.utng.pagosservicios.proveedor.BitacoraIntercambios.Intercambio;
import com.utng.pagosservicios.proveedor.BitacoraIntercambios.Operacion;
import com.utng.pagosservicios.proveedor.CatalogoObtenido;
import com.utng.pagosservicios.proveedor.ProductoProveedor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.utng.pagosservicios.proveedor.gestopago.PropiedadesDePrueba.API_KEY;
import static com.utng.pagosservicios.proveedor.gestopago.PropiedadesDePrueba.CATALOGO;
import static org.assertj.core.api.Assertions.assertThat;

class GestopagoCatalogoTest {

    private static WireMockServer servidor;

    private final List<Intercambio> bitacora = new ArrayList<>();
    private GestopagoCatalogoTest.Credencial credencial;
    private GestopagoCatalogo catalogo;

    static final class Credencial implements CredencialGestopago {
        private final boolean renovable;
        private int invalidaciones;

        Credencial(boolean renovable) {
            this.renovable = renovable;
        }

        @Override
        public String token() {
            return invalidaciones == 0 ? "token-viejo" : "token-nuevo";
        }

        @Override
        public void invalidar() {
            invalidaciones++;
        }

        @Override
        public boolean renovable() {
            return renovable;
        }
    }

    @BeforeAll
    static void iniciar() {
        servidor = new WireMockServer(options().dynamicPort());
        servidor.start();
    }

    @AfterAll
    static void detener() {
        servidor.stop();
    }

    @BeforeEach
    void preparar() {
        servidor.resetAll();
        bitacora.clear();
        credencial = new Credencial(false);
        catalogo = crear(List.of(), credencial);
    }

    private GestopagoCatalogo crear(List<Integer> tiposFrontPagoServicio, CredencialGestopago credencialUsada) {
        GestopagoProperties propiedades = PropiedadesDePrueba.gestopago("http://localhost:" + servidor.port(),
                PropiedadesDePrueba.CONSULTA, null, false, tiposFrontPagoServicio);
        BitacoraIntercambios enMemoria = bitacora::add;
        return new GestopagoCatalogo(new ConectorGestopago(GestopagoConfig.crearRestClient(propiedades), propiedades),
                propiedades, credencialUsada, enMemoria, Clock.fixed(Instant.parse("2026-10-27T09:10:00Z"), ZoneOffset.UTC));
    }

    private static ResponseDefinitionBuilder xml(String cuerpo) {
        return aResponse().withStatus(200).withHeader("Content-Type", "text/xml").withBody(cuerpo);
    }

    private void catalogoResponde(ResponseDefinitionBuilder respuesta) {
        servidor.stubFor(get(urlEqualTo(CATALOGO)).willReturn(respuesta));
    }

    @Test
    void leeLosProductosYDecideLaModalidadPorElPrecio() {
        catalogoResponde(xml(XmlPuntoRed.catalogo(
                XmlPuntoRed.producto(1, "Telcel", 10, "Telcel $20", "20.00", 1),
                XmlPuntoRed.producto(7, "CFE", 70, "Pago de recibo CFE", "0", 2))));

        CatalogoObtenido resultado = catalogo.obtenerCatalogo();

        assertThat(resultado.exitoso()).isTrue();
        assertThat(resultado.productos()).hasSize(2);
        ProductoProveedor telcel = resultado.productos().get(0);
        assertThat(telcel.idServicio()).isEqualTo(1);
        assertThat(telcel.idProducto()).isEqualTo(10);
        assertThat(telcel.servicio()).isEqualTo("Telcel");
        assertThat(telcel.producto()).isEqualTo("Telcel $20");
        assertThat(telcel.modalidad()).isEqualTo(Modalidad.RECARGA);
        assertThat(telcel.precio()).isEqualByComparingTo("20.00");
        assertThat(telcel.categoria()).isEqualTo("TIPO_1");
        assertThat(telcel.leyenda()).isEqualTo("Instrucciones de Telcel $20");
        assertThat(resultado.productos().get(1).modalidad()).isEqualTo(Modalidad.PAGO_SERVICIO);
    }

    @Test
    void unTipoFrontConfiguradoComoServicioManda() {
        catalogo = crear(List.of(2), credencial);
        catalogoResponde(xml(XmlPuntoRed.catalogo(XmlPuntoRed.producto(7, "Agua", 71, "Agua $150", "150.00", 2))));

        assertThat(catalogo.obtenerCatalogo().productos().get(0).modalidad()).isEqualTo(Modalidad.PAGO_SERVICIO);
    }

    @Test
    void enviaElTokenYLaApiKey() {
        catalogoResponde(xml(XmlPuntoRed.catalogo(XmlPuntoRed.producto(1, "Telcel", 10, "Telcel $20", "20.00", 1))));

        catalogo.obtenerCatalogo();

        servidor.verify(1, getRequestedFor(urlEqualTo(CATALOGO))
                .withHeader("Authorization", equalTo("Bearer token-viejo"))
                .withHeader("X-API-Key", equalTo(API_KEY)));
    }

    @Test
    void descartaProductosSinIdentificadores() {
        catalogoResponde(xml(XmlPuntoRed.catalogo(
                XmlPuntoRed.producto(1, "Telcel", 10, "Telcel $20", "20.00", 1),
                "<producto servicio=\"Sin numero\" producto=\"X\" idServicio=\"abc\" precio=\"5\"/>")));

        assertThat(catalogo.obtenerCatalogo().productos()).hasSize(1);
    }

    /** Un catalogo vacio desactivaria todos los productos: nunca se toma como exito. */
    @Test
    void unCatalogoVacioEsUnFallo() {
        catalogoResponde(xml(XmlPuntoRed.catalogo()));
        CatalogoObtenido resultado = catalogo.obtenerCatalogo();
        assertThat(resultado.exitoso()).isFalse();
        assertThat(resultado.detalle()).isEqualTo("CATALOGO_VACIO");
    }

    @Test
    void unCodigoDistintoDe01EsUnFallo() {
        catalogoResponde(xml(XmlPuntoRed.codigo("10", "Limite de consultas excedido")));
        CatalogoObtenido resultado = catalogo.obtenerCatalogo();
        assertThat(resultado.exitoso()).isFalse();
        assertThat(resultado.detalle()).isEqualTo("CODIGO_10");
    }

    @Test
    void unErrorDelServidorEsUnFallo() {
        catalogoResponde(aResponse().withStatus(503));
        assertThat(catalogo.obtenerCatalogo().detalle()).isEqualTo("HTTP_503");
    }

    @Test
    void conTokenVencidoYCredencialRenovableRepiteUnaVez() {
        Credencial renovable = new Credencial(true);
        catalogo = crear(List.of(), renovable);
        servidor.stubFor(get(urlEqualTo(CATALOGO)).withHeader("Authorization", equalTo("Bearer token-viejo"))
                .willReturn(aResponse().withStatus(403)));
        servidor.stubFor(get(urlEqualTo(CATALOGO)).withHeader("Authorization", equalTo("Bearer token-nuevo"))
                .willReturn(xml(XmlPuntoRed.catalogo(XmlPuntoRed.producto(1, "Telcel", 10, "Telcel $20", "20.00", 1)))));

        assertThat(catalogo.obtenerCatalogo().exitoso()).isTrue();
        servidor.verify(2, getRequestedFor(urlEqualTo(CATALOGO)));
    }

    @Test
    void laBitacoraGuardaUnResumenNoElCatalogoCompleto() {
        catalogoResponde(xml(XmlPuntoRed.catalogo(
                XmlPuntoRed.producto(1, "Telcel", 10, "Telcel $20", "20.00", 1),
                XmlPuntoRed.producto(1, "Telcel", 11, "Telcel $50", "50.00", 1))));

        catalogo.obtenerCatalogo();

        assertThat(bitacora).hasSize(1);
        assertThat(bitacora.get(0).operacion()).isEqualTo(Operacion.CATALOGO);
        assertThat(bitacora.get(0).respuesta()).isEqualTo("2 productos");
    }
}
