package com.utng.pagosservicios.proveedor.gestopago;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.utng.pagosservicios.dominio.catalogo.Modalidad;
import com.utng.pagosservicios.proveedor.BitacoraIntercambios;
import com.utng.pagosservicios.proveedor.BitacoraIntercambios.Intercambio;
import com.utng.pagosservicios.proveedor.BitacoraIntercambios.Operacion;
import com.utng.pagosservicios.proveedor.OrdenPago;
import com.utng.pagosservicios.proveedor.ResultadoConsulta;
import com.utng.pagosservicios.proveedor.ResultadoEnvio;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.utng.pagosservicios.proveedor.gestopago.PropiedadesDePrueba.API_KEY;
import static com.utng.pagosservicios.proveedor.gestopago.PropiedadesDePrueba.CONSULTA;
import static com.utng.pagosservicios.proveedor.gestopago.PropiedadesDePrueba.ENVIO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Prueba el adaptador contra un servidor HTTP real (WireMock), sin Spring. */
class GestopagoClientTest {

    private static final Instant MOMENTO = Instant.parse("2026-10-27T18:00:00Z");
    private static WireMockServer servidor;

    /** Telcel $100: la referencia es el telefono y no viaja monto. */
    private final OrdenPago recarga = new OrdenPago("123456789012345", 1001, 2003, Modalidad.RECARGA,
            "5512345678", new BigDecimal("100.00"), MOMENTO);
    /** CFE: viajan la referencia del recibo y el monto. */
    private final OrdenPago servicio = new OrdenPago("223456789012345", 1003, 3001, Modalidad.PAGO_SERVICIO,
            "123456789012", new BigDecimal("250.50"), MOMENTO);

    private final Clock reloj = Clock.fixed(MOMENTO, ZoneOffset.UTC);
    private final List<Intercambio> bitacora = new ArrayList<>();
    private CircuitBreaker circuito;
    private CredencialEspia credencial;
    private GestopagoClient cliente;

    /** Entrega tokens en orden: tras cada invalidacion, el siguiente. */
    static final class CredencialEspia implements CredencialGestopago {
        private final boolean renovable;
        private final List<String> tokens;
        private int indice;
        int invalidaciones;

        CredencialEspia(boolean renovable, String... tokens) {
            this.renovable = renovable;
            this.tokens = List.of(tokens);
        }

        @Override
        public String token() {
            return tokens.get(Math.min(indice, tokens.size() - 1));
        }

        @Override
        public void invalidar() {
            invalidaciones++;
            indice++;
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
        circuito = CircuitBreaker.of("gestopago-prueba", CircuitBreakerConfig.custom()
                .slidingWindowSize(4)
                .minimumNumberOfCalls(4)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMinutes(1))
                .build());
        credencial = new CredencialEspia(false, "token-unitario");
        cliente = crear("http://localhost:" + servidor.port(), CONSULTA, credencial);
    }

    private GestopagoClient crear(String urlBase, String rutaConsulta, CredencialGestopago credencialUsada) {
        GestopagoProperties propiedades = PropiedadesDePrueba.gestopago(urlBase, rutaConsulta, null);
        BitacoraIntercambios enMemoria = bitacora::add;
        return new GestopagoClient(new ConectorGestopago(GestopagoConfig.crearRestClient(propiedades), propiedades),
                JsonMapper.builder().build(), propiedades, circuito, credencialUsada,
                new CifradorAes(MensajeCifrado.LLAVE_PRUEBAS, MensajeCifrado.IV_PRUEBAS), enMemoria, reloj);
    }

    private static ResponseDefinitionBuilder xml(String cuerpo) {
        return aResponse().withStatus(200).withHeader("Content-Type", "text/xml;charset=UTF-8").withBody(cuerpo);
    }

    private void envioResponde(ResponseDefinitionBuilder respuesta) {
        servidor.stubFor(post(urlEqualTo(ENVIO)).willReturn(respuesta));
    }

    private List<LoggedRequest> enviados(String ruta) {
        return servidor.findAll(postRequestedFor(urlEqualTo(ruta)));
    }

