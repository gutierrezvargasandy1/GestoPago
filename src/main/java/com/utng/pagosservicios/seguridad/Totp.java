package com.utng.pagosservicios.seguridad;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.OptionalLong;

/**
 * Codigos temporales de un solo uso (TOTP, RFC 6238): HMAC-SHA1, 6 digitos,
 * intervalos de 30 s. Son los que generan Google Authenticator, Microsoft
 * Authenticator o Authy, sin depender de correo ni SMS. El codigo se valida
 * siempre en el servidor.
 */
public final class Totp {

    private static final int DIGITOS = 6;
    private static final long PERIODO_SEGUNDOS = 30;
    /** Se acepta el intervalo anterior y el siguiente: tolera relojes desfasados hasta 30 s. */
    private static final int VENTANA = 1;
    private static final String ALFABETO = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private Totp() {
    }

    /** 160 bits aleatorios en Base32, el tamano que recomienda el RFC 4226. */
    public static String generarSecreto() {
        byte[] bytes = new byte[20];
        ALEATORIO.nextBytes(bytes);
        return base32(bytes);
    }

    public static String uri(String emisor, String cuenta, String secreto) {
        String etiqueta = URLEncoder.encode(emisor + ":" + cuenta, StandardCharsets.UTF_8).replace("+", "%20");
        return "otpauth://totp/" + etiqueta + "?secret=" + secreto
                + "&issuer=" + URLEncoder.encode(emisor, StandardCharsets.UTF_8).replace("+", "%20")
                + "&algorithm=SHA1&digits=" + DIGITOS + "&period=" + PERIODO_SEGUNDOS;
    }

    /**
     * Devuelve el intervalo del codigo si es valido y posterior al ultimo usado;
     * vacio si no. Un codigo ya aceptado no se puede reutilizar (repeticion).
     */
    public static OptionalLong verificar(String secreto, String codigo, Instant ahora, Long ultimoPasoUsado) {
        if (secreto == null || codigo == null || !codigo.matches("^[0-9]{6}$")) {
            return OptionalLong.empty();
        }
        long actual = ahora.getEpochSecond() / PERIODO_SEGUNDOS;
        byte[] esperado = codigo.getBytes(StandardCharsets.US_ASCII);
        for (long paso = actual - VENTANA; paso <= actual + VENTANA; paso++) {
            if (ultimoPasoUsado != null && paso <= ultimoPasoUsado) {
                continue;
            }
            byte[] candidato = codigoDePaso(secreto, paso).getBytes(StandardCharsets.US_ASCII);
            // Comparacion en tiempo constante: no revela cuantos digitos coinciden.
            if (MessageDigest.isEqual(candidato, esperado)) {
                return OptionalLong.of(paso);
            }
        }
        return OptionalLong.empty();
    }

    /** Codigo vigente en un instante; lo usan las pruebas automatizadas. */
    public static String codigo(String secreto, Instant instante) {
        return codigoDePaso(secreto, instante.getEpochSecond() / PERIODO_SEGUNDOS);
    }

    private static String codigoDePaso(String secreto, long paso) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(decodificarBase32(secreto), "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(paso).array());
            int desplazamiento = hash[hash.length - 1] & 0x0f;
            int binario = ((hash[desplazamiento] & 0x7f) << 24)
                    | ((hash[desplazamiento + 1] & 0xff) << 16)
                    | ((hash[desplazamiento + 2] & 0xff) << 8)
                    | (hash[desplazamiento + 3] & 0xff);
            return String.format("%06d", binario % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA1 no disponible", e);
        }
    }

    static String base32(byte[] datos) {
        StringBuilder salida = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : datos) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                salida.append(ALFABETO.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) {
            salida.append(ALFABETO.charAt((buffer << (5 - bits)) & 31));
        }
        return salida.toString();
    }

    static byte[] decodificarBase32(String texto) {
        String limpio = texto.replace("=", "").replace(" ", "").toUpperCase();
        ByteBuffer salida = ByteBuffer.allocate(limpio.length() * 5 / 8);
        int buffer = 0;
        int bits = 0;
        for (char c : limpio.toCharArray()) {
            int valor = ALFABETO.indexOf(c);
            if (valor < 0) {
                throw new IllegalArgumentException("Secreto TOTP con caracteres fuera de Base32");
            }
            buffer = (buffer << 5) | valor;
            bits += 5;
            if (bits >= 8) {
                salida.put((byte) ((buffer >> (bits - 8)) & 0xff));
                bits -= 8;
            }
        }
        return salida.array();
    }
}
