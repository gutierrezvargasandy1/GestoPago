package com.utng.pagosservicios.dominio.pago;

import java.security.SecureRandom;

/**
 * Genera el UPC (Unique Payment Code): el identificador con el que PuntoRed
 * reconoce cada operacion, detecta duplicados y responde confirmTx.
 *
 * <p>Decisiones:
 * <ul>
 *   <li>15 caracteres, el maximo que acepta el proveedor (3 a 15): a mayor
 *       longitud, menor probabilidad de choque.</li>
 *   <li>Solo digitos, con el primero distinto de cero: es el formato que acepta
 *       cualquier validacion del proveedor y no pierde ceros si alguien lo trata
 *       como numero.</li>
 *   <li>Aleatorio y no secuencial: varios ambientes (la base local, Supabase,
 *       otros equipos) usan las mismas credenciales de prueba. Con un contador,
 *       todos empezarian en 1 y el proveedor los rechazaria como duplicados.</li>
 * </ul>
 * La unicidad dentro de la base la garantiza el UNIQUE de referencia_externa.
 */
public final class GeneradorUpc {

    public static final int LONGITUD = 15;

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private GeneradorUpc() {
    }

    public static String generar() {
        char[] digitos = new char[LONGITUD];
        digitos[0] = (char) ('1' + ALEATORIO.nextInt(9));
        for (int i = 1; i < LONGITUD; i++) {
            digitos[i] = (char) ('0' + ALEATORIO.nextInt(10));
        }
        return new String(digitos);
    }
}
