package com.utng.pagosservicios.proveedor;

import java.time.Instant;

/**
 * Registro de cada peticion y respuesta intercambiada con el proveedor
 * (PuntoRed exige conservarlo para aclaraciones).
 *
 * <p>Contrato: registrar nunca lanza excepciones ni bloquea el flujo de cobro;
 * si la bitacora falla, el pago sigue su curso y el fallo queda en el log.
 * Quien llama es responsable de entregar el intercambio ya sin secretos.
 */
public interface BitacoraIntercambios {

    void registrar(Intercambio intercambio);

    /** Bitacora que no guarda nada (pruebas unitarias). */
    static BitacoraIntercambios ninguna() {
        return intercambio -> {
        };
    }

    enum Operacion { AUTENTICACION, ENVIO, CONSULTA, CATALOGO }

    /**
     * @param solicitud datos enviados, ya enmascarados (nunca credenciales ni el campo cifrado)
     * @param respuesta cuerpo recibido, ya enmascarado y recortado
     * @param error     clasificacion del fallo de red si no hubo respuesta
     */
    record Intercambio(Operacion operacion, String referenciaExterna, int intento, String solicitud,
                       Integer statusHttp, String codigo, String respuesta, String error,
                       long duracionMs, Instant iniciadoEn, String traceId) {
    }
}
