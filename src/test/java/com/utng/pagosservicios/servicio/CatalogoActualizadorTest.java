package com.utng.pagosservicios.servicio;

import com.utng.pagosservicios.Fixtures;
import com.utng.pagosservicios.dominio.catalogo.CondicionesVenta;
import com.utng.pagosservicios.dominio.catalogo.Modalidad;
import com.utng.pagosservicios.dominio.catalogo.OrigenCatalogo;
import com.utng.pagosservicios.dominio.catalogo.Producto;
import com.utng.pagosservicios.dominio.catalogo.Servicio;
import com.utng.pagosservicios.dominio.catalogo.TipoMonto;
import com.utng.pagosservicios.proveedor.ProductoProveedor;
import com.utng.pagosservicios.repositorio.ProductoRepository;
import com.utng.pagosservicios.repositorio.ServicioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CatalogoActualizadorTest {

    private static final Instant AHORA = Instant.parse("2026-10-27T09:10:00Z");

    private final ServicioRepository servicios = mock(ServicioRepository.class);
    private final ProductoRepository productos = mock(ProductoRepository.class);
    private CatalogoActualizador actualizador;

    private static ProductoProveedor telcel20() {
        return new ProductoProveedor(1, "Telcel", "TIPO_1", 10, "Telcel $20", Modalidad.RECARGA,
                new BigDecimal("20.00"), "Recarga inmediata");
    }

    private static ProductoProveedor cfe() {
        return new ProductoProveedor(7, "CFE", "TIPO_2", 70, "Pago CFE", Modalidad.PAGO_SERVICIO,
                BigDecimal.ZERO, null);
    }

    @BeforeEach
    void preparar() {
        actualizador = new CatalogoActualizador(servicios, productos, Fixtures.propiedadesPagos(false));
        when(servicios.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
        when(productos.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
        when(servicios.findByOrigen(OrigenCatalogo.PROVEEDOR)).thenReturn(List.of());
        when(productos.buscarPorOrigen(OrigenCatalogo.PROVEEDOR)).thenReturn(List.of());
    }

    @Test
    void creaLosServiciosYProductosNuevosConLasReglasQueCorresponden() {
        int activos = actualizador.aplicar(List.of(telcel20(), cfe()), AHORA);

        assertThat(activos).isEqualTo(2);
        verify(servicios, times(2)).save(any());
        ArgumentCaptor<Producto> creados = ArgumentCaptor.forClass(Producto.class);
        verify(productos, times(2)).save(creados.capture());

        Producto telcel = creados.getAllValues().get(0);
        assertThat(telcel.getIdProducto()).isEqualTo("10");
        assertThat(telcel.getIdProductoProveedor()).isEqualTo(10);
        assertThat(telcel.getServicio().getIdServicio()).isEqualTo("1");
        assertThat(telcel.getOrigen()).isEqualTo(OrigenCatalogo.PROVEEDOR);
        assertThat(telcel.getModalidad()).isEqualTo(Modalidad.RECARGA);
        assertThat(telcel.getTipoMonto()).isEqualTo(TipoMonto.FIJO);
        assertThat(telcel.getMontoFijo()).isEqualByComparingTo("20.00");
        assertThat(telcel.referenciaValida("5512345678")).isTrue();
        assertThat(telcel.referenciaValida("55123")).isFalse();
        assertThat(telcel.getComision()).isEqualByComparingTo("0");
        assertThat(telcel.disponible()).isTrue();

        Producto recibo = creados.getAllValues().get(1);
        assertThat(recibo.getModalidad()).isEqualTo(Modalidad.PAGO_SERVICIO);
        assertThat(recibo.getTipoMonto()).isEqualTo(TipoMonto.ABIERTO);
        assertThat(recibo.getMontoMinimo()).isEqualByComparingTo("1.00");
        assertThat(recibo.getMontoMaximo()).isEqualByComparingTo("10000.00");
        assertThat(recibo.referenciaValida("ABC123456789")).isTrue();
    }

    @Test
    void variosProductosDelMismoServicioCreanUnSoloServicio() {
        ProductoProveedor telcel50 = new ProductoProveedor(1, "Telcel", "TIPO_1", 11, "Telcel $50",
                Modalidad.RECARGA, new BigDecimal("50.00"), null);

        actualizador.aplicar(List.of(telcel20(), telcel50), AHORA);

        verify(servicios, times(1)).save(any());
        verify(productos, times(2)).save(any());
    }

    @Test
    void actualizaLosExistentesConservaLaComisionYDesactivaLoQueYaNoSePublica() {
        Servicio telcel = Servicio.desdeProveedor(1, "Telcel viejo", "TIPO_1", AHORA.minusSeconds(86_400));
        Producto vigente = Producto.desdeProveedor(telcel, 10, "Telcel $20",
                CondicionesVenta.montoFijo(Modalidad.RECARGA, new BigDecimal("20.00"), 10, "^[0-9]{10}$"), null,
                AHORA.minusSeconds(86_400));
        ReflectionTestUtils.setField(vigente, "comision", new BigDecimal("2.50"));
        Producto retirado = Producto.desdeProveedor(telcel, 99, "Telcel $999",
                CondicionesVenta.montoFijo(Modalidad.RECARGA, new BigDecimal("999.00"), 10, "^[0-9]{10}$"), null,
                AHORA.minusSeconds(86_400));
        when(servicios.findByOrigen(OrigenCatalogo.PROVEEDOR)).thenReturn(List.of(telcel));
        when(productos.buscarPorOrigen(OrigenCatalogo.PROVEEDOR)).thenReturn(List.of(vigente, retirado));
        ProductoProveedor nuevoPrecio = new ProductoProveedor(1, "Telcel", "TIPO_1", 10, "Telcel $25",
                Modalidad.RECARGA, new BigDecimal("25.00"), "Nueva leyenda");

        actualizador.aplicar(List.of(nuevoPrecio), AHORA);

        verify(servicios, never()).save(any());
        verify(productos, never()).save(any());
        assertThat(telcel.getNombre()).isEqualTo("Telcel");
        assertThat(vigente.getMontoFijo()).isEqualByComparingTo("25.00");
        assertThat(vigente.getNombre()).isEqualTo("Telcel $25");
        assertThat(vigente.getLeyenda()).isEqualTo("Nueva leyenda");
        assertThat(vigente.getComision()).isEqualByComparingTo("2.50");
        assertThat(vigente.getSincronizadoEn()).isEqualTo(AHORA);
        assertThat(retirado.isActivo()).isFalse();
    }

    @Test
    void unServicioQueYaNoSePublicaSeDesactiva() {
        Servicio viejo = Servicio.desdeProveedor(5, "Servicio retirado", null, AHORA.minusSeconds(86_400));
        when(servicios.findByOrigen(OrigenCatalogo.PROVEEDOR)).thenReturn(List.of(viejo));

        actualizador.aplicar(List.of(telcel20()), AHORA);

        assertThat(viejo.isActivo()).isFalse();
    }

    /** En sendTx no viaja monto para una recarga: sin precio no se podria pedir. */
    @Test
    void unaRecargaSinPrecioSeTrataComoPagoDeServicio() {
        ProductoProveedor sinPrecio = new ProductoProveedor(1, "Telcel", null, 12, "Telcel libre",
                Modalidad.RECARGA, BigDecimal.ZERO, null);

        CondicionesVenta condiciones = actualizador.condiciones(sinPrecio);

        assertThat(condiciones.modalidad()).isEqualTo(Modalidad.PAGO_SERVICIO);
        assertThat(condiciones.tipoMonto()).isEqualTo(TipoMonto.ABIERTO);
    }
}
