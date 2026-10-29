package com.utng.pagosservicios.servicio;

import com.utng.pagosservicios.Fixtures;
import com.utng.pagosservicios.dominio.catalogo.Modalidad;
import com.utng.pagosservicios.proveedor.CatalogoObtenido;
import com.utng.pagosservicios.proveedor.CatalogoProveedor;
import com.utng.pagosservicios.proveedor.ProductoProveedor;
import com.utng.pagosservicios.repositorio.RegistroSincronizacionJdbc;
import com.utng.pagosservicios.servicio.SincronizacionCatalogoService.Resultado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SincronizacionCatalogoServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-27T09:10:00Z");

    private final CatalogoProveedor proveedor = mock(CatalogoProveedor.class);
    private final CatalogoActualizador actualizador = mock(CatalogoActualizador.class);
    private final RegistroSincronizacionJdbc registros = mock(RegistroSincronizacionJdbc.class);
    private SincronizacionCatalogoService sincronizacion;

    private final List<ProductoProveedor> publicados = List.of(new ProductoProveedor(1, "Telcel", null, 10,
            "Telcel $20", Modalidad.RECARGA, new BigDecimal("20.00"), null));

    @BeforeEach
    void preparar() {
        sincronizacion = new SincronizacionCatalogoService(proveedor, actualizador, registros,
                Fixtures.propiedadesPagos(false), Clock.fixed(AHORA, ZoneOffset.UTC));
        when(registros.ultimaExitosa()).thenReturn(Optional.empty());
        when(registros.intentosDesde(any())).thenReturn(0);
        when(registros.registrarInicio(any())).thenReturn(42L);
    }

    @Test
    void siYaSeSincronizoEnLasUltimas23Horas_noLlamaAlProveedor() {
        when(registros.ultimaExitosa()).thenReturn(Optional.of(AHORA.minus(Duration.ofHours(2))));

        assertThat(sincronizacion.sincronizarSiCorresponde()).isEqualTo(Resultado.OMITIDA_RECIENTE);
        verify(proveedor, never()).obtenerCatalogo();
    }

    /** El proveedor limita getProductList a 3 llamadas por dia, contando las fallidas. */
    @Test
    void respetaElLimiteDeTresLlamadasDiarias() {
        when(registros.intentosDesde(AHORA.minus(Duration.ofHours(24)))).thenReturn(3);

        assertThat(sincronizacion.sincronizarSiCorresponde()).isEqualTo(Resultado.OMITIDA_LIMITE_DIARIO);
        verify(proveedor, never()).obtenerCatalogo();
        verify(registros, never()).registrarInicio(any());
    }

    @Test
    void sincronizaYRegistraElResultado() {
        when(proveedor.obtenerCatalogo()).thenReturn(CatalogoObtenido.exitoso(publicados));
        when(actualizador.aplicar(publicados, AHORA)).thenReturn(1);

        assertThat(sincronizacion.sincronizarSiCorresponde()).isEqualTo(Resultado.EXITOSA);
        verify(registros).registrarInicio(AHORA);
        verify(registros).registrarFin(42L, AHORA, true, 1, null);
    }

    @Test
    void siElProveedorFalla_elCatalogoLocalNoSeToca() {
        when(proveedor.obtenerCatalogo()).thenReturn(CatalogoObtenido.fallido("HTTP_503"));

        assertThat(sincronizacion.sincronizarSiCorresponde()).isEqualTo(Resultado.FALLIDA);
        verify(actualizador, never()).aplicar(any(), any());
        verify(registros).registrarFin(42L, AHORA, false, 0, "HTTP_503");
    }

    @Test
    void siAplicarFalla_quedaRegistradaComoFallida() {
        when(proveedor.obtenerCatalogo()).thenReturn(CatalogoObtenido.exitoso(publicados));
        when(actualizador.aplicar(any(), any())).thenThrow(new IllegalStateException("constraint"));

        assertThat(sincronizacion.sincronizarSiCorresponde()).isEqualTo(Resultado.FALLIDA);
        verify(registros).registrarFin(eq(42L), eq(AHORA), eq(false), eq(0), eq("ERROR_AL_APLICAR"));
        verify(registros, never()).registrarFin(anyLong(), any(), eq(true), eq(1), any());
    }
}
