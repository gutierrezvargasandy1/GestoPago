package com.utng.pagosservicios.servicio;

import com.utng.pagosservicios.util.DescripcionError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Procesa un lote de transacciones pendientes de conciliar. La ejecucion
 * periodica la hace {@link ConciliacionProgramada}; las pruebas llaman a
 * {@link #ejecutarLote()} directamente.
 */
@Component
public class ConciliacionJob {

    private static final Logger log = LoggerFactory.getLogger(ConciliacionJob.class);

    private final TransaccionPagoService transacciones;
    private final ConciliacionService conciliacion;

    public ConciliacionJob(TransaccionPagoService transacciones, ConciliacionService conciliacion) {
        this.transacciones = transacciones;
        this.conciliacion = conciliacion;
    }

    /** Procesa un lote de transacciones pendientes y devuelve cuantas reviso. */
    public int ejecutarLote() {
        List<Long> pendientes = transacciones.pendientesDeConciliar();
        if (!pendientes.isEmpty()) {
            log.info("Conciliando {} transacciones pendientes", pendientes.size());
        }
        for (Long id : pendientes) {
            try {
                conciliacion.conciliar(id);
            } catch (RuntimeException e) {
                // Un error en una transaccion no detiene el lote; se reintentara al vencer su arrendamiento.
                log.error("Fallo la conciliacion de la transaccion {}: {}", id, DescripcionError.de(e));
                log.debug("Detalle del fallo de conciliacion", e);
            }
        }
        return pendientes.size();
    }
}
