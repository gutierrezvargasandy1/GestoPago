package com.utng.pagosservicios.dominio.pago;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Maquina de estados del pago.
 *
 * <pre>
 *   DEBITADA ──► ENVIADA ──┬──► CONFIRMADA
 *      │                   ├──► REVERSADA            (rechazo, o no procesada)
 *      │                   └──► EN_CONCILIACION ──┬──► CONFIRMADA
 *      │                                          ├──► REVERSADA
 *      │                                          └──► REVISION_MANUAL ──► CONFIRMADA | REVERSADA
 *      └──► REVERSADA                              (nunca se envio)
 * </pre>
 *
 * Las transiciones se declaran explicitamente para que un error no pueda, por
 * ejemplo, devolver el dinero de un pago que el proveedor ya confirmo.
 */
public enum EstadoTransaccion {

    /** El dinero salio de la cuenta y la operacion aun no se envia. Es seguro reversar. */
    DEBITADA,

    /** Se va a enviar o ya se envio al proveedor. El resultado es desconocido hasta resolverse. */
    ENVIADA,

    /**
     * No se pudo determinar si el proveedor proceso la operacion (timeout, 5xx,
     * respuesta ilegible). El dinero sigue retenido. NO equivale a "fallo".
     */
    EN_CONCILIACION,

    /** La conciliacion automatica no pudo resolverla; el dinero sigue retenido. */
    REVISION_MANUAL,

    /** El proveedor confirmo el pago. */
    CONFIRMADA,

    /** El dinero se devolvio a la cuenta. */
    REVERSADA;

    private static final Set<EstadoTransaccion> CONCILIABLES =
            Collections.unmodifiableSet(EnumSet.of(DEBITADA, ENVIADA, EN_CONCILIACION));

    /** Estados que el job de conciliacion puede resolver sin intervencion humana. */
    public static Set<EstadoTransaccion> conciliables() {
        return CONCILIABLES;
    }

    public boolean esTerminal() {
        return this == CONFIRMADA || this == REVERSADA;
    }

    /** La peticion original todavia esta trabajando con esta transaccion. */
    public boolean enCurso() {
        return this == DEBITADA || this == ENVIADA;
    }

    public boolean conciliableAutomaticamente() {
        return CONCILIABLES.contains(this);
    }

    public boolean puedeTransicionarA(EstadoTransaccion destino) {
        return switch (this) {
            case DEBITADA -> destino == ENVIADA || destino == REVERSADA;
            case ENVIADA -> destino == CONFIRMADA || destino == REVERSADA
                    || destino == EN_CONCILIACION || destino == REVISION_MANUAL;
            case EN_CONCILIACION -> destino == CONFIRMADA || destino == REVERSADA || destino == REVISION_MANUAL;
            case REVISION_MANUAL -> destino == CONFIRMADA || destino == REVERSADA;
            case CONFIRMADA, REVERSADA -> false;
        };
    }
}
