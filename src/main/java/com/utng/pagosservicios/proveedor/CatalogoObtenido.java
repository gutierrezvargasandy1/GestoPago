package com.utng.pagosservicios.proveedor;

import java.util.List;

/**
 * Resultado de pedir el catalogo. Un fallo nunca se confunde con un catalogo
 * vacio: con un catalogo vacio la sincronizacion desactivaria todos los productos.
 */
public record CatalogoObtenido(boolean exitoso, String detalle, List<ProductoProveedor> productos) {

    public CatalogoObtenido {
        productos = productos == null ? List.of() : List.copyOf(productos);
    }

    public static CatalogoObtenido exitoso(List<ProductoProveedor> productos) {
        return new CatalogoObtenido(true, null, productos);
    }

    public static CatalogoObtenido fallido(String detalle) {
        return new CatalogoObtenido(false, detalle, List.of());
    }
}
