package com.utng.pagosservicios.integracion;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RegistroIntegracionTest extends IntegracionBase {

    private ResultActions registrar(String cuerpo) throws Exception {
        return mvc.perform(post("/api/v1/auth/registro").contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private static String cuerpo(String correo, String password) {
        return """
                {"correo":"%s","password":"%s","nombre":"Luis Perez","aceptaTerminos":true}""".formatted(correo, password);
    }

    @Test
    void registraAlClienteConSuCuentaYPuedeIniciarSesion() throws Exception {
        registrar(cuerpo("Luis.Perez@Correo.com", "Secreta123"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.usuario").value("luis.perez@correo.com"))
                .andExpect(jsonPath("$.numeroCuenta", matchesPattern("^[1-9][0-9]{9}$")))
                .andExpect(content().string(not(containsString("Secreta123"))))
                .andExpect(content().string(not(containsString("password"))));

        mvc.perform(post("/api/v1/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"LUIS.PEREZ@correo.com\",\"password\":\"Secreta123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desafio").isNotEmpty())
                .andExpect(jsonPath("$.requiereConfiguracion").value(true));
    }

    @Test
    void unCorreoRepetidoEs409() throws Exception {
        registrar(cuerpo("luis.perez@correo.com", "Secreta123")).andExpect(status().isCreated());

        registrar(cuerpo("LUIS.PEREZ@correo.com", "OtraClave456"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("USUARIO_YA_REGISTRADO"));
    }

    @Test
    void rechazaContrasenasDebilesYCorreosInvalidos() throws Exception {
        registrar(cuerpo("luis.perez@correo.com", "corta1")).andExpect(status().isBadRequest());
        registrar(cuerpo("luis.perez@correo.com", "soloLetrasSinNumero")).andExpect(status().isBadRequest());
        registrar(cuerpo("luis.perez", "Secreta123")).andExpect(status().isBadRequest());
        registrar(cuerpo("luis perez@correo", "Secreta123")).andExpect(status().isBadRequest());
    }

    @Test
    void sinAceptarLosTerminosNoSeCreaLaCuenta() throws Exception {
        registrar("""
                {"correo":"luis.perez@correo.com","password":"Secreta123","nombre":"Luis","aceptaTerminos":false}""")
                .andExpect(status().isBadRequest());
        registrar("""
                {"correo":"luis.perez@correo.com","password":"Secreta123","nombre":"Luis"}""")
                .andExpect(status().isBadRequest());
    }

    @Test
    void losTerminosSonPublicos() throws Exception {
        mvc.perform(get("/api/v1/terminos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("1.0"))
                .andExpect(jsonPath("$.secciones[0].titulo").isNotEmpty());
    }

    /** El cliente no puede darse saldo ni permisos: un campo que no se espera es 400. */
    @Test
    void noAceptaCamposQueDecideElServidor() throws Exception {
        registrar("""
                {"correo":"luis.perez@correo.com","password":"Secreta123","nombre":"Luis","aceptaTerminos":true,"saldo":1000000}""")
                .andExpect(status().isBadRequest());
    }
}
