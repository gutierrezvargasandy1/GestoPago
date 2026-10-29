package com.utng.pagosservicios.integracion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Peticiones simultaneas reales (varios hilos) contra la misma cuenta. Sin el
 * bloqueo pesimista, varias leerian el mismo saldo y todas debitarian.
 */
class ConcurrenciaIntegracionTest extends IntegracionBase {

    private static final int HILOS = 10;
    private String token;

    @BeforeEach
    void cliente() {
        token = clienteConCuenta("ana", CUENTA, "500.00");
        proveedorAprueba();
    }

    /** Lanza todas las peticiones a la vez y devuelve los codigos HTTP obtenidos. */
    private List<Integer> enParalelo(IntFunction<String> clavePorHilo) throws Exception {
        ExecutorService hilos = Executors.newFixedThreadPool(HILOS);
        CountDownLatch salida = new CountDownLatch(1);
        try {
            List<Future<Integer>> futuros = new ArrayList<>();
            for (int i = 0; i < HILOS; i++) {
                String clave = clavePorHilo.apply(i);
                Callable<Integer> peticion = () -> {
                    salida.await();
                    return pagar(token, clave, telcel100()).andReturn().getResponse().getStatus();
                };
                futuros.add(hilos.submit(peticion));
            }
            salida.countDown();
            List<Integer> estados = new ArrayList<>();
            for (Future<Integer> futuro : futuros) {
                estados.add(futuro.get(60, TimeUnit.SECONDS));
            }
            return estados;
        } finally {
            hilos.shutdownNow();
        }
    }

    @Test
    void diezPagosSimultaneosDe100ConSaldo500_soloPasanCincoYNuncaHaySobregiro() throws Exception {
        List<Integer> estados = enParalelo(i -> "concurrencia-distinta-" + i);

        assertThat(estados).filteredOn(estado -> estado == 201).hasSize(5);
        assertThat(estados).filteredOn(estado -> estado == 422).hasSize(5);
        assertThat(datos.saldo(CUENTA)).isEqualByComparingTo("0.00");
        assertThat(datos.contarMovimientos(CUENTA, "CARGO")).isEqualTo(5);
        GESTOPAGO.verify(5, postRequestedFor(urlEqualTo(RUTA_ENVIO)));
    }

    @Test
    void laMismaClaveEnviadaDiezVecesALaVez_generaUnSoloCargo() throws Exception {
        List<Integer> estados = enParalelo(i -> "concurrencia-misma-clave");

        assertThat(estados).filteredOn(estado -> estado == 201).hasSize(1);
        // Las demas reciben la operacion original (200) o "en proceso" (409), nunca un segundo cobro.
        assertThat(estados).allMatch(estado -> estado == 201 || estado == 200 || estado == 409);
        assertThat(datos.saldo(CUENTA)).isEqualByComparingTo("400.00");
        assertThat(datos.contarMovimientos(CUENTA, "CARGO")).isEqualTo(1);
        GESTOPAGO.verify(1, postRequestedFor(urlEqualTo(RUTA_ENVIO)));
    }
}
