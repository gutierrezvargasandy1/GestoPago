package com.utng.pagosservicios.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.utng.pagosservicios.dominio.pago.EstadoTransaccion;
import com.utng.pagosservicios.servicio.ResultadoPago;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Representacion de un pago. Los montos van como texto con dos decimales: un
 * cliente JavaScript que los recibiera como numero los convertiria a punto
 * flotante y podria mostrar 0.30000000000000004.
 *
 * <p>pin e instrucciones solo aparecen en pagos confirmados de tarjetas de
 * regalo; el proveedor exige entregarlos al cliente. El endpoint solo responde
 * al dueno del pago.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PagoResponse(Long transaccionId, String estado, String motivo,
                           String monto, String comision, String montoTotal,
                           String folioProveedor, String autorizacion, String pin, String instrucciones,
                           Instant creadoEn, Instant resueltoEn, String mensaje) {

    public static PagoResponse de(ResultadoPago resultado) {
        boolean confirmada = resultado.estado() == EstadoTransaccion.CONFIRMADA;
        return new PagoResponse(
                resultado.transaccionId(),
                resultado.estado().name(),
                resultado.motivo() == null ? null : resultado.motivo().name(),
                texto(resultado.monto()),
                texto(resultado.comision()),
                texto(resultado.montoTotal()),
                confirmada ? resultado.folioProveedor() : null,
                confirmada ? resultado.autorizacion() : null,
                confirmada ? resultado.pin() : null,
                confirmada ? resultado.instrucciones() : null,
                resultado.creadoEn(),
                resultado.resueltoEn(),
                mensaje(resultado));
    }

    private static String mensaje(ResultadoPago resultado) {
        return switch (resultado.estado()) {
            case CONFIRMADA -> "Pago confirmado por el proveedor.";
            case EN_CONCILIACION -> "El proveedor no confirmó a tiempo. El monto queda retenido mientras se "
                    + "verifica el resultado; no repita el pago con otra clave.";
            case REVISION_MANUAL -> "La operación requiere revisión manual. El monto permanece retenido hasta resolverla.";
            case REVERSADA -> "El pago no se realizó y el monto fue devuelto a la cuenta. "
                    + (resultado.motivo() == null ? "" : resultado.motivo().getDescripcion());
            case DEBITADA, ENVIADA -> "La operación está en proceso.";
        };
    }

    private static String texto(BigDecimal valor) {
        return valor == null ? null : valor.toPlainString();
    }

    @Override
    public String toString() {
        return "PagoResponse[transaccionId=" + transaccionId + ", estado=" + estado
                + ", pin=" + (pin == null ? "(sin PIN)" : "****") + "]";
    }
}
