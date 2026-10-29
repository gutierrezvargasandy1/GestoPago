package com.utng.pagosservicios.proveedor.gestopago;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class ClasificadorFallosTest {

    private static ResourceAccessException envuelta(IOException causa) {
        return new ResourceAccessException("I/O error on POST request", causa);
    }

    @Test
    void sinConexionHayCertezaDeQueNoLlego() {
        assertThat(ClasificadorFallos.noLlegoAlProveedor(envuelta(new ConnectException("Connection refused")))).isTrue();
        assertThat(ClasificadorFallos.noLlegoAlProveedor(envuelta(new HttpConnectTimeoutException("connect")))).isTrue();
        assertThat(ClasificadorFallos.noLlegoAlProveedor(envuelta(new UnknownHostException("host")))).isTrue();
        assertThat(ClasificadorFallos.noLlegoAlProveedor(
                CallNotPermittedException.createCallNotPermittedException(CircuitBreaker.ofDefaults("x")))).isTrue();
    }

    @Test
    void unTimeoutDeLecturaNoDaCerteza() {
        assertThat(ClasificadorFallos.noLlegoAlProveedor(envuelta(new HttpTimeoutException("Request timed out")))).isFalse();
        assertThat(ClasificadorFallos.noLlegoAlProveedor(envuelta(new SocketException("Connection reset")))).isFalse();
        assertThat(ClasificadorFallos.noLlegoAlProveedor(new FalloServidorProveedorException(502))).isFalse();
    }

    @Test
    void describeElTipoDeFallo() {
        assertThat(ClasificadorFallos.describir(envuelta(new HttpTimeoutException("x")))).isEqualTo("TIMEOUT_LECTURA");
        assertThat(ClasificadorFallos.describir(envuelta(new HttpConnectTimeoutException("x")))).isEqualTo("TIMEOUT_CONEXION");
        assertThat(ClasificadorFallos.describir(envuelta(new ConnectException("x")))).isEqualTo("CONEXION_RECHAZADA");
        assertThat(ClasificadorFallos.describir(new FalloServidorProveedorException(503))).isEqualTo("HTTP_503");
        assertThat(ClasificadorFallos.describir(envuelta(new SocketException("reset")))).isEqualTo("SocketException");
    }
}
