package com.utng.pagosservicios.repositorio;

import com.utng.pagosservicios.proveedor.BitacoraIntercambios;
import com.utng.pagosservicios.util.DescripcionError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;

/**
 * Guarda la bitacora en la tabla bitacora_proveedor con JDBC directo: es una
 * insercion simple, sin entidad que mantener ni cache de Hibernate.
 *
 * <p>Se ejecuta fuera de las transacciones de negocio (el adaptador llama al
 * proveedor fuera de ellas), asi que cada registro se confirma al momento y
 * sobrevive aunque despues falle otra cosa.
 */
@Repository
public class BitacoraIntercambiosJdbc implements BitacoraIntercambios {

    private static final Logger log = LoggerFactory.getLogger(BitacoraIntercambiosJdbc.class);

    private static final String INSERTAR = """
            INSERT INTO bitacora_proveedor (operacion, referencia_externa, intento, solicitud, status_http, codigo,
                                            respuesta, error, duracion_ms, trace_id, creado_en)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbc;

    public BitacoraIntercambiosJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void registrar(Intercambio intercambio) {
        try {
            jdbc.update(INSERTAR,
                    intercambio.operacion().name(),
                    recortar(intercambio.referenciaExterna(), 40),
                    intercambio.intento(),
                    intercambio.solicitud(),
                    intercambio.statusHttp(),
                    recortar(intercambio.codigo(), 20),
                    intercambio.respuesta(),
                    recortar(intercambio.error(), 80),
                    (int) Math.min(Math.max(intercambio.duracionMs(), 0), Integer.MAX_VALUE),
                    recortar(intercambio.traceId(), 64),
                    Timestamp.from(intercambio.iniciadoEn()));
        } catch (RuntimeException e) {
            // La bitacora nunca debe tumbar un cobro: el pago sigue y el fallo queda en el log.
            log.warn("No se pudo registrar en la bitacora el intercambio {} ({}): {}",
                    intercambio.operacion(), intercambio.referenciaExterna(), DescripcionError.de(e));
        }
    }

    private static String recortar(String valor, int maximo) {
        return valor == null || valor.length() <= maximo ? valor : valor.substring(0, maximo);
    }
}