    private MensajeCifrado ultimoMensaje(String ruta) {
        List<LoggedRequest> recibidas = enviados(ruta);
        return MensajeCifrado.de(recibidas.get(recibidas.size() - 1).getBodyAsString());
    }

    @Nested
    class Envio {

        @Test
        void aprobada_conFolioYAutorizacion() {
            envioResponde(xml(XmlPuntoRed.exito("778899", "A1B2C3")));

            ResultadoEnvio resultado = cliente.enviar(recarga);

            assertThat(resultado.desenlace()).isEqualTo(ResultadoEnvio.Desenlace.APROBADA);
            assertThat(resultado.codigo()).isEqualTo("01");
            assertThat(resultado.folio()).isEqualTo("778899");
            assertThat(resultado.autorizacion()).isEqualTo("A1B2C3");
            assertThat(resultado.canje()).isNull();
        }

        @Test
        void tarjetaDeRegalo_devuelveElPinYLasInstrucciones() {
            envioResponde(xml(XmlPuntoRed.exitoConPin("5544", "1234-5678-9012", "Canjea en la tienda")));

            ResultadoEnvio resultado = cliente.enviar(recarga);

            assertThat(resultado.canje().pin()).isEqualTo("1234-5678-9012");
            assertThat(resultado.canje().instrucciones()).isEqualTo("Canjea en la tienda");
        }

        @Test
        void enviaUnFormularioConRandomYSignedCifradoConBearerYApiKey() {
            envioResponde(xml(XmlPuntoRed.exito("1", "A")));

            cliente.enviar(recarga);

            servidor.verify(1, postRequestedFor(urlEqualTo(ENVIO))
                    .withHeader("Authorization", equalTo("Bearer token-unitario"))
                    .withHeader("X-API-Key", equalTo(API_KEY))
                    .withHeader("Content-Type", containing("application/x-www-form-urlencoded")));
            MensajeCifrado mensaje = ultimoMensaje(ENVIO);
            assertThat(mensaje.campo("random")).isNotBlank();
            // Lo que viaja no es el JSON en claro: el telefono no aparece en el cuerpo.
            assertThat(enviados(ENVIO).get(0).getBodyAsString()).doesNotContain("5512345678");
        }

        @Test
        void recarga_llevaTelefonoUpcYHoraLocalSinMontoNiReferencia() {
            envioResponde(xml(XmlPuntoRed.exito("1", "A")));

            cliente.enviar(recarga);

            MensajeCifrado mensaje = ultimoMensaje(ENVIO);
            assertThat((Integer) mensaje.dato("$.idProducto")).isEqualTo(2003);
            assertThat((Integer) mensaje.dato("$.idServicio")).isEqualTo(1001);
            assertThat((String) mensaje.dato("$.telefono")).isEqualTo("5512345678");
            assertThat((String) mensaje.dato("$.upc")).isEqualTo("123456789012345");
            // 18:00 UTC es mediodia en la Ciudad de Mexico (UTC-6, sin horario de verano).
            assertThat((String) mensaje.dato("$.horaLocal")).isEqualTo("20261027120000");
            assertThat((String) mensaje.dato("$.unidad")).isEqualTo("API-PAGOS");
            assertThat(mensaje.tiene("montoPago")).isFalse();
            assertThat(mensaje.tiene("referencia")).isFalse();
        }

        @Test
        void pagoDeServicio_llevaReferenciaYMontoPagoSinTelefono() {
            envioResponde(xml(XmlPuntoRed.exito("1", "A")));

            cliente.enviar(servicio);

            MensajeCifrado mensaje = ultimoMensaje(ENVIO);
            assertThat((String) mensaje.dato("$.referencia")).isEqualTo("123456789012");
            assertThat(new BigDecimal(mensaje.dato("$.montoPago").toString())).isEqualByComparingTo("250.50");
            assertThat(mensaje.tiene("telefono")).isFalse();
        }

        @Test
        void randomEsDistintoEnCadaPeticion() {
            envioResponde(xml(XmlPuntoRed.exito("1", "A")));

            cliente.enviar(recarga);
            cliente.enviar(servicio);

            List<LoggedRequest> recibidas = enviados(ENVIO);
            assertThat(MensajeCifrado.de(recibidas.get(0).getBodyAsString()).campo("random"))
                    .isNotEqualTo(MensajeCifrado.de(recibidas.get(1).getBodyAsString()).campo("random"));
        }

