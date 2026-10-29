package com.utng.pagosservicios.integracion;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RegistroIntegracionTest extends IntegracionBase {

    private ResultActions registrar(String cuerpo) throws Exception {
        return mvc.perform(post("/api/v1/auth/registro").contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private static String cuerpo(String usuario, String password) {
        return """
                {"usuario":"%s","password":"%s","nombre":"Luis Perez"}""".formatted(usuario, password);
    }

    @Test
    void registraAlClienteConSuCuentaYPuedeIniciarSesion() throws Exception {
        registrar(cuerpo("luis.perez", "Secreta123"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.usuario").value("luis.perez"))
                .andExpect(jsonPath("$.numeroCuenta", matchesPattern("^[1-9][0-9]{9}$")))
                .andExpect(content().string(not(containsString("Secreta123"))))
                .andExpect(content().string(not(containsString("password"))));

        mvc.perform(post("/api/v1/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"luis.perez\",\"password\":\"Secreta123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void unUsuarioRepetidoEs409() throws Exception {
        registrar(cuerpo("luis.perez", "Secreta123")).andExpect(status().isCreated());

        registrar(cuerpo("luis.perez", "OtraClave456"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("USUARIO_YA_REGISTRADO"));
    }

    @Test
    void rechazaContrasenasDebilesYUsuariosInvalidos() throws Exception {
        registrar(cuerpo("luis.perez", "corta1")).andExpect(status().isBadRequest());
        registrar(cuerpo("luis.perez", "soloLetrasSinNumero")).andExpect(status().isBadRequest());
        registrar(cuerpo("Luis Perez", "Secreta123")).andExpect(status().isBadRequest());
    }

    /** El cliente no puede darse saldo ni permisos: un campo que no se espera es 400. */
    @Test
    void noAceptaCamposQueDecideElServidor() throws Exception {
        registrar("""
                {"usuario":"luis.perez","password":"Secreta123","nombre":"Luis","saldo":1000000}""")
                .andExpect(status().isBadRequest());
    }
}