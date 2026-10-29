package com.utng.pagosservicios.repositorio;

import com.utng.pagosservicios.dominio.cuenta.Cuenta;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CuentaRepository extends JpaRepository<Cuenta, Long> {

    /**
     * Carga la cuenta con un bloqueo exclusivo (SELECT ... FOR UPDATE) y solo si
     * pertenece al cliente indicado.
     *
     * <p>Sin el bloqueo, dos peticiones simultaneas leen el mismo saldo de 100,
     * ambas concluyen que alcanza para pagar 100 y ambas debitan. Con el bloqueo,
     * la segunda espera a que la primera confirme y lee el saldo ya descontado.
     *
     * <p>Filtrar por cliente en la misma consulta evita que alguien bloquee
     * cuentas ajenas solo con conocer su numero.
     */

        boolean existsByNumero(String numero);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("SELECT c FROM Cuenta c WHERE c.numero = :numero AND c.clienteId = :clienteId")
    Optional<Cuenta> bloquearPorNumeroYCliente(@Param("numero") String numero,
                                               @Param("clienteId") Long clienteId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("SELECT c FROM Cuenta c WHERE c.id = :id")
    Optional<Cuenta> bloquearPorId(@Param("id") Long id);
}
