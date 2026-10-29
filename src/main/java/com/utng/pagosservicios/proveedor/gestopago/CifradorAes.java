package com.utng.pagosservicios.proveedor.gestopago;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

/**
 * Cifrado que exige PuntoRed para el campo "signed": AES-256 en modo CBC con
 * relleno PKCS5, llave e IV fijos entregados por el proveedor (base64) y
 * resultado en base64.
 *
 * <p>Notas de seguridad:
 * <ul>
 *   <li>La llave y el IV son secretos: llegan por variables de entorno y nunca
 *       se imprimen (ni siquiera su longitud en un mensaje de error).</li>
 *   <li>Un IV fijo hace que dos mensajes iguales produzcan el mismo cifrado. Es
 *       decision del proveedor y se documenta como riesgo residual; aqui cada
 *       mensaje lleva UPC y hora distintos, asi que nunca se repite.</li>
 *   <li>{@link Cipher} no es seguro entre hilos: se crea uno por operacion.</li>
 * </ul>
 */
public final class CifradorAes {

    private static final String TRANSFORMACION = "AES/CBC/PKCS5Padding";
    private static final int BYTES_LLAVE = 32;
    private static final int BYTES_IV = 16;

    private final SecretKeySpec llave;
    private final IvParameterSpec iv;

    public CifradorAes(String llaveBase64, String ivBase64) {
        byte[] bytesLlave = decodificar(llaveBase64, "la llave AES (GESTOPAGO_AES_KEY)");
        byte[] bytesIv = decodificar(ivBase64, "el IV (GESTOPAGO_AES_IV)");
        if (bytesLlave.length != BYTES_LLAVE) {
            throw new IllegalArgumentException("La llave AES (GESTOPAGO_AES_KEY) debe ser base64 de 32 bytes (AES-256)");
        }
        if (bytesIv.length != BYTES_IV) {
            throw new IllegalArgumentException("El IV (GESTOPAGO_AES_IV) debe ser base64 de 16 bytes");
        }
        this.llave = new SecretKeySpec(bytesLlave, "AES");
        this.iv = new IvParameterSpec(bytesIv);
    }

    public String cifrar(String texto) {
        try {
            Cipher cifrador = Cipher.getInstance(TRANSFORMACION);
            cifrador.init(Cipher.ENCRYPT_MODE, llave, iv);
            return Base64.getEncoder().encodeToString(cifrador.doFinal(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            // Sin el mensaje original: algunos proveedores de criptografia incluyen detalles de la llave.
            throw new IllegalStateException("No se pudo cifrar la solicitud para el proveedor ("
                    + e.getClass().getSimpleName() + ")");
        }
    }

    public String descifrar(String base64) {
        try {
            Cipher cifrador = Cipher.getInstance(TRANSFORMACION);
            cifrador.init(Cipher.DECRYPT_MODE, llave, iv);
            return new String(cifrador.doFinal(Base64.getDecoder().decode(base64)), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("No se pudo descifrar el contenido (" + e.getClass().getSimpleName() + ")");
        }
    }

    private static byte[] decodificar(String valor, String nombre) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Falta " + nombre + " que entrega PuntoRed");
        }
        try {
            return Base64.getDecoder().decode(valor.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(nombre.substring(0, 1).toUpperCase() + nombre.substring(1)
                    + " no es base64 valido");
        }
    }

    @Override
    public String toString() {
        return "CifradorAes[" + TRANSFORMACION + ", llave=****, iv=****]";
    }
}
