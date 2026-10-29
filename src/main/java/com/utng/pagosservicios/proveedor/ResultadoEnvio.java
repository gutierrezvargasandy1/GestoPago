package com.utng.pagosservicios.proveedor;

/** Desenlace de enviar una operacion al proveedor. */
public record ResultadoEnvio(Desenlace desenlace, String codigo, String folio, String autorizacion,
                             DatosCanje canje) {

    public enum Desenlace {
        /** El proveedor confirmo el pago. */
        APROBADA,
        /** El proveedor evaluo la operacion y la rechazo: no la proceso. */
        RECHAZADA,
        /**
         * El proveedor la rechazo por ser identica a otra aplicada hace poco
         * (mismo servicio, referencia y monto). Esta no se aplico.
         */
        DUPLICADA,
        /**
         * Hay certeza de que el proveedor no la proceso (nunca se conecto,
         * circuito abierto, 4xx). Es seguro devolver el dinero.
         */
        NO_PROCESADA,
        /**
         * No hay forma de saber si la proceso (timeout de lectura, 5xx, conexion
         * cortada, respuesta ilegible, codigo desconocido o el 82 de PuntoRed).
         * NO es seguro devolver el dinero ni reintentar: se concilia.
         */
        INDETERMINADA
    }

    public static ResultadoEnvio aprobada(String codigo, String folio, String autorizacion) {
        return aprobada(codigo, folio, autorizacion, null);
    }

    public static ResultadoEnvio aprobada(String codigo, String folio, String autorizacion, DatosCanje canje) {
        return new ResultadoEnvio(Desenlace.APROBADA, codigo, folio, autorizacion, canje);
    }

    public static ResultadoEnvio rechazada(String codigo) {
        return new ResultadoEnvio(Desenlace.RECHAZADA, codigo, null, null, null);
    }

    public static ResultadoEnvio duplicada(String codigo) {
        return new ResultadoEnvio(Desenlace.DUPLICADA, codigo, null, null, null);
    }

    public static ResultadoEnvio noProcesada(String detalle) {
        return new ResultadoEnvio(Desenlace.NO_PROCESADA, detalle, null, null, null);
    }

    public static ResultadoEnvio indeterminada(String detalle) {
        return new ResultadoEnvio(Desenlace.INDETERMINADA, detalle, null, null, null);
    }
}
