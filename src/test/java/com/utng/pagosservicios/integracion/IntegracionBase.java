package com.utng.pagosservicios.integracion;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.jayway.jsonpath.JsonPath;
import com.utng.pagosservicios.proveedor.gestopago.MensajeCifrado;
import com.utng.pagosservicios.proveedor.gestopago.XmlPuntoRed;
import com.utng.pagosservicios.seguridad.EmisorTokens;
import com.utng.pagosservicios.servicio.ConciliacionJob;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Base de las pruebas de integracion: aplicacion completa (seguridad, servicios,
 * JPA, Flyway) contra PostgreSQL real y con PuntoRed simulado por WireMock con
 * su contrato real (formulario cifrado y respuestas XML).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(RequiereBaseDeDatos.class)
public abstract class IntegracionBase {

    protected static final String RUTA_ENVIO = "/sistema/service/sendTx.do";
    protected static final String RUTA_CONSULTA = "/sistema/service/confirmTx.do";
    protected static final String RUTA_CATALOGO = "/sistema/service/getProductList.do";
    protected static final String CUENTA = "1000000001";
    protected static final WireMockServer GESTOPAGO = iniciarSimulador();

    private static WireMockServer iniciarSimulador() {
        WireMockServer servidor = new WireMockServer(options().dynamicPort());
        servidor.start();
        return servidor;
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        BaseDeDatosPrueba.registrar(registro);
        registro.add("gestopago.url-base", () -> "http://localhost:" + GESTOPAGO.port());
    }

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected EmisorTokens emisorTokens;
    @Autowired
    protected ConciliacionJob conciliacion;
    @Autowired
    protected CircuitBreakerRegistry circuitos;
    @Autowired
    private PasswordEncoder codificador;

    private static String hashPassword;
    protected DatosPrueba datos;

    @BeforeEach
    void reiniciarEstado() {
        GESTOPAGO.resetAll();
        circuitos.circuitBreaker("gestopago").reset();
        if (hashPassword == null) {
            hashPassword = DatosPrueba.hash(codificador);
        }
        datos = new DatosPrueba(jdbc, hashPassword);
        datos.limpiar();
    }

    protected String tokenDe(String usuario) {
        return emisorTokens.emitir(usuario, true).valor();
    }

    /** Crea un cliente con su cuenta y devuelve un token valido para el. */
    protected String clienteConCuenta(String usuario, String numeroCuenta, String saldo) {
        long id = datos.crearCliente(usuario);
        datos.crearCuenta(id, numeroCuenta, saldo);
        return tokenDe(usuario);
    }

    protected ResultActions pagar(String token, String clave, String cuerpo) throws Exception {
        MockHttpServletRequestBuilder solicitud = org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/v1/pagos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo);
        if (token != null) {
            solicitud.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        if (clave != null) {
            solicitud.header("Idempotency-Key", clave);
        }
        return mvc.perform(solicitud);
    }

    protected ResultActions consultarPago(String token, long id) throws Exception {
        return mvc.perform(get("/api/v1/pagos/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    protected static String solicitud(String cuenta, String servicio, String producto, String referencia, String monto) {
        return """
                {"numeroCuenta":"%s","idServicio":"%s","idProducto":"%s","referencia":"%s","monto":%s}
                """.formatted(cuenta, servicio, producto, referencia, monto);
    }

    protected static String telcel100() {
        return solicitud(CUENTA, "TAE_TELCEL", "TEL_100", "5512345678", "100.00");
    }

    protected static ResponseDefinitionBuilder xml(String cuerpo) {
        return aResponse().withStatus(200).withHeader("Content-Type", "text/xml;charset=UTF-8").withBody(cuerpo);
    }

    protected void proveedorAprueba() {
        proveedorResponde(xml(XmlPuntoRed.exito("F-100", "A-200")));
    }

    protected void proveedorRespondeCodigo(String codigo, String texto) {
        proveedorResponde(xml(XmlPuntoRed.codigo(codigo, texto)));
    }

    protected void proveedorResponde(ResponseDefinitionBuilder respuesta) {
        GESTOPAGO.stubFor(post(urlEqualTo(RUTA_ENVIO)).willReturn(respuesta));
    }

    protected void consultaResponde(ResponseDefinitionBuilder respuesta) {
        GESTOPAGO.stubFor(post(urlEqualTo(RUTA_CONSULTA)).willReturn(respuesta));
    }

    /** Lo que recibio el proveedor en una ruta, ya descifrado. */
    protected static List<MensajeCifrado> mensajesRecibidos(String ruta) {
        List<LoggedRequest> recibidas = GESTOPAGO.findAll(postRequestedFor(urlEqualTo(ruta)));
        return recibidas.stream().map(peticion -> MensajeCifrado.de(peticion.getBodyAsString())).toList();
    }

    protected static long idDe(MvcResult resultado) throws Exception {
        Number id = JsonPath.read(resultado.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.transaccionId");
        return id.longValue();
    }
}
