package com.utng.pagosservicios.proveedor;

/**
 * Puerto para obtener el catalogo de productos del proveedor. Separado de
 * {@link ProveedorPagos} porque tiene otro ritmo (una vez al dia) y sus fallos
 * no deben afectar el cobro (no comparte el circuit breaker de los pagos).
 */
public interface CatalogoProveedor {

    CatalogoObtenido obtenerCatalogo();
}
