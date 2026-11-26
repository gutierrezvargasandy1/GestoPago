package com.utng.pagosservicios.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.utng.pagosservicios.servicio.HistorialService.Compra;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Una compra del cliente. Los montos van como texto con dos decimales.
 * estado es el interno (CONFIRMADA, EN_CONCILIACION...) y grupo lo que ve el
 * cliente (EXITOSA, PENDIENTE o RECHAZADA). Folio y autorizacion solo vienen en
 * las exitosas.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CompraResponse(Long transaccionId, Instant fecha, Instant creadaEn, String servicio, String producto,
                             String referencia, String monto, String comision, String montoTotal,
                             String estado, String grupo, String motivo,
                             String folioProveedor, String autorizacion, String pin, String instrucciones) {

    public static CompraResponse de(Compra compra) {
        return new CompraResponse(compra.transaccionId(), compra.fecha(), compra.creadaEn(), compra.servicio(),
                compra.producto(), compra.referencia(), texto(compra.monto()), texto(compra.comision()),
                texto(compra.montoTotal()), compra.estado(), compra.grupo(), compra.motivo(),
                compra.folioProveedor(), compra.autorizacion(), compra.pin(), compra.instrucciones());
    }

    private static String texto(BigDecimal valor) {
        return valor == null ? null : valor.toPlainString();
    }
}
