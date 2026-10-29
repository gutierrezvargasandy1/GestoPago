package com.utng.pagosservicios.proveedor;

import com.utng.pagosservicios.dominio.catalogo.Modalidad;
import com.utng.pagosservicios.dominio.catalogo.Producto;
import com.utng.pagosservicios.dominio.pago.TransaccionPago;
import com.utng.pagosservicios.util.Enmascarar;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Lo que se le pide al proveedor. Lleva el monto del servicio, no la comision
 * propia, y los identificadores numericos con los que el proveedor conoce el
 * producto.
 *
 * <p>Se construye siempre a partir de lo guardado en la transaccion, de modo
 * que la conciliacion puede reconstruir exactamente la misma orden (mismo UPC,
 * misma hora) para preguntar por ella.
 *
 * @param referenciaExterna UPC de la operacion
 * @param momento           hora de envio; viaja como horaLocal
 */
public record OrdenPago(String referenciaExterna, Integer idServicio, Integer idProducto, Modalidad modalidad,
                        String referencia, BigDecimal monto, Instant momento) {

    public OrdenPago {
        Objects.requireNonNull(referenciaExterna, "referenciaExterna");
        Objects.requireNonNull(modalidad, "modalidad");
        Objects.requireNonNull(referencia, "referencia");
        Objects.requireNonNull(monto, "monto");
        Objects.requireNonNull(momento, "momento");
    }

    public static OrdenPago de(TransaccionPago transaccion, Producto producto) {
        Instant momento = transaccion.getEnviadoEn() != null ? transaccion.getEnviadoEn() : transaccion.getCreadoEn();
        return new OrdenPago(
                transaccion.getReferenciaExterna(),
                producto.getServicio().getIdServicioProveedor(),
                producto.getIdProductoProveedor(),
                producto.getModalidad(),
                transaccion.getReferenciaServicio(),
                transaccion.getMonto(),
                momento);
    }

    /** La referencia (telefono, numero de recibo) no se imprime completa. */
    @Override
    public String toString() {
        return "OrdenPago[upc=" + referenciaExterna + ", idServicio=" + idServicio + ", idProducto=" + idProducto
                + ", modalidad=" + modalidad + ", referencia=" + Enmascarar.ultimos(referencia)
                + ", monto=" + monto + ", momento=" + momento + "]";
    }
}
