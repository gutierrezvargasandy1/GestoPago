package com.utng.pagosservicios.proveedor.gestopago;

import com.utng.pagosservicios.proveedor.BitacoraIntercambios;
import com.utng.pagosservicios.proveedor.CatalogoProveedor;
import com.utng.pagosservicios.proveedor.ProveedorPagos;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.time.Clock;
import java.util.Locale;

@Configuration
public class GestopagoConfig {

    private static final Logger log = LoggerFactory.getLogger(GestopagoConfig.class);

    @Bean
    public RestClient gestopagoRestClient(GestopagoProperties propiedades) {
        return crearRestClient(propiedades);
    }

    /**
     * Timeouts explicitos: sin ellos una llamada colgada retiene el hilo para
     * siempre. HTTP/1.1 fijo porque el endpoint (.do) es un servidor clasico y
     * algunos no toleran el intento de negociar HTTP/2. El cliente HTTP del JDK
     * no reintenta POST por su cuenta, lo cual es indispensable aqui.
     */
    static RestClient crearRestClient(GestopagoProperties propiedades) {
        HttpClient cliente = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(propiedades.timeout().conexion())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(cliente);
        fabrica.setReadTimeout(propiedades.timeout().lectura());
        return RestClient.builder()
                .baseUrl(propiedades.urlBase())
                .requestFactory(fabrica)
                .build();
    }

    @Bean
    ConectorGestopago conectorGestopago(RestClient gestopagoRestClient, GestopagoProperties propiedades) {
        exigirRutasDistintas(propiedades.endpoints());
        return new ConectorGestopago(gestopagoRestClient, propiedades);
    }

    @Bean
    public CircuitBreaker circuitoGestopago(CircuitBreakerRegistry registro) {
        return registro.circuitBreaker("gestopago");
    }

    /** Sin llave e IV no hay forma de hablar con el proveedor: la aplicacion no arranca. */
    @Bean
    public CifradorAes cifradorGestopago(GestopagoProperties propiedades) {
        if (!propiedades.cifrado().configurado()) {
            throw new IllegalStateException("Falta el cifrado de Gestopago: defina GESTOPAGO_AES_KEY y "
                    + "GESTOPAGO_AES_IV con la llave y el IV (base64) que entrega PuntoRed");
        }
        return new CifradorAes(propiedades.cifrado().llave(), propiedades.cifrado().iv());
    }

    @Bean
    public CredencialGestopago credencialGestopago(GestopagoProperties propiedades,
                                                   ConectorGestopago conectorGestopago, JsonMapper json,
                                                   BitacoraIntercambios bitacora, Clock reloj) {
        if (propiedades.usaTokenEstatico()) {
            log.info("Gestopago: se usa el token configurado en GESTOPAGO_TOKEN (no se renueva solo)");
            return new TokenEstatico(propiedades.token());
        }
        if (propiedades.autenticacion().configurada()) {
            log.info("Gestopago: el token se obtendra y renovara con las credenciales de distribuidor");
            return new TokenRenovable(conectorGestopago, json, propiedades.endpoints().autenticacion(),
                    propiedades.autenticacion(), bitacora, reloj);
        }
        throw new IllegalStateException("Falta la credencial de Gestopago: defina GESTOPAGO_ID_DISTRIBUIDOR, "
                + "GESTOPAGO_CODIGO_DISPOSITIVO y GESTOPAGO_PASSWORD, o bien GESTOPAGO_TOKEN");
    }

    @Bean
    public ProveedorPagos proveedorPagos(ConectorGestopago conectorGestopago, JsonMapper json,
                                         GestopagoProperties propiedades, CircuitBreaker circuitoGestopago,
                                         CredencialGestopago credencialGestopago, CifradorAes cifradorGestopago,
                                         BitacoraIntercambios bitacora, Clock reloj) {
        return new GestopagoClient(conectorGestopago, json, propiedades, circuitoGestopago, credencialGestopago,
                cifradorGestopago, bitacora, reloj);
    }

    @Bean
    public CatalogoProveedor catalogoProveedor(ConectorGestopago conectorGestopago, GestopagoProperties propiedades,
                                               CredencialGestopago credencialGestopago,
                                               BitacoraIntercambios bitacora, Clock reloj) {
        return new GestopagoCatalogo(conectorGestopago, propiedades, credencialGestopago, bitacora, reloj);
    }

    /**
     * La conciliacion jamas debe poder reenviar un cargo: si por error de
     * configuracion la ruta de consulta apuntara a sendTx, cada "consulta" seria
     * un cobro nuevo. Se impide al arrancar.
     */
    static void exigirRutasDistintas(GestopagoProperties.Endpoints rutas) {
        if (rutas.tieneConsulta() && normalizar(rutas.consulta()).equals(normalizar(rutas.envio()))) {
            throw new IllegalStateException("gestopago.endpoints.consulta no puede ser la misma ruta que "
                    + "gestopago.endpoints.envio: la conciliacion reenviaria el cargo");
        }
    }

    private static String normalizar(String ruta) {
        String limpia = ruta.strip().toLowerCase(Locale.ROOT);
        while (limpia.endsWith("/")) {
            limpia = limpia.substring(0, limpia.length() - 1);
        }
        return limpia;
    }
}
