package com.utng.pagosservicios.proveedor.gestopago;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;

import javax.net.ssl.SSLHandshakeException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;

/**
 * Decide si un fallo de red garantiza que la peticion NO llego al proveedor.
 *
 * <p>Solo hay certeza cuando la conexion nunca se establecio: circuito abierto,
 * conexion rechazada, timeout DE CONEXION, host desconocido o fallo del
 * handshake TLS (el cuerpo HTTP se envia despues del handshake). En cualquier
 * otro caso (timeout de LECTURA, conexion cortada a mitad de la respuesta) la
 * peticion pudo haberse procesado y el resultado es indeterminado.
 */
final class ClasificadorFallos {

    private static final int PROFUNDIDAD_MAXIMA = 10;

    private ClasificadorFallos() {
    }

    static boolean noLlegoAlProveedor(Throwable error) {
        Throwable actual = error;
        for (int i = 0; actual != null && i < PROFUNDIDAD_MAXIMA; i++) {
            if (actual instanceof CallNotPermittedException
                    || actual instanceof ConnectException
                    || actual instanceof HttpConnectTimeoutException
                    || actual instanceof UnknownHostException
                    || actual instanceof NoRouteToHostException
                    || actual instanceof SSLHandshakeException) {
                return true;
            }
            actual = actual.getCause() == actual ? null : actual.getCause();
        }
        return false;
    }

    /** Etiqueta corta para guardar en codigo_resultado y en los logs. */
    static String describir(Throwable error) {
        if (error instanceof CallNotPermittedException) {
            return "CIRCUITO_ABIERTO";
        }
        if (error instanceof FalloServidorProveedorException fallo) {
            return "HTTP_" + fallo.status();
        }
        Throwable actual = error;
        for (int i = 0; actual != null && i < PROFUNDIDAD_MAXIMA; i++) {
            if (actual instanceof HttpConnectTimeoutException) {
                return "TIMEOUT_CONEXION";
            }
            if (actual instanceof HttpTimeoutException) {
                return "TIMEOUT_LECTURA";
            }
            if (actual instanceof ConnectException) {
                return "CONEXION_RECHAZADA";
            }
            actual = actual.getCause() == actual ? null : actual.getCause();
        }
        Throwable raiz = error;
        for (int i = 0; raiz.getCause() != null && raiz.getCause() != raiz && i < PROFUNDIDAD_MAXIMA; i++) {
            raiz = raiz.getCause();
        }
        String nombre = raiz.getClass().getSimpleName();
        return nombre.length() > 40 ? nombre.substring(0, 40) : nombre;
    }
}
