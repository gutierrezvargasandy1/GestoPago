package com.utng.pagosservicios.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.utng.pagosservicios.servicio.HistorialService.Compra;

import java.math.BigDecimal;
import java.time.Instant;

/** Una compra confirmada. Los montos van como texto con dos decimales. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CompraResponse(Long transaccionId, Instant fecha, String servicio, String producto,
                             String referencia, String monto, String comision, String montoTotal,
                             String folioProveedor, String autorizacion, String pin, String instrucciones) {

    public static CompraResponse de(Compra compra) {
        return new CompraResponse(compra.transaccionId(), compra.fecha(), compra.servicio(), compra.producto(),
                compra.referencia(), texto(compra.monto()), texto(compra.comision()), texto(compra.montoTotal()),
                compra.folioProveedor(), compra.autorizacion(), compra.pin(), compra.instrucciones());
    }

    private static String texto(BigDecimal valor) {
        return valor == null ? null : valor.toPlainString();
    }
}
