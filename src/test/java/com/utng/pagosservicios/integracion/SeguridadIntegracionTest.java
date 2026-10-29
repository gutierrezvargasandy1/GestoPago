package com.utng.pagosservicios.integracion;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeguridadIntegracionTest extends IntegracionBase {

    @Autowired
    private JwtEncoder codificadorDeLaApi;

    @BeforeEach
    void cliente() {
        clienteConCuenta("ana", CUENTA, "1000.00");
    }

    private String tokenFirmado(JwtEncoder codificador, String emisor, Instant emitido, Instant vence) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(emisor).subject("ana").issuedAt(emitido).expiresAt(vence).claim("scope", "pagos")
                .build();
        return codificador.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    @Test
    void sinToken_401EnFormatoProblemDetail() throws Exception {
        pagar(null, "seg-sin-token-1", telcel100())
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("Bearer")))
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.traceId").exists());
        assertThat(datos.contarTransacciones()).isZero();
    }

    @Test
    void tokenMalformado_401() throws Exception {
        pagar("esto.no.es-un-jwt", "seg-malformado1", telcel100()).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenFirmadoConOtraClave_401() throws Exception {
        NimbusJwtEncoder falsificador = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(
                "otra-clave-que-no-es-la-de-la-api-0123456789".getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        Instant ahora = Instant.now();
        String falso = tokenFirmado(falsificador, "pagos-servicios", ahora, ahora.plus(Duration.ofMinutes(10)));

        pagar(falso, "seg-falsificado", telcel100()).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenExpirado_401() throws Exception {
        Instant ahora = Instant.now();
        String vencido = tokenFirmado(codificadorDeLaApi, "pagos-servicios",
                ahora.minus(Duration.ofHours(2)), ahora.minus(Duration.ofHours(1)));

        pagar(vencido, "seg-expirado-01", telcel100()).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenDeOtroEmisor_401() throws Exception {
        Instant ahora = Instant.now();
        String ajeno = tokenFirmado(codificadorDeLaApi, "otro-sistema", ahora, ahora.plus(Duration.ofMinutes(10)));

        pagar(ajeno, "seg-emisor-0001", telcel100()).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSinPermisoDePagos_403() throws Exception {
        String sinScope = emisorTokens.emitir("ana", false).valor();

        pagar(sinScope, "seg-sin-scope-1", telcel100())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    @Test
    void loginCorrecto_entregaUnTokenQueSirveParaOperar() throws Exception {
        MvcResult resultado = mvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"ana\",\"password\":\"" + DatosPrueba.PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiraEnSegundos").value(900))
                .andReturn();
        String token = JsonPath.read(resultado.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.accessToken");

        mvc.perform(get("/api/v1/catalogo/servicios/TAE_TELCEL/productos")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].idServicio").value("TAE_TELCEL"));
    }

    @Test
    void contrasenaIncorrectaYUsuarioInexistenteRespondenIgual() throws Exception {
        String incorrecta = mvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"ana\",\"password\":\"incorrecta\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String inexistente = mvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"nadie\",\"password\":\"incorrecta\"}"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        String detalle = JsonPath.read(incorrecta, "$.detail");
        assertThat((String) JsonPath.read(inexistente, "$.detail")).isEqualTo(detalle);
    }

    @Test
    void losErroresNoExponenInformacionInterna() throws Exception {
        String cuerpo = pagar(tokenDe("ana"), "seg-sensibles-1", "{\"numeroCuenta\":")
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(cuerpo)
                .doesNotContain("Exception")
                .doesNotContain("at com.")
                .doesNotContain("token-de-pruebas")
                .doesNotContain("secreto");
    }

    @Test
    void elHealthCheckEsPublico() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void unaRutaInexistenteResponde404EnElMismoFormato() throws Exception {
        mvc.perform(get("/api/v1/no-existe").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDe("ana")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }
}
