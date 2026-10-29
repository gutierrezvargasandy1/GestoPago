package com.utng.pagosservicios.repositorio;

import com.utng.pagosservicios.dominio.pago.EstadoTransaccion;
import com.utng.pagosservicios.dominio.pago.TransaccionPago;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TransaccionPagoRepository extends JpaRepository<TransaccionPago, Long> {

    /**
     * Camino rapido de idempotencia. NO es la garantia contra el cargo duplicado:
     * entre este SELECT y el INSERT puede colarse otra peticion. La garantia es
     * el UNIQUE (cliente_id, clave_idempotencia) de la base de datos.
     */
    Optional<TransaccionPago> findByClienteIdAndClaveIdempotencia(Long clienteId, String claveIdempotencia);

    Optional<TransaccionPago> findByIdAndClienteId(Long id, Long clienteId);

    /**
     * Monto comprometido hoy por la cuenta: todo lo que no termino reversado
     * (confirmado o aun pendiente). Devuelve null si no hay operaciones.
     */
    @Query("""
            SELECT SUM(t.montoTotal) FROM TransaccionPago t
            WHERE t.cuentaId = :cuentaId
              AND t.creadoEn >= :desde
              AND t.estado <> :excluido
            """)
    BigDecimal sumarComprometidoDesde(@Param("cuentaId") Long cuentaId,
                                      @Param("desde") Instant desde,
                                      @Param("excluido") EstadoTransaccion excluido);

    @Query("""
            SELECT t.id FROM TransaccionPago t
            WHERE t.estado IN :estados
              AND t.proximoIntentoEn <= :ahora
            ORDER BY t.proximoIntentoEn
            """)
    List<Long> idsListosParaConciliar(@Param("estados") Collection<EstadoTransaccion> estados,
                                      @Param("ahora") Instant ahora,
                                      Pageable pagina);
}
