package com.utng.pagosservicios.repositorio;

import com.utng.pagosservicios.dominio.catalogo.OrigenCatalogo;
import com.utng.pagosservicios.dominio.catalogo.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductoRepository extends JpaRepository<Producto, Long> {

    /** El JOIN FETCH trae el servicio en la misma consulta: se necesita para saber si esta activo. */
    @Query("""
            SELECT p FROM Producto p
            JOIN FETCH p.servicio s
            WHERE s.idServicio = :idServicio
              AND p.idProducto = :idProducto
            """)
    Optional<Producto> buscarEnCatalogo(@Param("idServicio") String idServicio,
                                        @Param("idProducto") String idProducto);

    @Query("""
            SELECT p FROM Producto p
            JOIN FETCH p.servicio s
            WHERE s.idServicio = :idServicio
              AND p.activo = true
              AND s.activo = true
            ORDER BY p.idProducto
            """)
    List<Producto> listarDisponibles(@Param("idServicio") String idServicio);

    /**
     * La conciliacion corre fuera de una transaccion: el servicio debe venir
     * cargado para reconstruir la orden (identificadores del proveedor).
     */
    @Query("SELECT p FROM Producto p JOIN FETCH p.servicio WHERE p.id = :id")
    Optional<Producto> buscarConServicio(@Param("id") Long id);

    @Query("SELECT p FROM Producto p JOIN FETCH p.servicio WHERE p.origen = :origen")
    List<Producto> buscarPorOrigen(@Param("origen") OrigenCatalogo origen);
}
