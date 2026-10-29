package com.utng.pagosservicios.servicio;

import com.utng.pagosservicios.config.PagosProperties;
import com.utng.pagosservicios.dominio.catalogo.Producto;
import com.utng.pagosservicios.dominio.pago.EstadoTransaccion;
import com.utng.pagosservicios.dominio.pago.MotivoResolucion;
import com.utng.pagosservicios.dominio.pago.TransaccionPago;
import com.utng.pagosservicios.proveedor.OrdenPago;
import com.utng.pagosservicios.proveedor.ProveedorPagos;
import com.utng.pagosservicios.proveedor.ResultadoConsulta;
import com.utng.pagosservicios.repositorio.ProductoRepository;
import com.utng.pagosservicios.util.DescripcionError;
import com.utng.pagosservicios.util.Trazabilidad;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Resuelve las transacciones cuyo resultado no se pudo determinar.
 *
 * <p>Regla central: una operacion financiera dudosa nunca se reenvia a ciegas.
 * Primero se le pregunta al proveedor que paso con ella (confirmTx, con el mismo
 * UPC y la misma hora del envio original) y solo con una respuesta concluyente
 * se confirma o se devuelve el dinero. Si no hay forma de saberlo, la decide
 * una persona.
 */
@Service
public class ConciliacionService {

    private static final Logger log = LoggerFactory.getLogger(ConciliacionService.class);

    private final TransaccionPagoService transacciones;
    private final FondosService fondos;
    private final ProveedorPagos proveedor;
    private final ProductoRepository productos;
    private final PagosProperties.Conciliacion configuracion;
    private final Clock reloj;

    public ConciliacionService(TransaccionPagoService transacciones, FondosService fondos, ProveedorPagos proveedor,
                               ProductoRepository productos, PagosProperties propiedades, Clock reloj) {
        this.transacciones = transacciones;
        this.fondos = fondos;
        this.proveedor = proveedor;
        this.productos = productos;
        this.configuracion = propiedades.conciliacion();
        this.reloj = reloj;
    }

    public void conciliar(Long transaccionId) {
        Optional<TransaccionPago> reclamada;
        try {
            reclamada = transacciones.reclamarParaConciliar(transaccionId);
        } catch (OptimisticLockingFailureException e) {
            log.debug("La transaccion {} la tomo otro proceso de conciliacion", transaccionId);
            return;
        }
        if (reclamada.isEmpty()) {
            return;
        }

        TransaccionPago transaccion = reclamada.get();
        String trazaAnterior = MDC.get(Trazabilidad.CLAVE_MDC);
        MDC.put(Trazabilidad.CLAVE_MDC, Objects.requireNonNullElse(transaccion.getTraceId(),
                "conciliacion-" + transaccion.getId()));
        try {
            resolver(transaccion);
        } finally {
            if (trazaAnterior == null) {
                MDC.remove(Trazabilidad.CLAVE_MDC);
            } else {
                MDC.put(Trazabilidad.CLAVE_MDC, trazaAnterior);
            }
        }
    }

    private void resolver(TransaccionPago transaccion) {
        Long id = transaccion.getId();

        // DEBITADA solo existe antes de enviar: hay certeza de que el proveedor no la recibio.
        if (transaccion.getEstado() == EstadoTransaccion.DEBITADA) {
            fondos.reversar(id, MotivoResolucion.NUNCA_ENVIADA, null);
            return;
        }

        // El proveedor solo responde confirmTx durante 24 h despues del envio.
        if (consultaVencida(transaccion)) {
            log.warn("La transaccion {} supero el plazo de consulta del proveedor: pasa a revision manual", id);
            transacciones.escalarARevisionManual(id, MotivoResolucion.CONSULTA_VENCIDA);
            return;
        }

        Optional<Producto> producto = productos.buscarConServicio(transaccion.getProductoId());
        if (producto.isEmpty()) {
            log.error("No se encontro el producto {} de la transaccion {}: pasa a revision manual",
                    transaccion.getProductoId(), id);
            transacciones.escalarARevisionManual(id, MotivoResolucion.CONSULTA_IMPOSIBLE);
            return;
        }

        ResultadoConsulta resultado = consultar(transaccion, producto.get());
        log.info("Conciliacion de la transaccion {} (intento {}): {} ({})",
                id, transaccion.getIntentosConsulta(), resultado.estado(), resultado.codigo());

        switch (resultado.estado()) {
            case APROBADA -> transacciones.confirmar(id, resultado.codigo(), resultado.folio(),
                    resultado.autorizacion(), resultado.canje());
            case RECHAZADA -> fondos.reversar(id, MotivoResolucion.RECHAZADA_POR_PROVEEDOR, resultado.codigo());
            case NO_ENCONTRADA -> {
                // Se exige que el "no aplicada" se repita: un unico "no la tengo" podria ser
                // un retraso del proveedor y reversar algo que si cobro seria una perdida.
                if (transaccion.getIntentosConsulta() >= configuracion.confirmacionesNoEncontrada()) {
                    fondos.reversar(id, MotivoResolucion.NO_ENCONTRADA_EN_PROVEEDOR, resultado.codigo());
                } else {
                    reprogramarOEscalar(transaccion, resultado.codigo());
                }
            }
            case INDETERMINADA -> reprogramarOEscalar(transaccion, resultado.codigo());
            case NO_SOPORTADA -> {
                log.warn("El proveedor no ofrece consulta de estatus: la transaccion {} pasa a revision manual", id);
                transacciones.escalarARevisionManual(id, MotivoResolucion.CONSULTA_NO_SOPORTADA);
            }
        }
    }

    private boolean consultaVencida(TransaccionPago transaccion) {
        Instant enviada = transaccion.getEnviadoEn();
        return enviada != null && !reloj.instant().isBefore(enviada.plus(configuracion.vigenciaConsulta()));
    }

    private void reprogramarOEscalar(TransaccionPago transaccion, String codigo) {
        if (transaccion.getIntentosConsulta() >= configuracion.maxIntentos()) {
            log.warn("La transaccion {} agoto {} intentos de conciliacion: pasa a revision manual",
                    transaccion.getId(), transaccion.getIntentosConsulta());
            transacciones.escalarARevisionManual(transaccion.getId(), MotivoResolucion.INTENTOS_AGOTADOS);
        } else {
            transacciones.reprogramarConsulta(transaccion.getId(), codigo);
        }
    }

    private ResultadoConsulta consultar(TransaccionPago transaccion, Producto producto) {
        try {
            return Objects.requireNonNullElse(proveedor.consultar(OrdenPago.de(transaccion, producto)),
                    ResultadoConsulta.indeterminada("SIN_RESPUESTA"));
        } catch (RuntimeException e) {
            log.error("Error inesperado al consultar la transaccion {}: {}", transaccion.getId(), DescripcionError.de(e));
            return ResultadoConsulta.indeterminada("ERROR_INESPERADO");
        }
    }
}
