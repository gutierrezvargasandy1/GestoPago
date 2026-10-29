package com.utng.pagosservicios.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.utng.pagosservicios.dominio.catalogo.Producto;

import java.math.BigDecimal;

/**
 * @param modalidad RECARGA (la referencia es el telefono) o PAGO_SERVICIO (la
 *                  referencia es el numero del recibo)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProductoResponse(String idServicio, String idProducto, String nombre, String modalidad,
                               String tipoMonto, String montoFijo, String montoMinimo, String montoMaximo,
                               String multiplo, String comision, Integer longitudReferencia, String leyenda) {

    public static ProductoResponse de(Producto producto) {
        return new ProductoResponse(
                producto.getServicio().getIdServicio(),
                producto.getIdProducto(),
                producto.getNombre(),
                producto.getModalidad().name(),
                producto.getTipoMonto().name(),
                texto(producto.getMontoFijo()),
                texto(producto.getMontoMinimo()),
                texto(producto.getMontoMaximo()),
                texto(producto.getMultiplo()),
                texto(producto.getComision()),
                producto.getLongitudReferencia(),
                producto.getLeyenda());
    }

    private static String texto(BigDecimal valor) {
        return valor == null ? null : valor.toPlainString();
    }
}
