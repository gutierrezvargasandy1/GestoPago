package com.utng.pagosservicios.servicio;

import com.utng.pagosservicios.util.DescripcionError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Dispara la sincronizacion del catalogo al arrancar (si toca) y una vez al dia.
 * Se desactiva con pagos.catalogo.sincronizacion-habilitada=false (pruebas y
 * simulador, que usan el catalogo de ejemplo).
 *
 * <p>Ningun fallo sale de aqui: una excepcion en ApplicationReadyEvent cerraria
 * la aplicacion, y un catalogo que no se pudo actualizar no es razon para
 * dejar de cobrar con el que ya existe.
 */
@Component
@ConditionalOnProperty(prefix = "pagos.catalogo", name = "sincronizacion-habilitada", havingValue = "true",
        matchIfMissing = true)
public class CatalogoProgramado {

    private static final Logger log = LoggerFactory.getLogger(CatalogoProgramado.class);

    private final SincronizacionCatalogoService sincronizacion;

    public CatalogoProgramado(SincronizacionCatalogoService sincronizacion) {
        this.sincronizacion = sincronizacion;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void alArrancar() {
        ejecutar();
    }

    @Scheduled(cron = "${pagos.catalogo.cron}", zone = "${pagos.zona-horaria}")
    public void diariamente() {
        ejecutar();
    }

    private void ejecutar() {
        try {
            SincronizacionCatalogoService.Resultado resultado = sincronizacion.sincronizarSiCorresponde();
            log.debug("Sincronizacion de catalogo: {}", resultado);
        } catch (RuntimeException e) {
            log.error("Fallo la sincronizacion del catalogo: {}", DescripcionError.de(e));
        }
    }
}
