package com.utng.pagosservicios.servicio;

import com.utng.pagosservicios.Fixtures;
import com.utng.pagosservicios.dominio.pago.EstadoTransaccion;
import com.utng.pagosservicios.dominio.pago.MotivoResolucion;
import com.utng.pagosservicios.dominio.pago.TransaccionPago;
import com.utng.pagosservicios.proveedor.DatosCanje;
import com.utng.pagosservicios.proveedor.OrdenPago;
import com.utng.pagosservicios.proveedor.ProveedorPagos;
import com.utng.pagosservicios.proveedor.ResultadoConsulta;
import com.utng.pagosservicios.repositorio.ProductoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ConciliacionServiceTest {

    private static final long ID = 1L;

    private final TransaccionPagoService transacciones = mock(TransaccionPagoService.class);
    private final FondosService fondos = mock(FondosService.class);
    private final ProveedorPagos proveedor = mock(ProveedorPagos.class);
    private final ProductoRepository productos = mock(ProductoRepository.class);
    private ConciliacionService servicio;

    @BeforeEach
    void preparar() {
        servicio = crear(Clock.fixed(Fixtures.AHORA.plus(Duration.ofMinutes(10)), ZoneOffset.UTC));
        when(productos.buscarConServicio(10L)).thenReturn(Optional.of(Fixtures.telcel100()));
    }

    /** Maximo 3 intentos; "no aplicada" debe repetirse 2 veces antes de reversar. */
    private ConciliacionService crear(Clock reloj) {
        return new ConciliacionService(transacciones, fondos, proveedor, productos,
                Fixtures.propiedadesPagos(3, 2), reloj);
    }

    private TransaccionPago reclamada(EstadoTransaccion estado, int intentos) {
        TransaccionPago transaccion = Fixtures.conIntentos(Fixtures.transaccion(ID, estado), intentos);
        when(transacciones.reclamarParaConciliar(ID)).thenReturn(Optional.of(transaccion));
        return transaccion;
    }

    private void proveedorResponde(ResultadoConsulta resultado) {
        when(proveedor.consultar(any())).thenReturn(resultado);
    }

    @Test
    void unaDebitadaSeReversaSinPreguntarAlProveedor() {
        reclamada(EstadoTransaccion.DEBITADA, 1);
        servicio.conciliar(ID);
        verify(fondos).reversar(ID, MotivoResolucion.NUNCA_ENVIADA, null);
        verify(proveedor, never()).consultar(any());
    }

    /** confirmTx identifica la operacion por sus datos: se reconstruye la misma orden del envio. */
    @Test
    void preguntaPorLaMismaOperacionQueSeEnvio() {
        TransaccionPago transaccion = reclamada(EstadoTransaccion.EN_CONCILIACION, 1);
        proveedorResponde(ResultadoConsulta.indeterminada("HTTP_503"));

        servicio.conciliar(ID);

        ArgumentCaptor<OrdenPago> orden = ArgumentCaptor.forClass(OrdenPago.class);
        verify(proveedor).consultar(orden.capture());
        assertThat(orden.getValue().referenciaExterna()).isEqualTo(transaccion.getReferenciaExterna());
        assertThat(orden.getValue().momento()).isEqualTo(transaccion.getEnviadoEn());
        assertThat(orden.getValue().idProducto()).isEqualTo(2003);
    }

    @Test
    void siElProveedorLaAplico_seConfirmaConSusDatos() {
        reclamada(EstadoTransaccion.EN_CONCILIACION, 1);
        DatosCanje canje = new DatosCanje("1234", "Canje");
        proveedorResponde(ResultadoConsulta.aprobada("01", "F-7", "A-7", canje));
        servicio.conciliar(ID);
        verify(transacciones).confirmar(ID, "01", "F-7", "A-7", canje);
        verify(fondos, never()).reversar(anyLong(), any(), any());
    }

    @Test
    void siElProveedorLaRechazo_seDevuelveElDinero() {
        reclamada(EstadoTransaccion.EN_CONCILIACION, 1);
        proveedorResponde(ResultadoConsulta.rechazada("03"));
        servicio.conciliar(ID);
        verify(fondos).reversar(ID, MotivoResolucion.RECHAZADA_POR_PROVEEDOR, "03");
    }

    @Test
    void unPrimerNoAplicadaNoBastaParaReversar() {
        reclamada(EstadoTransaccion.EN_CONCILIACION, 1);
        proveedorResponde(ResultadoConsulta.noEncontrada("04"));
        servicio.conciliar(ID);
        verify(transacciones).reprogramarConsulta(ID, "04");
        verify(fondos, never()).reversar(anyLong(), any(), any());
    }

    @Test
    void noAplicadaConfirmadaEnUnSegundoIntento_seReversa() {
        reclamada(EstadoTransaccion.EN_CONCILIACION, 2);
        proveedorResponde(ResultadoConsulta.noEncontrada("04"));
        servicio.conciliar(ID);
        verify(fondos).reversar(ID, MotivoResolucion.NO_ENCONTRADA_EN_PROVEEDOR, "04");
    }

    @Test
    void consultaFallida_seReprograma() {
        reclamada(EstadoTransaccion.ENVIADA, 1);
        proveedorResponde(ResultadoConsulta.indeterminada("HTTP_503"));
        servicio.conciliar(ID);
        verify(transacciones).reprogramarConsulta(ID, "HTTP_503");
    }

    @Test
    void alAgotarLosIntentos_pasaARevisionManualConElDineroRetenido() {
        reclamada(EstadoTransaccion.EN_CONCILIACION, 3);
        proveedorResponde(ResultadoConsulta.indeterminada("TIMEOUT_LECTURA"));
        servicio.conciliar(ID);
        verify(transacciones).escalarARevisionManual(ID, MotivoResolucion.INTENTOS_AGOTADOS);
        verify(fondos, never()).reversar(anyLong(), any(), any());
    }

    @Test
    void sinConsultaDeEstatus_pasaDirectoARevisionManual() {
        reclamada(EstadoTransaccion.EN_CONCILIACION, 1);
        proveedorResponde(ResultadoConsulta.noSoportada());
        servicio.conciliar(ID);
        verify(transacciones).escalarARevisionManual(ID, MotivoResolucion.CONSULTA_NO_SOPORTADA);
    }

    /** El proveedor solo responde confirmTx durante 24 h: despues ni se le pregunta. */
    @Test
    void pasadoElPlazoDeConsulta_vaARevisionManualSinPreguntar() {
        servicio = crear(Clock.fixed(Fixtures.AHORA.plus(Duration.ofHours(23)), ZoneOffset.UTC));
        reclamada(EstadoTransaccion.EN_CONCILIACION, 1);

        servicio.conciliar(ID);

        verify(transacciones).escalarARevisionManual(ID, MotivoResolucion.CONSULTA_VENCIDA);
        verify(proveedor, never()).consultar(any());
        verify(fondos, never()).reversar(anyLong(), any(), any());
    }

    @Test
    void sinElProductoNoSePuedeArmarLaConsulta_vaARevisionManual() {
        when(productos.buscarConServicio(10L)).thenReturn(Optional.empty());
        reclamada(EstadoTransaccion.EN_CONCILIACION, 1);

        servicio.conciliar(ID);

        verify(transacciones).escalarARevisionManual(ID, MotivoResolucion.CONSULTA_IMPOSIBLE);
        verify(proveedor, never()).consultar(any());
    }

    @Test
    void unErrorInesperadoAlConsultarNoReversa() {
        reclamada(EstadoTransaccion.EN_CONCILIACION, 1);
        when(proveedor.consultar(any())).thenThrow(new IllegalStateException("x"));
        servicio.conciliar(ID);
        verify(transacciones).reprogramarConsulta(ID, "ERROR_INESPERADO");
        verify(fondos, never()).reversar(anyLong(), any(), any());
    }

    @Test
    void siNoEstaListaOYaSeResolvio_noHaceNada() {
        when(transacciones.reclamarParaConciliar(ID)).thenReturn(Optional.empty());
        servicio.conciliar(ID);
        verifyNoInteractions(proveedor, fondos);
    }

    @Test
    void siOtroProcesoLaTomo_laDejaPasar() {
        when(transacciones.reclamarParaConciliar(ID)).thenThrow(new OptimisticLockingFailureException("version"));
        servicio.conciliar(ID);
        verifyNoInteractions(proveedor, fondos);
    }
}
