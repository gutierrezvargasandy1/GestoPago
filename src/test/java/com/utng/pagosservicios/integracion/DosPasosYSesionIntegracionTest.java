package com.utng.pagosservicios.integracion;

import com.jayway.jsonpath.JsonPath;
import com.utng.pagosservicios.seguridad.Totp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Doble factor validado en el servidor y una sola sesion activa por cliente:
 * entrar desde la otra plataforma exige aceptar cerrar la anterior, y el
 * token de la sesion cerrada deja de servir al instante.
 */
class DosPasosYSesionIntegracionTest extends IntegracionBase {

    private String secreto;

    @BeforeEach
    void cliente() throws Exception {
        clienteSinSesion();
        secreto = JsonPath.read(primerPaso(), "$.secreto");
    }

    private void clienteSinSesion() {
        long id = datos.crearCliente("ana");
        datos.crearCuenta(id, CUENTA, "1000.00");
    }

    private String primerPaso() throws Exception {
        return mvc.perform(post("/api/v1/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"ana\",\"password\":\"" + DatosPrueba.PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private ResultActions segundoPaso(String codigo, String plataforma, boolean forzar) throws Exception {
        String desafio = JsonPath.read(primerPaso(), "$.desafio");
        return mvc.perform(post("/api/v1/auth/2fa").contentType(MediaType.APPLICATION_JSON)
                .content("{\"desafio\":\"" + desafio + "\",\"codigo\":\"" + codigo + "\",\"plataforma\":\""
                        + plataforma + "\",\"forzar\":" + forzar + "}"));
    }

    /** Cada inicio usa un intervalo de 30 s distinto: un codigo no se acepta dos veces. */
    private String codigo(int intervalo) {
        return Totp.codigo(secreto, Instant.now().plus(Duration.ofSeconds(30L * intervalo)));
    }

    private String token(ResultActions resultado) throws Exception {
        return JsonPath.read(resultado.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8),
                "$.accessToken");
    }

    private ResultActions perfil(String token) throws Exception {
        return mvc.perform(get("/api/v1/perfil").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    @Test
    void elDesafioNoSirveComoTokenDeAcceso() throws Exception {
        String desafio = JsonPath.read(primerPaso(), "$.desafio");
        perfil(desafio).andExpect(status().isUnauthorized());
    }

    @Test
    void codigoIncorrecto_401() throws Exception {
        segundoPaso("000000".equals(codigo(0)) ? "111111" : "000000", "MOVIL", false)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CODIGO_2FA_INVALIDO"));
    }

    @Test
    void codigoCorrecto_abreSesionYDevuelveElPerfil() throws Exception {
        String token = token(segundoPaso(codigo(0), "MOVIL", false).andExpect(status().isOk()));

        perfil(token).andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario").value("ana"))
                .andExpect(jsonPath("$.dosPasosActivo").value(true))
                .andExpect(jsonPath("$.sesion.plataforma").value("MOVIL"))
                .andExpect(jsonPath("$.cuentas[0].numero").value(CUENTA));
    }

    @Test
    void conSesionEnElMovil_laWebDebeAceptarCerrarla() throws Exception {
        String movil = token(segundoPaso(codigo(0), "MOVIL", false).andExpect(status().isOk()));

        segundoPaso(codigo(1), "WEB", false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("SESION_ACTIVA"));
        perfil(movil).andExpect(status().isOk());

        String web = token(segundoPaso(codigo(1), "WEB", true).andExpect(status().isOk()));
        perfil(web).andExpect(status().isOk()).andExpect(jsonPath("$.sesion.plataforma").value("WEB"));
        perfil(movil).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("SESION_FINALIZADA"));
    }

    @Test
    void cerrarSesion_invalidaElToken() throws Exception {
        String token = token(segundoPaso(codigo(0), "WEB", false).andExpect(status().isOk()));

        mvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        perfil(token).andExpect(status().isUnauthorized());
        segundoPaso(codigo(1), "MOVIL", false).andExpect(status().isOk());
    }

    @Test
    void elHistorialSoloMuestraComprasConfirmadas() throws Exception {
        String token = token(segundoPaso(codigo(0), "WEB", false).andExpect(status().isOk()));
        proveedorAprueba();
        pagar(token, "historial-ok-001", solicitud(CUENTA, "TAE_TELCEL", "TEL_100", "5512345678", "100.00"))
                .andExpect(status().isCreated());
        proveedorRespondeCodigo("02", "Saldo insuficiente");
        pagar(token, "historial-rech-1", solicitud(CUENTA, "TAE_TELCEL", "TEL_100", "5512345678", "100.00"));

        mvc.perform(get("/api/v1/pagos/historial").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].producto").value("Telcel $100"))
                .andExpect(jsonPath("$[0].montoTotal").value("100.00"));
    }
}
