package com.utng.pagosservicios.servicio;

import com.utng.pagosservicios.config.PagosProperties;
import com.utng.pagosservicios.dominio.catalogo.Producto;
import com.utng.pagosservicios.dominio.pago.SolicitudPago;
import com.utng.pagosservicios.repositorio.ProductoRepository;
import com.utng.pagosservicios.servicio.excepcion.CodigoError;
import com.utng.pagosservicios.servicio.excepcion.ReglaNegocioException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Valida servicio, producto, monto y referencia contra el catalogo. No toca
 * saldos ni bloqueos: corre antes de abrir la transaccion que mueve dinero, para
 * que una solicitud invalida nunca llegue a bloquear la cuenta.
 */
@Service
public class ValidadorPago {

    private final ProductoRepository productos;
    private final boolean permitirEjemplo;

    public ValidadorPago(ProductoRepository productos, PagosProperties propiedades) {
        this.productos = productos;
        this.permitirEjemplo = propiedades.catalogo().permitirEjemplo();
    }

    @Transactional(readOnly = true)
    public Producto validar(SolicitudPago solicitud) {
        Producto producto = productos.buscarEnCatalogo(solicitud.idServicio(), solicitud.idProducto())
                .filter(Producto::disponible)
                // El catalogo de ejemplo usa identificadores que el proveedor real no conoce.
                .filter(candidato -> permitirEjemplo || !candidato.esDeEjemplo())
                .orElseThrow(() -> new ReglaNegocioException(CodigoError.PRODUCTO_NO_DISPONIBLE,
                        "El producto no existe en el catálogo o no está disponible."));

        switch (producto.validarMonto(solicitud.monto())) {
            case VALIDO -> {
                // continua
            }
            case NO_COINCIDE_CON_MONTO_FIJO -> throw montoNoPermitido(
                    "El producto tiene un monto fijo de " + producto.getMontoFijo().toPlainString() + ".");
            case FUERA_DE_RANGO -> throw montoNoPermitido(
                    "El monto debe estar entre " + producto.getMontoMinimo().toPlainString()
                            + " y " + producto.getMontoMaximo().toPlainString() + ".");
            case NO_RESPETA_MULTIPLO -> throw montoNoPermitido(
                    "El monto debe ser múltiplo de " + producto.getMultiplo().toPlainString() + ".");
            case DEMASIADOS_DECIMALES -> throw montoNoPermitido("El monto admite como máximo dos decimales.");
        }

        if (!producto.referenciaValida(solicitud.referencia())) {
            throw new ReglaNegocioException(CodigoError.REFERENCIA_INVALIDA,
                    "La referencia no tiene el formato esperado para este servicio.");
        }
        return producto;
    }

    private static ReglaNegocioException montoNoPermitido(String detalle) {
        return new ReglaNegocioException(CodigoError.MONTO_NO_PERMITIDO, detalle);
    }
}
