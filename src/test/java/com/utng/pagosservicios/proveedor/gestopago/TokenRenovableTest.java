package com.utng.pagosservicios.proveedor.gestopago;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.utng.pagosservicios.RelojAjustable;
import com.utng.pagosservicios.proveedor.BitacoraIntercambios;
import com.utng.pagosservicios.proveedor.BitacoraIntercambios.Intercambio;
import com.utng.pagosservicios.proveedor.BitacoraIntercambios.Operacion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.utng.pagosservicios.proveedor.gestopago.PropiedadesDePrueba.API_KEY;
import static com.utng.pagosservicios.proveedor.gestopago.PropiedadesDePrueba.AUTENTICACION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenRenovableTest {

    private static final Instant INICIO = Instant.parse("2026-10-27T18:00:00Z");
    private static WireMockServer servidor;

    private final List<Intercambio> bitacora = new ArrayList<>();
    private RelojAjustable reloj;
    private TokenRenovable credencial;

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
        reloj = new RelojAjustable(INICIO);
        credencial = crear(false);
    }

    private TokenRenovable crear(boolean credencialesEnUrl) {
        GestopagoProperties propiedades = PropiedadesDePrueba.gestopago("http://localhost:" + servidor.port(),
                PropiedadesDePrueba.CONSULTA, null, credencialesEnUrl, List.of());
        BitacoraIntercambios enMemoria = bitacora::add;
        return new TokenRenovable(new ConectorGestopago(GestopagoConfig.crearRestClient(propiedades), propiedades),
                JsonMapper.builder().build(), AUTENTICACION, propiedades.autenticacion(), enMemoria, reloj);
    }

    /** JWT sin firma valida: al cliente solo le interesa leer el claim exp. */
    private static String jwtQueVenceEn(Duration vigencia) {
        Base64.Encoder base64 = Base64.getUrlEncoder().withoutPadding();
        String cabecera = base64.encodeToString("{\"alg\":\"HS256\"}".getBytes(StandardCharsets.UTF_8));
        long exp = INICIO.plus(vigencia).getEpochSecond();
        String carga = base64.encodeToString(("{\"sub\":\"DIST-1\",\"exp\":" + exp + "}").getBytes(StandardCharsets.UTF_8));
        return cabecera + "." + carga + ".firma";
    }

    private void autenticacionDevuelve(String cuerpoJson) {
        servidor.stubFor(post(urlPathEqualTo(AUTENTICACION)).willReturn(okJson(cuerpoJson)));
    }

    @Test
    void obtieneElTokenUnaVezYLoReutilizaMientrasEsteVigente() {
        String jwt = jwtQueVenceEn(Duration.ofHours(24));
        autenticacionDevuelve("{\"token\":\"" + jwt + "\",\"success\":true}");

        assertThat(credencial.token()).isEqualTo(jwt);
        reloj.avanzar(Duration.ofHours(12));
        assertThat(credencial.token()).isEqualTo(jwt);

        servidor.verify(1, postRequestedFor(urlPathEqualTo(AUTENTICACION)));
    }

    /** En el cuerpo y no en la URL: los parametros de una URL terminan en logs de servidores. */
    @Test
    void enviaLasCredencialesEnElCuerpoComoFormulario() {
        autenticacionDevuelve("{\"token\":\"" + jwtQueVenceEn(Duration.ofHours(24)) + "\",\"success\":true}");

        credencial.token();

        servidor.verify(postRequestedFor(urlEqualTo(AUTENTICACION))
                .withHeader("Content-Type", containing("application/x-www-form-urlencoded"))
                .withHeader("X-API-Key", equalTo(API_KEY))
                .withRequestBody(equalTo(
                        "idDistribuidor=DIST-1&codigoDispositivo=DISP-1&password=secreto%2Bdistribuidor")));
    }

    @Test
    void siSeConfiguraLasEnviaEnLaUrlComoElEjemploDeLaDocumentacion() {
        credencial = crear(true);
        autenticacionDevuelve("{\"token\":\"" + jwtQueVenceEn(Duration.ofHours(24)) + "\",\"success\":true}");

        credencial.token();

        servidor.verify(postRequestedFor(urlEqualTo(AUTENTICACION
                + "?idDistribuidor=DIST-1&codigoDispositivo=DISP-1&password=secreto%2Bdistribuidor")));
    }

    @Test
    void renuevaPocoAntesDeQueVenza() {
        autenticacionDevuelve("{\"token\":\"" + jwtQueVenceEn(Duration.ofHours(24)) + "\",\"success\":true}");
        credencial.token();

        // La vigencia configurada (23 h) manda sobre el exp del token (24 h); se renueva 5 min antes.
        reloj.avanzar(Duration.ofHours(23).minusMinutes(4));
        credencial.token();

        servidor.verify(2, postRequestedFor(urlPathEqualTo(AUTENTICACION)));
    }

    @Test
    void trasInvalidarloPideUnoNuevo() {
        autenticacionDevuelve("{\"token\":\"" + jwtQueVenceEn(Duration.ofHours(24)) + "\",\"success\":true}");
        credencial.token();

        credencial.invalidar();
        reloj.avanzar(Duration.ofMinutes(1));
        credencial.token();

        servidor.verify(2, postRequestedFor(urlPathEqualTo(AUTENTICACION)));
        assertThat(credencial.renovable()).isTrue();
    }

    /** El proveedor no permite pedir un token por llamada: nunca se autentica en rafaga. */
    @Test
    void noRepiteLaAutenticacionEnRafaga() {
        autenticacionDevuelve("{\"token\":\"" + jwtQueVenceEn(Duration.ofHours(24)) + "\",\"success\":true}");
        credencial.token();
        credencial.invalidar();

        assertThatThrownBy(() -> credencial.token()).isInstanceOf(IllegalStateException.class);
        servidor.verify(1, postRequestedFor(urlPathEqualTo(AUTENTICACION)));
    }

    @Test
    void aceptaAccessTokenConExpiresIn() {
        autenticacionDevuelve("{\"access_token\":\"opaco-123\",\"expires_in\":3600}");
        assertThat(credencial.token()).isEqualTo("opaco-123");
    }

    @Test
    void successFalseEsUnRechazoAunqueTraigaToken() {
        autenticacionDevuelve("{\"token\":\"no-sirve\",\"success\":false}");
        assertThatThrownBy(() -> credencial.token()).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("success=false");
    }

    @Test
    void siLaAutenticacionFallaLanzaExcepcionSinExponerCredenciales() {
        servidor.stubFor(post(urlPathEqualTo(AUTENTICACION)).willReturn(aResponse().withStatus(401)));
        assertThatThrownBy(() -> credencial.token())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTP 401")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("secreto").doesNotContain("DIST-1"));
    }

    @Test
    void respuestaSinToken() {
        autenticacionDevuelve("{\"mensaje\":\"ok\"}");
        assertThatThrownBy(() -> credencial.token()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void laBitacoraRegistraLaAutenticacionSinElTokenNiLasCredenciales() {
        String jwt = jwtQueVenceEn(Duration.ofHours(24));
        autenticacionDevuelve("{\"token\":\"" + jwt + "\",\"success\":true}");

        credencial.token();

        assertThat(bitacora).hasSize(1);
        Intercambio registro = bitacora.get(0);
        assertThat(registro.operacion()).isEqualTo(Operacion.AUTENTICACION);
        assertThat(registro.statusHttp()).isEqualTo(200);
        assertThat(registro.solicitud()).isNull();
        assertThat(registro.respuesta()).isNull();
    }
}
