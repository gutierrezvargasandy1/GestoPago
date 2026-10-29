import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simulador local de PuntoRed (GestoPago) para demostraciones. Implementa el
 * contrato de la documentacion: autenticacion con credenciales de distribuidor,
 * token Bearer de 24 h, X-API-Key, formulario random/signed con el JSON cifrado
 * en AES-256-CBC, respuestas XML, confirmTx y getProductList.
 *
 * Solo usa el JDK: se ejecuta con  java SimuladorPuntoRed.java  (docker compose
 * lo levanta asi). Los valores por defecto son de DEMOSTRACION y coinciden con
 * application-simulador.yml; no sirven contra el proveedor real.
 *
 * Decide por los ultimos 4 digitos del telefono o de la referencia:
 *   0000 -> 02 saldo insuficiente (no se aplica)
 *   0006 -> 06 duplicada (no se aplica)
 *   8282 -> 82 sin respuesta del carrier (no se aplica; confirmTx dira "no aplicada")
 *   9999 -> responde 01 despues de 8 s (la API corta a los 5 s; confirmTx dira "aplicada")
 *   5000 -> HTTP 500 (no se aplica; confirmTx dira "no aplicada")
 *   7777 -> 01 con PIN y leyenda (tarjeta de regalo)
 *   otra -> 01 aplicada
 */
public class SimuladorPuntoRed {

    private static final String ID_DISTRIBUIDOR = env("SIMULADOR_ID_DISTRIBUIDOR", "DEMO");
    private static final String CODIGO_DISPOSITIVO = env("SIMULADOR_CODIGO_DISPOSITIVO", "DEMO-01");
    private static final String PASSWORD = env("SIMULADOR_PASSWORD", "demo-simulador");
    private static final String API_KEY = env("SIMULADOR_API_KEY", "clave-api-simulador");
    private static final String LLAVE = env("SIMULADOR_AES_KEY", "vETiw7gnv7yO8Nh/R7HkzV7RKas/2mWixcXstqFFcrU=");
    private static final String IV = env("SIMULADOR_AES_IV", "iw7ERHzR2c2YAPIhy6zs7g==");
    private static final int PUERTO = Integer.parseInt(env("SIMULADOR_PUERTO", "8089"));
    private static final String NO_APLICADA = "04";

    private static final Pattern CAMPO_JSON =
            Pattern.compile("\"([A-Za-z]+)\"\\s*:\\s*(\"((?:[^\"\\\\]|\\\\.)*)\"|-?[0-9]+(?:\\.[0-9]+)?)");
    private static final SecureRandom ALEATORIO = new SecureRandom();
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    /** Tokens emitidos y su vencimiento. */
    private static final Map<String, Instant> TOKENS = new ConcurrentHashMap<>();
    /** Operaciones recibidas por UPC: lo que confirmTx consulta. */
    private static final Map<String, Operacion> OPERACIONES = new ConcurrentHashMap<>();
    private static final Map<String, Instant> RANDOMS = new ConcurrentHashMap<>();

    private record Operacion(boolean aplicada, String idTx, String autorizacion, String monto, String referencia) {
    }

    public static void main(String[] args) throws IOException {
        HttpServer servidor = HttpServer.create(new InetSocketAddress(PUERTO), 0);
        servidor.createContext("/sistema/app/jwt-gp/authenticate/", SimuladorPuntoRed::autenticar);
        servidor.createContext("/sistema/service/sendTx.do", intercambio -> transaccion(intercambio, false));
        servidor.createContext("/sistema/service/confirmTx.do", intercambio -> transaccion(intercambio, true));
        servidor.createContext("/sistema/service/getProductList.do", SimuladorPuntoRed::catalogo);
        servidor.createContext("/sistema/service/sendEcho.do", intercambio -> responder(intercambio, 200,
                "text/xml", xml("01", "Echo OK", Map.of())));
        servidor.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        servidor.start();
        System.out.println("Simulador de PuntoRed escuchando en el puerto " + PUERTO);
    }