        @Test
        void saldoInsuficiente02_esRechazo() {
            envioResponde(xml(XmlPuntoRed.codigo("02", "Saldo insuficiente")));
            ResultadoEnvio resultado = cliente.enviar(recarga);
            assertThat(resultado.desenlace()).isEqualTo(ResultadoEnvio.Desenlace.RECHAZADA);
            assertThat(resultado.codigo()).isEqualTo("02");
        }

        @Test
        void duplicada06_esDuplicadaYNoSeAplico() {
            envioResponde(xml(XmlPuntoRed.codigo("06", "Transaccion duplicada")));
            assertThat(cliente.enviar(recarga).desenlace()).isEqualTo(ResultadoEnvio.Desenlace.DUPLICADA);
        }

        /** El 82 dice que el carrier no respondio en 62 s: no prueba que no se aplico. */
        @Test
        void sinRespuestaDelCarrier82_esIndeterminada() {
            envioResponde(xml(XmlPuntoRed.codigo("82", "Tiempo de espera agotado")));
            ResultadoEnvio resultado = cliente.enviar(recarga);
            assertThat(resultado.desenlace()).isEqualTo(ResultadoEnvio.Desenlace.INDETERMINADA);
            assertThat(resultado.codigo()).isEqualTo("CODIGO_82");
        }

        @Test
        void codigoDesconocido_esIndeterminadoNoRechazo() {
            envioResponde(xml(XmlPuntoRed.codigo("77", "En proceso")));
            ResultadoEnvio resultado = cliente.enviar(recarga);
            assertThat(resultado.desenlace()).isEqualTo(ResultadoEnvio.Desenlace.INDETERMINADA);
            assertThat(resultado.codigo()).isEqualTo("CODIGO_77");
        }

        @Test
        void timeoutDeLectura_esIndeterminado() {
            envioResponde(xml(XmlPuntoRed.exito("1", "A")).withFixedDelay(1500));
            ResultadoEnvio resultado = cliente.enviar(recarga);
            assertThat(resultado.desenlace()).isEqualTo(ResultadoEnvio.Desenlace.INDETERMINADA);
            assertThat(resultado.codigo()).isEqualTo("TIMEOUT_LECTURA");
        }

        @Test
        void error500_esIndeterminado() {
            envioResponde(aResponse().withStatus(500));
            ResultadoEnvio resultado = cliente.enviar(recarga);
            assertThat(resultado.desenlace()).isEqualTo(ResultadoEnvio.Desenlace.INDETERMINADA);
            assertThat(resultado.codigo()).isEqualTo("HTTP_500");
        }

        @Test
        void conexionCortadaSinRespuesta_esIndeterminado() {
            envioResponde(aResponse().withFault(Fault.EMPTY_RESPONSE));
            assertThat(cliente.enviar(recarga).desenlace()).isEqualTo(ResultadoEnvio.Desenlace.INDETERMINADA);
        }

        @Test
        void respuestaQueNoEsXmlDelProveedor_esIndeterminado() {
            envioResponde(aResponse().withStatus(200).withBody("<html>Mantenimiento</html>"));
            ResultadoEnvio resultado = cliente.enviar(recarga);
            assertThat(resultado.desenlace()).isEqualTo(ResultadoEnvio.Desenlace.INDETERMINADA);
            assertThat(resultado.codigo()).isEqualTo("RESPUESTA_ILEGIBLE");
        }

        @Test
        void respuestaExcesivamenteGrande_esIndeterminado() {
            envioResponde(aResponse().withStatus(200)
                    .withBody("x".repeat(ConectorGestopago.MAX_BYTES_RESPUESTA + 10)));
            assertThat(cliente.enviar(recarga).desenlace()).isEqualTo(ResultadoEnvio.Desenlace.INDETERMINADA);
        }

