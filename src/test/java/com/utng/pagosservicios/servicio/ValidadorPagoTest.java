package com.utng.pagosservicios.servicio;

import com.utng.pagosservicios.Fixtures;
import com.utng.pagosservicios.dominio.catalogo.Producto;
import com.utng.pagosservicios.dominio.pago.SolicitudPago;
import com.utng.pagosservicios.repositorio.ProductoRepository;
import com.utng.pagosservicios.servicio.excepcion.CodigoError;
import com.utng.pagosservicios.servicio.excepcion.ReglaNegocioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ValidadorPagoTest {

    private final ProductoRepository productos = mock(ProductoRepository.class);
    private ValidadorPago validador;

    @BeforeEach
    void preparar() {
        validador = new ValidadorPago(productos, Fixtures.propiedadesPagos(true));
        when(productos.buscarEnCatalogo("TAE_TELCEL", "TEL_100")).thenReturn(Optional.of(Fixtures.telcel100()));
        when(productos.buscarEnCatalogo("CFE", "CFE_PAGO")).thenReturn(Optional.of(Fixtures.cfe()));
    }

    private static SolicitudPago telcel(String referencia, String monto) {
        return new SolicitudPago("1000000001", "TAE_TELCEL", "TEL_100", referencia, new BigDecimal(monto));
    }

    private void esperarRechazo(ValidadorPago usado, SolicitudPago solicitud, CodigoError codigo) {
        assertThatThrownBy(() -> usado.validar(solicitud))
                .isInstanceOfSatisfying(ReglaNegocioException.class, e -> assertThat(e.getCodigo()).isEqualTo(codigo));
    }

    private void esperarRechazo(SolicitudPago solicitud, CodigoError codigo) {
        esperarRechazo(validador, solicitud, codigo);
    }

    @Test
    void solicitudCorrectaDevuelveElProducto() {
        Producto producto = validador.validar(telcel("5512345678", "100.00"));
        assertThat(producto.getIdProducto()).isEqualTo("TEL_100");
    }

    @Test
    void productoInexistente() {
        esperarRechazo(new SolicitudPago("1000000001", "TAE_TELCEL", "TEL_999", "5512345678",
                new BigDecimal("100.00")), CodigoError.PRODUCTO_NO_DISPONIBLE);
    }

    @Test
    void productoDesactivadoAunqueExistaEnElCatalogo() {
        Producto inactivo = Fixtures.telcel100();
        inactivo.desactivar();
        when(productos.buscarEnCatalogo("TAE_TELCEL", "TEL_100")).thenReturn(Optional.of(inactivo));
        esperarRechazo(telcel("5512345678", "100.00"), CodigoError.PRODUCTO_NO_DISPONIBLE);
    }

    /** El catalogo de ejemplo tiene identificadores que el proveedor real no conoce. */
    @Test
    void productoDeEjemplo_seRechazaSiNoEstaPermitido() {
        ValidadorPago soloProveedor = new ValidadorPago(productos, Fixtures.propiedadesPagos(false));
        esperarRechazo(soloProveedor, telcel("5512345678", "100.00"), CodigoError.PRODUCTO_NO_DISPONIBLE);
    }

    @Test
    void productoSinIdentificadorDelProveedor_noSePuedeVender() {
        Producto sinMapeo = Fixtures.telcel100();
        ReflectionTestUtils.setField(sinMapeo, "idProductoProveedor", null);
        when(productos.buscarEnCatalogo("TAE_TELCEL", "TEL_100")).thenReturn(Optional.of(sinMapeo));
        esperarRechazo(telcel("5512345678", "100.00"), CodigoError.PRODUCTO_NO_DISPONIBLE);
    }

    @Test
    void montoManipuladoEnProductoDeMontoFijo() {
        esperarRechazo(telcel("5512345678", "1.00"), CodigoError.MONTO_NO_PERMITIDO);
    }

    @Test
    void montoFueraDeRangoYNoMultiplo() {
        esperarRechazo(new SolicitudPago("1000000001", "CFE", "CFE_PAGO", "123456789012",
                new BigDecimal("5.00")), CodigoError.MONTO_NO_PERMITIDO);
        esperarRechazo(new SolicitudPago("1000000001", "CFE", "CFE_PAGO", "123456789012",
                new BigDecimal("100.25")), CodigoError.MONTO_NO_PERMITIDO);
    }

    @Test
    void referenciaConFormatoIncorrecto() {
        esperarRechazo(telcel("12345", "100.00"), CodigoError.REFERENCIA_INVALIDA);
    }
}
