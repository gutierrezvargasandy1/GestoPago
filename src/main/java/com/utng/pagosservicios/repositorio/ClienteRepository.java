package com.utng.pagosservicios.repositorio;

import com.utng.pagosservicios.dominio.cliente.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    Optional<Cliente> findByUsuario(String usuario);

    boolean existsByUsuario(String usuario);
}