        @Test
        void error400_noFueProcesada() {
            envioResponde(aResponse().withStatus(400));
            assertThat(cliente.enviar(recarga).desenlace()).isEqualTo(ResultadoEnvio.Desenlace.NO_PROCESADA);
        }

        @Test
        void error409_puedeSerUnDuplicado_esIndeterminado() {
            envioResponde(aResponse().withStatus(409));
            assertThat(cliente.enviar(recarga).desenlace()).isEqualTo(ResultadoEnvio.Desenlace.INDETERMINADA);
        }

        @Test
        void conexionRechazada_noLlegoAlProveedor() throws IOException {
            int puertoCerrado;
            try (ServerSocket socket = new ServerSocket(0)) {
                puertoCerrado = socket.getLocalPort();
            }
            GestopagoClient sinServidor = crear("http://localhost:" + puertoCerrado, CONSULTA, credencial);

            assertThat(sinServidor.enviar(recarga).desenlace()).isEqualTo(ResultadoEnvio.Desenlace.NO_PROCESADA);
        }

        @Test
        void sinCredencial_noSeEnviaNada() {
            CredencialGestopago sinToken = () -> {
                throw new IllegalStateException("autenticacion caida");
            };
            GestopagoClient sinCredencial = crear("http://localhost:" + servidor.port(), CONSULTA, sinToken);

            ResultadoEnvio resultado = sinCredencial.enviar(recarga);

            assertThat(resultado.desenlace()).isEqualTo(ResultadoEnvio.Desenlace.NO_PROCESADA);
            assertThat(resultado.codigo()).isEqualTo("SIN_CREDENCIAL");
            servidor.verify(0, postRequestedFor(urlEqualTo(ENVIO)));
        }

        @Test
        void ordenSinIdentificadoresDelProveedor_noSeEnvia() {
            OrdenPago incompleta = new OrdenPago("323456789012345", null, null, Modalidad.RECARGA,
                    "5512345678", new BigDecimal("100.00"), MOMENTO);

            ResultadoEnvio resultado = cliente.enviar(incompleta);

            assertThat(resultado.desenlace()).isEqualTo(ResultadoEnvio.Desenlace.NO_PROCESADA);
            assertThat(resultado.codigo()).isEqualTo("SOLICITUD_INVALIDA");
            servidor.verify(0, postRequestedFor(urlEqualTo(ENVIO)));
        }
    }

    /** 403 "EXPIRED": el proveedor rechazo la credencial antes de procesar nada. */
    @Nested
    class TokenVencido {

        private GestopagoClient conTokenRenovable;
        private CredencialEspia renovable;

        @BeforeEach
        void preparar() {
            renovable = new CredencialEspia(true, "token-viejo", "token-nuevo");
            conTokenRenovable = crear("http://localhost:" + servidor.port(), CONSULTA, renovable);
            servidor.stubFor(post(urlEqualTo(ENVIO)).withHeader("Authorization", equalTo("Bearer token-viejo"))
                    .willReturn(aResponse().withStatus(403).withHeader("Content-Type", "application/json")
                            .withBody("{\"token\":\"EXPIRED\"}")));
        }

        @Test
        void seRepiteUnaVezConTokenNuevoYExactamenteLaMismaOperacion() {
            servidor.stubFor(post(urlEqualTo(ENVIO)).withHeader("Authorization", equalTo("Bearer token-nuevo"))
                    .willReturn(xml(XmlPuntoRed.exito("991", "B1"))));

            ResultadoEnvio resultado = conTokenRenovable.enviar(recarga);

            assertThat(resultado.desenlace()).isEqualTo(ResultadoEnvio.Desenlace.APROBADA);
            assertThat(renovable.invalidaciones).isEqualTo(1);
            List<LoggedRequest> recibidas = enviados(ENVIO);
            assertThat(recibidas).hasSize(2);
            // Mismo UPC y mismo contenido cifrado: el proveedor lo ve como la misma operacion.
            assertThat(MensajeCifrado.de(recibidas.get(0).getBodyAsString()).campo("signed"))
                    .isEqualTo(MensajeCifrado.de(recibidas.get(1).getBodyAsString()).campo("signed"));
        }