    private static void autenticar(HttpExchange intercambio) throws IOException {
        if (!"POST".equals(intercambio.getRequestMethod())) {
            responder(intercambio, 405, "text/plain", "Metodo no permitido");
            return;
        }
        if (!apiKeyValida(intercambio)) {
            responder(intercambio, 401, "application/json", "{\"success\":false,\"mensaje\":\"X-API-Key invalida\"}");
            return;
        }
        Map<String, String> datos = new HashMap<>(formulario(intercambio.getRequestURI().getRawQuery()));
        datos.putAll(formulario(cuerpo(intercambio)));
        boolean validas = ID_DISTRIBUIDOR.equals(datos.get("idDistribuidor"))
                && CODIGO_DISPOSITIVO.equals(datos.get("codigoDispositivo"))
                && PASSWORD.equals(datos.get("password"));
        log("AUTENTICACION", validas ? "credenciales validas" : "credenciales rechazadas");
        if (!validas) {
            responder(intercambio, 401, "application/json", "{\"success\":false}");
            return;
        }
        Instant vence = Instant.now().plusSeconds(24 * 3600);
        String token = jwt(vence);
        TOKENS.put(token, vence);
        responder(intercambio, 200, "application/json", "{\"token\":\"" + token + "\",\"success\":true}");
    }

    private static void transaccion(HttpExchange intercambio, boolean consulta) throws IOException {
        String operacion = consulta ? "CONFIRMTX" : "SENDTX";
        if (!"POST".equals(intercambio.getRequestMethod())) {
            responder(intercambio, 405, "text/plain", "Metodo no permitido");
            return;
        }
        if (!tokenValido(intercambio) || !apiKeyValida(intercambio)) {
            log(operacion, "token vencido o invalido -> 403");
            responder(intercambio, 403, "application/json", "{\"token\":\"EXPIRED\"}");
            return;
        }
        Map<String, String> campos = formulario(cuerpo(intercambio));
        String random = campos.get("random");
        if (random == null || random.isBlank() || RANDOMS.putIfAbsent(random, Instant.now()) != null) {
            responder(intercambio, 200, "text/xml", xml("10", "Campo random ausente o repetido", Map.of()));
            return;
        }
        Map<String, String> datos;
        try {
            datos = json(descifrar(campos.get("signed")));
        } catch (RuntimeException e) {
            log(operacion, "no se pudo descifrar signed -> 10");
            responder(intercambio, 200, "text/xml", xml("10", "No se pudo descifrar la solicitud", Map.of()));
            return;
        }
        Optional<String> error = validar(datos);
        if (error.isPresent()) {
            log(operacion, "datos invalidos: " + error.get());
            responder(intercambio, 200, "text/xml", xml("10", error.get(), Map.of()));
            return;
        }
        String upc = datos.get("upc");
        String referencia = datos.containsKey("telefono") ? datos.get("telefono") : datos.get("referencia");
        log(operacion, "upc=" + upc + " referencia=****" + ultimos(referencia, 4) + " producto=" + datos.get("idProducto"));

        if (consulta) {
            Operacion previa = OPERACIONES.get(upc);
            if (previa != null && previa.aplicada()) {
                responder(intercambio, 200, "text/xml", xml("01", "Transaccion aplicada", exito(previa)));
            } else {
                responder(intercambio, 200, "text/xml", xml(NO_APLICADA, "Transaccion no aplicada", Map.of()));
            }
            return;
        }

        Operacion previa = OPERACIONES.get(upc);
        if (previa != null) {
            responder(intercambio, 200, "text/xml", xml("06", "Transaccion duplicada, intente en 15 minutos", Map.of()));
            return;
        }
        String terminacion = ultimos(referencia, 4);
        String monto = datos.getOrDefault("montoPago", "0");
        switch (terminacion) {
            case "0000" -> {
                OPERACIONES.put(upc, new Operacion(false, null, null, monto, referencia));
                responder(intercambio, 200, "text/xml", xml("02", "Saldo insuficiente", Map.of("NUM_AUTORIZACION", "-1")));
            }
            case "0006" -> responder(intercambio, 200, "text/xml",
                    xml("06", "Transaccion duplicada, intente en 15 minutos", Map.of("NUM_AUTORIZACION", "-1")));
            case "8282" -> {
                OPERACIONES.put(upc, new Operacion(false, null, null, monto, referencia));
                responder(intercambio, 200, "text/xml", xml("82", "Sin respuesta del carrier", Map.of("NUM_AUTORIZACION", "-1")));
            }
            case "5000" -> {
                OPERACIONES.put(upc, new Operacion(false, null, null, monto, referencia));
                responder(intercambio, 500, "text/plain", "Internal Server Error");
            }
            case "9999" -> {
                Operacion aplicada = aplicar(upc, monto, referencia);
                dormir(8_000);
                responder(intercambio, 200, "text/xml", xml("01", "Operacion realizada con exito", exito(aplicada)));
            }
            default -> {
                Operacion aplicada = aplicar(upc, monto, referencia);
                Map<String, String> extra = new LinkedHashMap<>(exito(aplicada));
                if ("7777".equals(terminacion)) {
                    extra.put("PIN", digitos(4) + "-" + digitos(4) + "-" + digitos(4));
                    extra.put("legend", "Ingresa el PIN en la tienda en linea para canjear tu saldo.");
                }
                responder(intercambio, 200, "text/xml", xml("01", "Operacion realizada con exito", extra));
            }
        }
    }

