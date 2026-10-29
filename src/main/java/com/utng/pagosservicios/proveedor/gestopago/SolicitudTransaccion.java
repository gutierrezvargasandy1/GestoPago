package com.utng.pagosservicios.proveedor.gestopago;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.utng.pagosservicios.proveedor.OrdenPago;
import com.utng.pagosservicios.util.Enmascarar;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * JSON que se cifra y viaja en el campo "signed" de sendTx (y de confirmTx).
 *
 * <p>Es el UNICO lugar que conoce los nombres de campo del proveedor:
 * <ul>
 *   <li>Recarga (tiempo aire, PIN): idProducto, idServicio, telefono, horaLocal, upc, unidad.</li>
 *   <li>Pago de servicio: ademas referencia y montoPago; el telefono no aplica.</li>
 * </ul>
 * idDistribuidor y codigoDispositivo no viajan aqui: ya van en el token.
 *
 * <p>montoPago es un BigDecimal con dos decimales: Jackson lo escribe como
 * numero (250.50) sin pasar por double, que podria convertir 0.1 en 0.1000000001.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SolicitudTransaccion(Integer idProducto, Integer idServicio, String telefono, String horaLocal,
                                   String referencia, BigDecimal montoPago, String upc, String unidad) {

    /** Formato documentado de horaLocal, por ejemplo 20101109131526. */
    static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public static SolicitudTransaccion de(OrdenPago orden, String unidad, ZoneId zona) {
        Integer idProducto = Objects.requireNonNull(orden.idProducto(), "El producto no tiene identificador del proveedor");
        Integer idServicio = Objects.requireNonNull(orden.idServicio(), "El servicio no tiene identificador del proveedor");
        String horaLocal = FORMATO_HORA.format(orden.momento().atZone(zona));
        return switch (orden.modalidad()) {
            case RECARGA -> new SolicitudTransaccion(idProducto, idServicio, orden.referencia(), horaLocal,
                    null, null, orden.referenciaExterna(), unidad);
            case PAGO_SERVICIO -> new SolicitudTransaccion(idProducto, idServicio, null, horaLocal,
                    orden.referencia(), orden.monto().setScale(2, RoundingMode.UNNECESSARY),
                    orden.referenciaExterna(), unidad);
        };
    }

    /** Copia para la bitacora: telefono y referencia enmascarados (la completa vive en transaccion_pago). */
    SolicitudTransaccion enmascarada() {
        return new SolicitudTransaccion(idProducto, idServicio, enmascarar(telefono), horaLocal,
                enmascarar(referencia), montoPago, upc, unidad);
    }

    private static String enmascarar(String valor) {
        return valor == null ? null : Enmascarar.ultimos(valor);
    }

    @Override
    public String toString() {
        return "SolicitudTransaccion[upc=" + upc + ", idServicio=" + idServicio + ", idProducto=" + idProducto
                + ", telefono=" + enmascarar(telefono) + ", referencia=" + enmascarar(referencia)
                + ", montoPago=" + montoPago + ", horaLocal=" + horaLocal + "]";
    }
}