        @Test
        void siLaRepeticionTambienEsRechazada_noSeProcesoYNoHayTercerIntento() {
            servidor.stubFor(post(urlEqualTo(ENVIO)).withHeader("Authorization", equalTo("Bearer token-nuevo"))
                    .willReturn(aResponse().withStatus(403)));

            ResultadoEnvio resultado = conTokenRenovable.enviar(recarga);

            assertThat(resultado.desenlace()).isEqualTo(ResultadoEnvio.Desenlace.NO_PROCESADA);
            assertThat(resultado.codigo()).isEqualTo("HTTP_403");
            servidor.verify(2, postRequestedFor(urlEqualTo(ENVIO)));
        }

        /** Tras repetir, un 06 podria referirse al primer envio de este mismo UPC. */
        @Test
        void un06TrasRepetir_esIndeterminadoNoRechazo() {
            servidor.stubFor(post(urlEqualTo(ENVIO)).withHeader("Authorization", equalTo("Bearer token-nuevo"))
                    .willReturn(xml(XmlPuntoRed.codigo("06", "Transaccion duplicada"))));

            ResultadoEnvio resultado = conTokenRenovable.enviar(recarga);

            assertThat(resultado.desenlace()).isEqualTo(ResultadoEnvio.Desenlace.INDETERMINADA);
            assertThat(resultado.codigo()).isEqualTo("CODIGO_06_TRAS_REINTENTO");
        }

        @Test
        void conTokenFijo_noSeRepite() {
            GestopagoClient conTokenFijo = crear("http://localhost:" + servidor.port(), CONSULTA,
                    new CredencialEspia(false, "token-viejo"));

            assertThat(conTokenFijo.enviar(recarga).desenlace()).isEqualTo(ResultadoEnvio.Desenlace.NO_PROCESADA);
            servidor.verify(1, postRequestedFor(urlEqualTo(ENVIO)));
        }
    }

    @Nested
    class Bitacora {

        @Test
        void registraCadaIntercambioSinSecretos() {
            envioResponde(xml(XmlPuntoRed.exitoConPin("778899", "1234-5678-9012", "Canje")));

            cliente.enviar(recarga);

            assertThat(bitacora).hasSize(1);
            Intercambio registro = bitacora.get(0);
            assertThat(registro.operacion()).isEqualTo(Operacion.ENVIO);
            assertThat(registro.referenciaExterna()).isEqualTo("123456789012345");
            assertThat(registro.statusHttp()).isEqualTo(200);
            assertThat(registro.codigo()).isEqualTo("01");
            assertThat(registro.solicitud()).contains("******5678").doesNotContain("5512345678")
                    .contains("123456789012345");
            String signed = MensajeCifrado.formulario(enviados(ENVIO).get(0).getBodyAsString()).get("signed");
            assertThat(registro.solicitud()).doesNotContain(signed).doesNotContain("token-unitario");
            assertThat(registro.respuesta()).contains("<PIN>****</PIN>").doesNotContain("1234-5678-9012")
                    .doesNotContain("5512345678");
        }

        @Test
        void unTimeoutQuedaRegistradoComoErrorSinRespuesta() {
            envioResponde(xml(XmlPuntoRed.exito("1", "A")).withFixedDelay(1500));

            cliente.enviar(recarga);

            assertThat(bitacora).hasSize(1);
            assertThat(bitacora.get(0).statusHttp()).isNull();
            assertThat(bitacora.get(0).error()).isEqualTo("TIMEOUT_LECTURA");
        }
    }

    @Nested
    class CircuitBreakerDelProveedor {

        @Test
        void tras_fallosRepetidos_seAbreYYaNoSeEnviaNada() {
            envioResponde(aResponse().withStatus(500));
            for (int i = 0; i < 4; i++) {
                cliente.enviar(recarga);
            }

            assertThat(cliente.disponible()).isFalse();
            ResultadoEnvio resultado = cliente.enviar(recarga);
            assertThat(resultado.desenlace()).isEqualTo(ResultadoEnvio.Desenlace.NO_PROCESADA);
            assertThat(resultado.codigo()).isEqualTo("CIRCUITO_ABIERTO");
            servidor.verify(4, postRequestedFor(urlEqualTo(ENVIO)));
        }