    private static void catalogo(HttpExchange intercambio) throws IOException {
        if (!tokenValido(intercambio) || !apiKeyValida(intercambio)) {
            responder(intercambio, 403, "application/json", "{\"token\":\"EXPIRED\"}");
            return;
        }
        log("CATALOGO", "se entrega el catalogo de ejemplo");
        String productos = String.join("",
                producto(1001, "Telcel", 2001, "Telcel $20", "20.00", 1),
                producto(1001, "Telcel", 2002, "Telcel $50", "50.00", 1),
                producto(1001, "Telcel", 2003, "Telcel $100", "100.00", 1),
                producto(1001, "Telcel", 2004, "Telcel $200", "200.00", 1),
                producto(1002, "Movistar", 2101, "Movistar $30", "30.00", 1),
                producto(1002, "Movistar", 2102, "Movistar $100", "100.00", 1),
                producto(1003, "CFE", 3001, "Pago de recibo CFE", "0", 2),
                producto(1004, "Sky", 4001, "Recarga Sky", "0", 2));
        responder(intercambio, 200, "text/xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><RESPONSE>"
                + "<MENSAJE><CODIGO>01</CODIGO><TEXTO>Operacion realizada con exito</TEXTO></MENSAJE>"
                + "<PRODUCTOS>" + productos + "</PRODUCTOS></RESPONSE>");
    }

    private static String producto(int idServicio, String servicio, int idProducto, String producto, String precio,
                                   int tipoFront) {
        return "<producto servicio=\"" + servicio + "\" producto=\"" + producto + "\" idServicio=\"" + idServicio
                + "\" idProducto=\"" + idProducto + "\" idCatTipoServicio=\"" + tipoFront + "\" tipoFront=\""
                + tipoFront + "\" hasDigitoVerificador=\"false\" precio=\"" + precio
                + "\" showAyuda=\"0\" tipoReferencia=\"a\"><legend>" + producto + "</legend></producto>";
    }

    private static Optional<String> validar(Map<String, String> datos) {
        String upc = datos.get("upc");
        if (upc == null || upc.length() < 3 || upc.length() > 15) {
            return Optional.of("upc debe tener de 3 a 15 caracteres");
        }
        if (!datos.getOrDefault("horaLocal", "").matches("^[0-9]{14}$")) {
            return Optional.of("horaLocal debe tener formato yyyyMMddHHmmss");
        }
        if (!datos.getOrDefault("idProducto", "").matches("^[0-9]+$")
                || !datos.getOrDefault("idServicio", "").matches("^[0-9]+$")) {
            return Optional.of("idProducto e idServicio deben ser numericos");
        }
        String unidad = datos.getOrDefault("unidad", "");
        if (unidad.length() < 3 || unidad.length() > 25) {
            return Optional.of("unidad debe tener de 3 a 25 caracteres");
        }
        boolean recarga = datos.containsKey("telefono");
        if (recarga && !datos.get("telefono").matches("^[0-9]{10,20}$")) {
            return Optional.of("telefono debe tener de 10 a 20 digitos");
        }
        if (!recarga && (!datos.containsKey("referencia") || !datos.containsKey("montoPago"))) {
            return Optional.of("un pago de servicio requiere referencia y montoPago");
        }
        return Optional.empty();
    }

    private static Operacion aplicar(String upc, String monto, String referencia) {
        Operacion operacion = new Operacion(true, digitos(9), "AUT" + digitos(6), monto, referencia);
        OPERACIONES.put(upc, operacion);
        return operacion;
    }

    private static Map<String, String> exito(Operacion operacion) {
        Map<String, String> datos = new LinkedHashMap<>();
        datos.put("ID_TX", operacion.idTx());
        datos.put("NUM_AUTORIZACION", operacion.autorizacion());
        datos.put("SALDO", "9500.00");
        datos.put("COMISION", "0.00");
        datos.put("FECHA", FECHA.format(LocalDateTime.now()));
        datos.put("MONTO", operacion.monto());
        datos.put("REFERENCIA", operacion.referencia());
        return datos;
    }

