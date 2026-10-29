package com.utng.pagosservicios.servicio;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Ejecuta la conciliacion periodicamente. Se desactiva con
 * pagos.conciliacion.habilitada=false; en las pruebas se invoca
 * {@link ConciliacionJob#ejecutarLote()} directamente, de forma controlada.
 */
@Component
@ConditionalOnProperty(prefix = "pagos.conciliacion", name = "habilitada", havingValue = "true",
        matchIfMissing = true)
public class ConciliacionProgramada {

    private final ConciliacionJob job;

    public ConciliacionProgramada(ConciliacionJob job) {
        this.job = job;
    }

    @Scheduled(fixedDelayString = "${pagos.conciliacion.intervalo}",
               initialDelayString = "${pagos.conciliacion.intervalo}")
    public void ejecutar() {
        job.ejecutarLote();
    }
}
