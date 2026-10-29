package com.utng.pagosservicios.repositorio;

import com.utng.pagosservicios.dominio.catalogo.OrigenCatalogo;
import com.utng.pagosservicios.dominio.catalogo.Servicio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ServicioRepository extends JpaRepository<Servicio, Long> {

    List<Servicio> findByOrigen(OrigenCatalogo origen);

    @Query("SELECT s FROM Servicio s WHERE s.activo = true ORDER BY s.nombre")
    List<Servicio> listarActivos();
}
