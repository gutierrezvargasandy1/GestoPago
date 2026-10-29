package com.utng.pagosservicios.repositorio;

import com.utng.pagosservicios.dominio.cuenta.MovimientoCuenta;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovimientoCuentaRepository extends JpaRepository<MovimientoCuenta, Long> {
}
