package com.utng.pagosservicios.servicio;

import com.utng.pagosservicios.config.PagosProperties;
import com.utng.pagosservicios.dominio.catalogo.CondicionesVenta;
import com.utng.pagosservicios.dominio.catalogo.Modalidad;
import com.utng.pagosservicios.dominio.catalogo.OrigenCatalogo;
import com.utng.pagosservicios.dominio.catalogo.Producto;
import com.utng.pagosservicios.dominio.catalogo.Servicio;
import com.utng.pagosservicios.proveedor.ProductoProveedor;
import com.utng.pagosservicios.repositorio.ProductoRepository;
import com.utng.pagosservicios.repositorio.ServicioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Aplica en la base el catalogo que publico el proveedor, en UNA transaccion:
 * o queda el catalogo nuevo completo o queda el anterior, nunca una mezcla.
 *
 * <p>Solo toca registros de origen PROVEEDOR; el catalogo de ejemplo no se
 * modifica. Lo que el proveedor dejo de publicar se desactiva, no se borra:
 * hay transacciones que lo referencian.
 */
@Service
public class CatalogoActualizador {

    /** Telefonos de Mexico: 10 digitos. */
    static final String PATRON_TELEFONO = "^[0-9]{10}$";
    /** Referencia de recibo: alfanumerica, hasta el tamano de la columna. */
    static final String PATRON_REFERENCIA_SERVICIO = "^[A-Za-z0-9]{1,40}$";

    private final ServicioRepository servicios;
    private final ProductoRepository productos;
    private final PagosProperties.Catalogo reglas;

    public CatalogoActualizador(ServicioRepository servicios, ProductoRepository productos,
                                PagosProperties propiedades) {
        this.servicios = servicios;
        this.productos = productos;
        this.reglas = propiedades.catalogo();
    }

    /** Devuelve cuantos productos quedaron activos. */
    @Transactional
    public int aplicar(List<ProductoProveedor> publicados, Instant ahora) {
        Map<Integer, Servicio> serviciosPorId = new HashMap<>();
        for (Servicio servicio : servicios.findByOrigen(OrigenCatalogo.PROVEEDOR)) {
            serviciosPorId.put(servicio.getIdServicioProveedor(), servicio);
        }
        Map<String, Producto> productosPorId = new HashMap<>();
        for (Producto producto : productos.buscarPorOrigen(OrigenCatalogo.PROVEEDOR)) {
            productosPorId.put(llave(producto.getServicio().getIdServicioProveedor(), producto.getIdProductoProveedor()),
                    producto);
        }

        Set<Integer> serviciosVigentes = new HashSet<>();
        Set<String> productosVigentes = new HashSet<>();
        for (ProductoProveedor publicado : publicados) {
            Servicio servicio = serviciosPorId.get(publicado.idServicio());
            if (servicio == null) {
                servicio = servicios.save(Servicio.desdeProveedor(publicado.idServicio(), publicado.servicio(),
                        publicado.categoria(), ahora));
                serviciosPorId.put(publicado.idServicio(), servicio);
            } else if (serviciosVigentes.add(publicado.idServicio())) {
                servicio.actualizarDesdeProveedor(publicado.servicio(), publicado.categoria(), ahora);
            }
            serviciosVigentes.add(publicado.idServicio());

            String llave = llave(publicado.idServicio(), publicado.idProducto());
            CondicionesVenta condiciones = condiciones(publicado);
            Producto producto = productosPorId.get(llave);
            if (producto == null) {
                producto = productos.save(Producto.desdeProveedor(servicio, publicado.idProducto(),
                        publicado.producto(), condiciones, publicado.leyenda(), ahora));
                productosPorId.put(llave, producto);
            } else {
                producto.actualizarDesdeProveedor(publicado.producto(), condiciones, publicado.leyenda(), ahora);
            }
            productosVigentes.add(llave);
        }

        productosPorId.forEach((llave, producto) -> {
            if (!productosVigentes.contains(llave)) {
                producto.desactivar();
            }
        });
        serviciosPorId.forEach((id, servicio) -> {
            if (!serviciosVigentes.contains(id)) {
                servicio.desactivar();
            }
        });
        return productosVigentes.size();
    }

    /**
     * Con precio: monto fijo (el cliente no elige el monto). Sin precio: monto
     * abierto dentro del rango configurado. La referencia de una recarga es un
     * telefono; la de un servicio, el numero del recibo.
     */
    CondicionesVenta condiciones(ProductoProveedor publicado) {
        boolean conPrecio = publicado.precio().signum() > 0;
        // Una recarga sin precio no se podria pedir (en sendTx no viaja monto para ella).
        Modalidad modalidad = conPrecio ? publicado.modalidad() : Modalidad.PAGO_SERVICIO;
        boolean recarga = modalidad == Modalidad.RECARGA;
        Integer longitud = recarga ? 10 : null;
        String patron = recarga ? PATRON_TELEFONO : PATRON_REFERENCIA_SERVICIO;
        if (conPrecio) {
            return CondicionesVenta.montoFijo(modalidad, publicado.precio(), longitud, patron);
        }
        return CondicionesVenta.montoAbierto(modalidad, reglas.montoMinimoServicios(),
                reglas.montoMaximoServicios(), null, longitud, patron);
    }

    private static String llave(Integer idServicio, Integer idProducto) {
        return idServicio + ":" + idProducto;
    }
}
