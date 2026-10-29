package com.utng.pagosservicios.proveedor.gestopago;

import com.jayway.jsonpath.JsonPath;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Abre lo que la aplicacion envio a PuntoRed: decodifica el formulario y
 * descifra el campo "signed". Asi las pruebas verifican el contenido real de
 * la operacion, que en la red viaja cifrado.
 */
public final class MensajeCifrado {

    /** Llave e IV de pruebas (los mismos de application-test.yml). No son secretos. */
    public static final String LLAVE_PRUEBAS = "F+ZRjJ3B8HMoG8EjpbBOG3smlOGFa8povHGWXQKrMqs=";
    public static final String IV_PRUEBAS = "vHvwOdGeLqxpK9u3tyShrA==";

    private final Map<String, String> campos;
    private final String json;

    private MensajeCifrado(Map<String, String> campos, String json) {
        this.campos = campos;
        this.json = json;
    }

    public static MensajeCifrado de(String cuerpoFormulario) {
        return de(cuerpoFormulario, new CifradorAes(LLAVE_PRUEBAS, IV_PRUEBAS));
    }

    public static MensajeCifrado de(String cuerpoFormulario, CifradorAes cifrador) {
        Map<String, String> campos = formulario(cuerpoFormulario);
        return new MensajeCifrado(campos, cifrador.descifrar(campos.get("signed")));
    }

    public static Map<String, String> formulario(String cuerpo) {
        Map<String, String> campos = new LinkedHashMap<>();
        for (String par : cuerpo.split("&")) {
            int igual = par.indexOf('=');
            campos.put(URLDecoder.decode(par.substring(0, igual), StandardCharsets.UTF_8),
                    URLDecoder.decode(par.substring(igual + 1), StandardCharsets.UTF_8));
        }
        return campos;
    }

    public String campo(String nombre) {
        return campos.get(nombre);
    }

    /** JSON descifrado, tal como lo recibe el proveedor. */
    public String json() {
        return json;
    }

    public <T> T dato(String rutaJson) {
        return JsonPath.read(json, rutaJson);
    }

    public boolean tiene(String nombre) {
        Map<String, Object> todo = JsonPath.read(json, "$");
        return todo.containsKey(nombre);
    }
}
