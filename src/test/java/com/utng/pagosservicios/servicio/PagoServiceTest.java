package com.utng.pagosservicios.servicio;

import com.utng.pagosservicios.Fixtures;
import com.utng.pagosservicios.dominio.catalogo.Modalidad;
import com.utng.pagosservicios.dominio.catalogo.Producto;
import com.utng.pagosservicios.dominio.pago.MotivoResolucion;
import com.utng.pagosservicios.dominio.pago.SolicitudPago;
import com.utng.pagosservicios.dominio.pago.TransaccionPago;
import com.utng.pagosservicios.proveedor.DatosCanje;
import com.utng.pagosservicios.proveedor.OrdenPago;
import com.utng.pagosservicios.proveedor.ProveedorPagos;
import com.utng.pagosservicios.proveedor.ResultadoEnvio;
import com.utng.pagosservicios.repositorio.ClienteRepository;
import com.utng.pagosservicios.repositorio.TransaccionPagoRepository;
import com.utng.pagosservicios.servicio.excepcion.AccesoDenegadoException;
import com.utng.pagosservicios.servicio.excepcion.CodigoError;
import com.utng.pagosservicios.servicio.excepcion.ConflictoException;
import com.utng.pagosservicios.servicio.excepcion.PagoDuplicadoException;
import com.utng.pagosservicios.servicio.excepcion.PagoRechazadoException;
import com.utng.pagosservicios.servicio.excepcion.ProveedorNoDisponibleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.Optional;