    private static String xml(String codigo, String texto, Map<String, String> datos) {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><RESPONSE>");
        datos.forEach((nombre, valor) -> xml.append('<').append(nombre).append('>').append(escapar(valor))
                .append("</").append(nombre).append('>'));
        return xml.append("<MENSAJE><CODIGO>").append(codigo).append("</CODIGO><TEXTO>").append(escapar(texto))
                .append("</TEXTO></MENSAJE></RESPONSE>").toString();
    }

    private static boolean tokenValido(HttpExchange intercambio) {
        String autorizacion = intercambio.getRequestHeaders().getFirst("Authorization");
        if (autorizacion == null || !autorizacion.startsWith("Bearer ")) {
            return false;
        }
        Instant vence = TOKENS.get(autorizacion.substring(7).strip());
        return vence != null && Instant.now().isBefore(vence);
    }

    private static boolean apiKeyValida(HttpExchange intercambio) {
        return API_KEY.isBlank() || API_KEY.equals(intercambio.getRequestHeaders().getFirst("X-API-Key"));
    }

    private static String descifrar(String base64) {
        try {
            Cipher cifrador = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cifrador.init(Cipher.DECRYPT_MODE, new SecretKeySpec(Base64.getDecoder().decode(LLAVE), "AES"),
                    new IvParameterSpec(Base64.getDecoder().decode(IV)));
            return new String(cifrador.doFinal(Base64.getDecoder().decode(base64)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalArgumentException("signed invalido", e);
        }
    }

    /** JSON plano (el de sendTx no tiene objetos anidados): suficiente para el simulador. */
    private static Map<String, String> json(String texto) {
        Map<String, String> campos = new LinkedHashMap<>();
        Matcher campo = CAMPO_JSON.matcher(texto);
        while (campo.find()) {
            campos.put(campo.group(1), campo.group(3) != null ? campo.group(3) : campo.group(2));
        }
        if (campos.isEmpty()) {
            throw new IllegalArgumentException("JSON vacio");
        }
        return campos;
    }

    private static Map<String, String> formulario(String texto) {
        Map<String, String> campos = new LinkedHashMap<>();
        if (texto == null || texto.isBlank()) {
            return campos;
        }
        for (String par : texto.split("&")) {
            int igual = par.indexOf('=');
            if (igual > 0) {
                campos.put(URLDecoder.decode(par.substring(0, igual), StandardCharsets.UTF_8),
                        URLDecoder.decode(par.substring(igual + 1), StandardCharsets.UTF_8));
            }
        }
        return campos;
    }

    private static String cuerpo(HttpExchange intercambio) throws IOException {
        return new String(intercambio.getRequestBody().readNBytes(64 * 1024), StandardCharsets.UTF_8);
    }

    private static void responder(HttpExchange intercambio, int status, String tipo, String cuerpo) throws IOException {
        byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().set("Content-Type", tipo + ";charset=UTF-8");
        intercambio.sendResponseHeaders(status, bytes.length);
        try (OutputStream salida = intercambio.getResponseBody()) {
            salida.write(bytes);
        } catch (IOException e) {
            // El cliente corto la conexion (p. ej. por su timeout de lectura): es parte de la demostracion.
            log("RED", "el cliente cerro la conexion antes de recibir la respuesta");
        }
    }

    private static String jwt(Instant vence) {
        Base64.Encoder base64 = Base64.getUrlEncoder().withoutPadding();
        String cabecera = base64.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String carga = base64.encodeToString(("{\"sub\":\"" + ID_DISTRIBUIDOR + "\",\"exp\":" + vence.getEpochSecond()
                + "}").getBytes(StandardCharsets.UTF_8));
        byte[] firma = new byte[32];
        ALEATORIO.nextBytes(firma);
        return cabecera + "." + carga + "." + base64.encodeToString(firma);
    }

    private static String digitos(int cantidad) {
        StringBuilder texto = new StringBuilder();
        for (int i = 0; i < cantidad; i++) {
            texto.append(ALEATORIO.nextInt(10));
        }
        return texto.toString();
    }

    private static String ultimos(String valor, int cantidad) {
        if (valor == null) {
            return "";
        }
        return valor.length() <= cantidad ? valor : valor.substring(valor.length() - cantidad);
    }

    private static String escapar(String valor) {
        return valor == null ? "" : valor.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static void dormir(long milisegundos) {
        try {
            Thread.sleep(milisegundos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void log(String operacion, String detalle) {
        System.out.println(Instant.now() + " [" + operacion + "] " + detalle);
    }

    private static String env(String nombre, String porDefecto) {
        String valor = System.getenv(nombre);
        return valor == null || valor.isBlank() ? porDefecto : valor;
    }
}
