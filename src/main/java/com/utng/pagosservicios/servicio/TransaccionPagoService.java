package com.utng.pagosservicios.servicio;

import com.utng.pagosservicios.config.PagosProperties;
import com.utng.pagosservicios.dominio.pago.EstadoTransaccion;
import com.utng.pagosservicios.dominio.pago.MotivoResolucion;
import com.utng.pagosservicios.dominio.pago.TransaccionPago;
import com.utng.pagosservicios.proveedor.DatosCanje;
import com.utng.pagosservicios.repositorio.TransaccionPagoRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Cambios de estado de la transaccion que no mueven dinero. Cada metodo es una
 * transaccion corta; @Version en la entidad evita que dos procesos (la peticion
 * original y la conciliacion) se pisen.
 */
@Service
public class TransaccionPagoService {

    private final TransaccionPagoRepository transacciones;
    private final PagosProperties.Conciliacion conciliacion;
    private final Clock reloj;

    public TransaccionPagoService(TransaccionPagoRepository transacciones, PagosProperties propiedades, Clock reloj) {
        this.transacciones = transacciones;
        this.conciliacion = propiedades.conciliacion();
        this.reloj = reloj;
    }

    /** Transaccion 2: se confirma en base de datos que se va a enviar, antes de enviar. */
    @Transactional
    public TransaccionPago marcarEnviada(Long id) {
        TransaccionPago transaccion = obtener(id);
        transaccion.marcarEnviada(reloj.instant(), conciliacion.margenInicial());
        return transaccion;
    }

    @Transactional
    public TransaccionPago confirmar(Long id, String codigo, String folio, String autorizacion, DatosCanje canje) {
        TransaccionPago transaccion = obtener(id);
        transaccion.confirmar(codigo, folio, autorizacion,
                canje == null ? null : canje.pin(), canje == null ? null : canje.instrucciones(), reloj.instant());
        return transaccion;
    }

    @Transactional
    public TransaccionPago enviarAConciliacion(Long id, String codigo) {
        TransaccionPago transaccion = obtener(id);
        transaccion.enviarAConciliacion(codigo, reloj.instant(), conciliacion.margenInicial());
        return transaccion;
    }

    @Transactional
    public TransaccionPago reprogramarConsulta(Long id, String codigo) {
        TransaccionPago transaccion = obtener(id);
        transaccion.programarSiguienteConsulta(codigo, reloj.instant(),
                conciliacion.esperaBase(), conciliacion.esperaMaxima());
        return transaccion;
    }

    @Transactional
    public TransaccionPago escalarARevisionManual(Long id, MotivoResolucion motivo) {
        TransaccionPago transaccion = obtener(id);
        transaccion.escalarARevisionManual(motivo, reloj.instant());
        return transaccion;
    }

    /**
     * Toma la transaccion para un intento de conciliacion. El flush incrementa la
     * version: si otro proceso la tomo al mismo tiempo, uno de los dos recibe
     * OptimisticLockingFailureException y la deja pasar.
     */
    @Transactional
    public Optional<TransaccionPago> reclamarParaConciliar(Long id) {
        Instant ahora = reloj.instant();
        Optional<TransaccionPago> transaccion = transacciones.findById(id)
                .filter(t -> t.listaParaConciliar(ahora));
        transaccion.ifPresent(t -> {
            t.registrarIntentoConsulta(ahora, conciliacion.arrendamiento());
            transacciones.saveAndFlush(t);
        });
        return transaccion;
    }

    @Transactional(readOnly = true)
    public List<Long> pendientesDeConciliar() {
        return transacciones.idsListosParaConciliar(EstadoTransaccion.conciliables(), reloj.instant(),
                PageRequest.of(0, conciliacion.tamanoLote()));
    }

    private TransaccionPago obtener(Long id) {
        return transacciones.findById(id)
                .orElseThrow(() -> new IllegalStateException("No existe la transaccion " + id));
    }
}
