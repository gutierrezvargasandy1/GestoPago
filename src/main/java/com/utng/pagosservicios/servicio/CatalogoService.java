package com.utng.pagosservicios.servicio;

import com.utng.pagosservicios.config.PagosProperties;
import com.utng.pagosservicios.dominio.catalogo.OrigenCatalogo;
import com.utng.pagosservicios.dominio.catalogo.Producto;
import com.utng.pagosservicios.dominio.catalogo.Servicio;
import com.utng.pagosservicios.repositorio.ProductoRepository;
import com.utng.pagosservicios.repositorio.ServicioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Catalogo que ven los clientes. Se lee siempre de la copia local: el proveedor
 * prohibe usar getProductList como fuente en linea.
 */
@Service
public class CatalogoService {

    private final ServicioRepository servicios;
    private final ProductoRepository productos;
    private final boolean permitirEjemplo;

    public CatalogoService(ServicioRepository servicios, ProductoRepository productos, PagosProperties propiedades) {
        this.servicios = servicios;
        this.productos = productos;
        this.permitirEjemplo = propiedades.catalogo().permitirEjemplo();
    }

    @Transactional(readOnly = true)
    public List<Servicio> serviciosDisponibles() {
        return servicios.listarActivos().stream()
                .filter(servicio -> permitirEjemplo || servicio.getOrigen() == OrigenCatalogo.PROVEEDOR)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Producto> productosDisponibles(String idServicio) {
        return productos.listarDisponibles(idServicio).stream()
                .filter(Producto::disponible)
                .filter(producto -> permitirEjemplo || !producto.esDeEjemplo())
                .toList();
    }
}
