package com.utng.pagosservicios.servicio;

import com.utng.pagosservicios.dominio.pago.EstadoTransaccion;
import com.utng.pagosservicios.dominio.pago.MotivoResolucion;
import com.utng.pagosservicios.dominio.pago.TransaccionPago;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Lo que la capa de servicio devuelve sobre un pago. Evita que la capa web
 * reciba entidades JPA.
 *
 * @param repetido true si la respuesta corresponde a una clave de idempotencia
 *                 ya usada: no se ejecuto nada nuevo.
 * @param pin      PIN de una tarjeta de regalo: lo que el cliente compro. Solo
 *                 llega al dueno del pago.
 */
public record ResultadoPago(Long transaccionId, EstadoTransaccion estado, MotivoResolucion motivo,
                            BigDecimal monto, BigDecimal comision, BigDecimal montoTotal,
                            String folioProveedor, String autorizacion, String pin, String instrucciones,
                            Instant creadoEn, Instant resueltoEn, boolean repetido) {

    public static ResultadoPago de(TransaccionPago tx, boolean repetido) {
        return new ResultadoPago(tx.getId(), tx.getEstado(), tx.getMotivo(),
                tx.getMonto(), tx.getComision(), tx.getMontoTotal(),
                tx.getFolioProveedor(), tx.getAutorizacionProveedor(),
                tx.getPinCanje(), tx.getInstruccionesCanje(),
                tx.getCreadoEn(), tx.getResueltoEn(), repetido);
    }

    @Override
    public String toString() {
        return "ResultadoPago[transaccionId=" + transaccionId + ", estado=" + estado + ", motivo=" + motivo
                + ", montoTotal=" + montoTotal + ", pin=" + (pin == null ? "(sin PIN)" : "****")
                + ", repetido=" + repetido + "]";
    }
}