        @Test
        void losRechazosDeNegocioNoAbrenElCircuito() {
            envioResponde(xml(XmlPuntoRed.codigo("02", "Saldo insuficiente")));
            for (int i = 0; i < 6; i++) {
                cliente.enviar(recarga);
            }
            assertThat(cliente.disponible()).isTrue();
        }
    }

    @Nested
    class Consulta {

        private void consultaResponde(ResponseDefinitionBuilder respuesta) {
            servidor.stubFor(post(urlEqualTo(CONSULTA)).willReturn(respuesta));
        }

        @Test
        void aplicada_preguntaConLaMismaOrdenQueSeEnvio() {
            consultaResponde(xml(XmlPuntoRed.exito("F-9", "A-9")));

            ResultadoConsulta resultado = cliente.consultar(recarga);

            assertThat(resultado.estado()).isEqualTo(ResultadoConsulta.Estado.APROBADA);
            assertThat(resultado.folio()).isEqualTo("F-9");
            MensajeCifrado mensaje = ultimoMensaje(CONSULTA);
            assertThat((String) mensaje.dato("$.upc")).isEqualTo("123456789012345");
            assertThat((String) mensaje.dato("$.horaLocal")).isEqualTo("20261027120000");
            servidor.verify(0, postRequestedFor(urlEqualTo(ENVIO)));
            assertThat(bitacora.get(0).operacion()).isEqualTo(Operacion.CONSULTA);
        }

        @Test
        void noAplicadaSoloConElCodigoConfigurado() {
            consultaResponde(xml(XmlPuntoRed.codigo("04", "No aplicada")));
            assertThat(cliente.consultar(recarga).estado()).isEqualTo(ResultadoConsulta.Estado.NO_ENCONTRADA);
        }

        /** Un codigo de rechazo de sendTx no significa lo mismo en confirmTx: no se reversa con el. */
        @Test
        void unCodigoDeEnvioNoSeInterpretaEnLaConsulta() {
            consultaResponde(xml(XmlPuntoRed.codigo("02", "Saldo insuficiente")));
            ResultadoConsulta resultado = cliente.consultar(recarga);
            assertThat(resultado.estado()).isEqualTo(ResultadoConsulta.Estado.INDETERMINADA);
            assertThat(resultado.codigo()).isEqualTo("CODIGO_02");
        }

        @Test
        void un404NoSeInterpretaComoNoAplicada() {
            consultaResponde(aResponse().withStatus(404));
            assertThat(cliente.consultar(recarga).estado()).isEqualTo(ResultadoConsulta.Estado.INDETERMINADA);
        }

        @Test
        void un403ConTokenFijo_esIndeterminado() {
            consultaResponde(aResponse().withStatus(403));
            assertThat(cliente.consultar(recarga).estado()).isEqualTo(ResultadoConsulta.Estado.INDETERMINADA);
            assertThat(credencial.invalidaciones).isEqualTo(1);
        }

        @Test
        void sinEndpointDeConsultaConfigurado() {
            GestopagoClient sinConsulta = crear("http://localhost:" + servidor.port(), "", credencial);
            assertThat(sinConsulta.consultar(recarga).estado()).isEqualTo(ResultadoConsulta.Estado.NO_SOPORTADA);
        }
    }

    @Test
    void laRutaDeConsultaNuncaPuedeSerLaDeEnvio() {
        assertThatThrownBy(() -> GestopagoConfig.exigirRutasDistintas(new GestopagoProperties.Endpoints(
                ENVIO, "/auth", "/catalogo", "/SISTEMA/service/sendTx.do/")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("reenviaria el cargo");
    }

    @Test
    void sanearLimpiaYTruncaLosDatosDelProveedor() {
        assertThat(GestopagoClient.sanear(" F-1\n<script> ", 80)).isEqualTo("F-1script");
        assertThat(GestopagoClient.sanear("1234567890", 4)).isEqualTo("1234");
        assertThat(GestopagoClient.sanear("###", 10)).isNull();
        assertThat(GestopagoClient.sanear(null, 10)).isNull();
    }
}
