package com.utng.pagosservicios.proveedor;

import com.utng.pagosservicios.dominio.catalogo.Modalidad;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Producto tal como lo publica el proveedor, ya traducido a conceptos propios
 * (la modalidad la decide el adaptador, que es quien entiende los campos del
 * proveedor).
 *
 * @param precio precio del producto; cero si el monto lo captura el cliente
 */
public record ProductoProveedor(int idServicio, String servicio, String categoria,
                                int idProducto, String producto, Modalidad modalidad,
                                BigDecimal precio, String leyenda) {

    public ProductoProveedor {
        Objects.requireNonNull(modalidad, "modalidad");
        precio = precio == null ? BigDecimal.ZERO : precio;
    }
}
