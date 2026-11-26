package com.utng.pagosservicios.integracion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Edicion del perfil y busqueda de compras en cualquier estado. */
class PerfilYComprasIntegracionTest extends IntegracionBase {

    private static final String CORREO = "ana@correo.com";

    private String token;

    @BeforeEach
    void cliente() {
        token = clienteConCuenta(CORREO, CUENTA, "1000.00");
    }

    private ResultActions put(String ruta, String cuerpo) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(ruta)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private ResultActions compras(String parametros) throws Exception {
        return mvc.perform(get("/api/v1/pagos" + parametros).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    @Test
    void editaElNombreYLoDevuelveEnElPerfil() throws Exception {
        put("/api/v1/perfil", "{\"nombre\":\"  Ana Lopez Ruiz  \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Ana Lopez Ruiz"))
                .andExpect(jsonPath("$.correo").value(CORREO))
                .andExpect(jsonPath("$.modoDemostracion").value(false));
    }

    @Test
    void cambiarLaContrasenaExigeLaActual() throws Exception {
        put("/api/v1/perfil/contrasena", "{\"actual\":\"NoEsLaMia1\",\"nueva\":\"NuevaClave2026\"}")
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.codigo").value("CONTRASENA_INCORRECTA"));

        put("/api/v1/perfil/contrasena",
                "{\"actual\":\"" + DatosPrueba.PASSWORD + "\",\"nueva\":\"NuevaClave2026\"}")
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"" + CORREO + "\",\"password\":\"NuevaClave2026\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void cambiarElCorreoCierraLaSesionYSeEntraConElNuevo() throws Exception {
        put("/api/v1/perfil/correo", "{\"correo\":\"Ana.Nueva@Correo.com\",\"password\":\"" + DatosPrueba.PASSWORD + "\"}")
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/perfil").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"ana.nueva@correo.com\",\"password\":\"" + DatosPrueba.PASSWORD + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void noSePuedeUsarElCorreoDeOtroCliente() throws Exception {
        datos.crearCliente("beto@correo.com");

        put("/api/v1/perfil/correo", "{\"correo\":\"beto@correo.com\",\"password\":\"" + DatosPrueba.PASSWORD + "\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("USUARIO_YA_REGISTRADO"));
    }

    @Test
    void laBusquedaMuestraTodosLosEstadosYFiltra() throws Exception {
        proveedorAprueba();
        pagar(token, "busqueda-exito-1", telcel100()).andExpect(status().isCreated());
        proveedorRespondeCodigo("02", "Saldo insuficiente");
        pagar(token, "busqueda-rechazo", telcel100()).andExpect(status().is(422));
        // El proveedor no procesa la operacion: error de comunicacion, el dinero se devuelve.
        proveedorResponde(aResponse().withStatus(403).withBody("{\"token\":\"EXPIRED\"}"));
        pagar(token, "busqueda-sin-comunicacion", telcel100()).andExpect(status().isServiceUnavailable());

        compras("").andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(3)));
        compras("?estado=EXITOSAS")
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].grupo").value("EXITOSA"))
                .andExpect(jsonPath("$[0].folioProveedor").isNotEmpty());
        compras("?estado=RECHAZADAS")
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].estado").value("REVERSADA"))
                .andExpect(jsonPath("$[0].grupo").value("RECHAZADA"))
                .andExpect(jsonPath("$[0].motivo").isNotEmpty());
        compras("?estado=ERRORES_COMUNICACION")
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].estado").value("REVERSADA"))
                .andExpect(jsonPath("$[0].grupo").value("ERROR_COMUNICACION"))
                .andExpect(jsonPath("$[0].folioProveedor").doesNotExist());
        compras("?texto=telcel").andExpect(jsonPath("$", hasSize(3)));
        compras("?texto=no-existe").andExpect(jsonPath("$", hasSize(0)));
        // El historial sigue mostrando solo las exitosas.
        mvc.perform(get("/api/v1/pagos/historial").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void fechasInvertidasOEstadoDesconocidoSon400() throws Exception {
        compras("?desde=2026-11-30&hasta=2026-11-01").andExpect(status().isBadRequest());
        compras("?estado=CUALQUIERA").andExpect(status().isBadRequest());
    }
}