import static com.utng.pagosservicios.dominio.pago.EstadoTransaccion.CONFIRMADA;
import static com.utng.pagosservicios.dominio.pago.EstadoTransaccion.DEBITADA;
import static com.utng.pagosservicios.dominio.pago.EstadoTransaccion.EN_CONCILIACION;
import static com.utng.pagosservicios.dominio.pago.EstadoTransaccion.ENVIADA;
import static com.utng.pagosservicios.dominio.pago.EstadoTransaccion.REVERSADA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PagoServiceTest {

    private static final long ID = 100L;

    private final ClienteRepository clientes = mock(ClienteRepository.class);
    private final TransaccionPagoRepository transacciones = mock(TransaccionPagoRepository.class);
    private final ValidadorPago validador = mock(ValidadorPago.class);
    private final FondosService fondos = mock(FondosService.class);
    private final TransaccionPagoService estados = mock(TransaccionPagoService.class);
    private final ProveedorPagos proveedor = mock(ProveedorPagos.class);

    private final SolicitudPago solicitud = Fixtures.solicitudTelcel();
    private final ClaveIdempotencia clave = ClaveIdempotencia.de("clave-0001");
    private final Producto producto = Fixtures.telcel100();
    private PagoService servicio;

    @BeforeEach
    void preparar() {
        servicio = new PagoService(clientes, transacciones, validador, fondos, estados, proveedor);
        when(clientes.findByUsuario("ana")).thenReturn(Optional.of(Fixtures.cliente(7L, "ana")));
        when(transacciones.findByClienteIdAndClaveIdempotencia(7L, "clave-0001")).thenReturn(Optional.empty());
        when(validador.validar(solicitud)).thenReturn(producto);
        when(proveedor.disponible()).thenReturn(true);
        when(fondos.reservar(eq(7L), eq("clave-0001"), eq(solicitud), eq(producto), any()))
                .thenReturn(Fixtures.transaccion(ID, DEBITADA));
        when(estados.marcarEnviada(ID)).thenReturn(Fixtures.transaccion(ID, ENVIADA));
    }

    @Test
    void aprobada_confirmaYNoDevuelveDinero() {
        when(proveedor.enviar(any())).thenReturn(ResultadoEnvio.aprobada("01", "F-1", "A-1"));
        when(estados.confirmar(ID, "01", "F-1", "A-1", null)).thenReturn(Fixtures.transaccion(ID, CONFIRMADA));

        ResultadoPago resultado = servicio.pagar("ana", clave, solicitud);

        assertThat(resultado.estado()).isEqualTo(CONFIRMADA);
        assertThat(resultado.repetido()).isFalse();
        verify(fondos, never()).reversar(anyLong(), any(), any());
    }

    @Test
    void laOrdenLlevaLaReferenciaExternaYElMontoDelServicio() {
        when(proveedor.enviar(any())).thenReturn(ResultadoEnvio.aprobada("01", "F-1", "A-1"));
        when(estados.confirmar(ID, "01", "F-1", "A-1", null)).thenReturn(Fixtures.transaccion(ID, CONFIRMADA));

        servicio.pagar("ana", clave, solicitud);

        ArgumentCaptor<OrdenPago> orden = ArgumentCaptor.forClass(OrdenPago.class);
        verify(proveedor).enviar(orden.capture());
        assertThat(orden.getValue().referenciaExterna()).matches("^[1-9][0-9]{14}$");
        // Al proveedor viajan sus propios identificadores, no los codigos de la API.
        assertThat(orden.getValue().idServicio()).isEqualTo(1001);
        assertThat(orden.getValue().idProducto()).isEqualTo(2003);
        assertThat(orden.getValue().modalidad()).isEqualTo(Modalidad.RECARGA);
        assertThat(orden.getValue().monto()).isEqualByComparingTo("100.00");
        assertThat(orden.getValue().momento()).isEqualTo(Fixtures.AHORA);
    }

    @Test
    void rechazada_devuelveElDineroYInformaElRechazo() {
        when(proveedor.enviar(any())).thenReturn(ResultadoEnvio.rechazada("02"));

        assertThatThrownBy(() -> servicio.pagar("ana", clave, solicitud))
                .isInstanceOfSatisfying(PagoRechazadoException.class,
                        e -> assertThat(e.getTransaccionId()).isEqualTo(ID));
        verify(fondos).reversar(ID, MotivoResolucion.RECHAZADA_POR_PROVEEDOR, "02");
    }

    @Test
    void duplicadaEnElProveedor_devuelveElDineroYLoInformaComoConflicto() {
        when(proveedor.enviar(any())).thenReturn(ResultadoEnvio.duplicada("06"));

        assertThatThrownBy(() -> servicio.pagar("ana", clave, solicitud))
                .isInstanceOfSatisfying(PagoDuplicadoException.class, e -> {
                    assertThat(e.getTransaccionId()).isEqualTo(ID);
                    assertThat(e.getCodigo()).isEqualTo(CodigoError.PAGO_DUPLICADO);
                });
        verify(fondos).reversar(ID, MotivoResolucion.DUPLICADA_EN_PROVEEDOR, "06");
    }

    @Test
    void aprobadaConDatosDeCanje_losGuardaConLaConfirmacion() {
        DatosCanje canje = new DatosCanje("1234-5678", "Canjea en la app");
        when(proveedor.enviar(any())).thenReturn(ResultadoEnvio.aprobada("01", "F-1", "A-1", canje));
        when(estados.confirmar(ID, "01", "F-1", "A-1", canje)).thenReturn(Fixtures.transaccion(ID, CONFIRMADA));

        servicio.pagar("ana", clave, solicitud);

        verify(estados).confirmar(ID, "01", "F-1", "A-1", canje);
    }

    @Test
    void noProcesada_devuelveElDineroYReportaProveedorNoDisponible() {
        when(proveedor.enviar(any())).thenReturn(ResultadoEnvio.noProcesada("CONEXION_RECHAZADA"));

        assertThatThrownBy(() -> servicio.pagar("ana", clave, solicitud))
                .isInstanceOf(ProveedorNoDisponibleException.class);
        verify(fondos).reversar(ID, MotivoResolucion.PROVEEDOR_NO_DISPONIBLE, "CONEXION_RECHAZADA");
    }

    /** El requisito central: un timeout NO significa que el pago fallo. */
    @Test
    void indeterminada_retieneElDineroYVaAConciliacion() {
        when(proveedor.enviar(any())).thenReturn(ResultadoEnvio.indeterminada("TIMEOUT_LECTURA"));
        when(estados.enviarAConciliacion(ID, "TIMEOUT_LECTURA")).thenReturn(Fixtures.transaccion(ID, EN_CONCILIACION));

        ResultadoPago resultado = servicio.pagar("ana", clave, solicitud);

        assertThat(resultado.estado()).isEqualTo(EN_CONCILIACION);
        verify(fondos, never()).reversar(anyLong(), any(), any());
    }

    @Test
    void errorInesperadoDelAdaptador_seTrataComoIndeterminadoNuncaComoRechazo() {
        when(proveedor.enviar(any())).thenThrow(new IllegalStateException("fallo no previsto"));
        when(estados.enviarAConciliacion(ID, "ERROR_INESPERADO")).thenReturn(Fixtures.transaccion(ID, EN_CONCILIACION));

        ResultadoPago resultado = servicio.pagar("ana", clave, solicitud);

        assertThat(resultado.estado()).isEqualTo(EN_CONCILIACION);
        verify(fondos, never()).reversar(anyLong(), any(), any());
    }

    @Test
    void circuitoAbierto_rechazaSinTocarElSaldo() {
        when(proveedor.disponible()).thenReturn(false);

        assertThatThrownBy(() -> servicio.pagar("ana", clave, solicitud))
                .isInstanceOf(ProveedorNoDisponibleException.class);
        verify(fondos, never()).reservar(any(), any(), any(), any(), any());
        verify(proveedor, never()).enviar(any());
    }

    @Test
    void claveRepetidaConLosMismosDatos_devuelveLaOperacionOriginalSinCobrar() {
        when(transacciones.findByClienteIdAndClaveIdempotencia(7L, "clave-0001"))
                .thenReturn(Optional.of(Fixtures.transaccion(55L, CONFIRMADA)));

        ResultadoPago resultado = servicio.pagar("ana", clave, solicitud);

        assertThat(resultado.repetido()).isTrue();
        assertThat(resultado.transaccionId()).isEqualTo(55L);
        verify(fondos, never()).reservar(any(), any(), any(), any(), any());
        verify(proveedor, never()).enviar(any());
    }

    @Test
    void claveRepetidaDeUnPagoReversado_noVuelveACobrar() {
        when(transacciones.findByClienteIdAndClaveIdempotencia(7L, "clave-0001"))
                .thenReturn(Optional.of(Fixtures.transaccion(56L, REVERSADA)));

        ResultadoPago resultado = servicio.pagar("ana", clave, solicitud);

        assertThat(resultado.estado()).isEqualTo(REVERSADA);
        assertThat(resultado.repetido()).isTrue();
        verify(proveedor, never()).enviar(any());
    }

    @Test
    void claveRepetidaConOtrosDatos_esConflicto() {
        when(transacciones.findByClienteIdAndClaveIdempotencia(7L, "clave-0001"))
                .thenReturn(Optional.of(Fixtures.transaccion(55L, CONFIRMADA)));
        SolicitudPago otroMonto = new SolicitudPago("1000000001", "TAE_TELCEL", "TEL_200", "5512345678",
                new BigDecimal("200.00"));

        assertThatThrownBy(() -> servicio.pagar("ana", clave, otroMonto))
                .isInstanceOfSatisfying(ConflictoException.class,
                        e -> assertThat(e.getCodigo()).isEqualTo(CodigoError.CLAVE_IDEMPOTENCIA_REUTILIZADA));
    }

    @Test
    void claveRepetidaMientrasLaOriginalSigueEnCurso_esConflicto() {
        when(transacciones.findByClienteIdAndClaveIdempotencia(7L, "clave-0001"))
                .thenReturn(Optional.of(Fixtures.transaccion(55L, ENVIADA)));

        assertThatThrownBy(() -> servicio.pagar("ana", clave, solicitud))
                .isInstanceOfSatisfying(ConflictoException.class, e -> {
                    assertThat(e.getCodigo()).isEqualTo(CodigoError.PAGO_EN_PROCESO);
                    assertThat(e.getTransaccionId()).isEqualTo(55L);
                });
    }

    @Test
    void carreraConLaMismaClave_ganaLaQueInsertoPrimeroYNoSeCobraDosVeces() {
        when(fondos.reservar(any(), any(), any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("uk_transaccion_idempotencia"));
        when(transacciones.findByClienteIdAndClaveIdempotencia(7L, "clave-0001"))
                .thenReturn(Optional.empty(), Optional.of(Fixtures.transaccion(77L, CONFIRMADA)));

        ResultadoPago resultado = servicio.pagar("ana", clave, solicitud);

        assertThat(resultado.repetido()).isTrue();
        assertThat(resultado.transaccionId()).isEqualTo(77L);
        verify(proveedor, never()).enviar(any());
    }

    @Test
    void clienteSinPermisoDePagos_esRechazado() {
        when(clientes.findByUsuario("carla")).thenReturn(Optional.of(Fixtures.clienteSinPermisoDePagos(8L, "carla")));

        assertThatThrownBy(() -> servicio.pagar("carla", clave, solicitud))
                .isInstanceOfSatisfying(AccesoDenegadoException.class,
                        e -> assertThat(e.getCodigo()).isEqualTo(CodigoError.CLIENTE_NO_HABILITADO));
        verify(fondos, never()).reservar(any(), any(), any(), any(), any());
    }

    @Test
    void consultarUnPagoAjenoNoLoEncuentra() {
        when(transacciones.findByIdAndClienteId(ID, 7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.consultar("ana", ID))
                .hasMessageContaining("No existe un pago");
    }

    @Test
    void consultarDevuelveElEstadoActual() {
        TransaccionPago transaccion = Fixtures.transaccion(ID, EN_CONCILIACION);
        when(transacciones.findByIdAndClienteId(ID, 7L)).thenReturn(Optional.of(transaccion));
        assertThat(servicio.consultar("ana", ID).estado()).isEqualTo(EN_CONCILIACION);
    }
}
