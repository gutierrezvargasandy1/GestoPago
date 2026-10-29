package com.utng.pagosservicios.repositorio;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

/**
 * Historial de sincronizaciones del catalogo. Vive en la base (y no en memoria)
 * para que el limite de 3 llamadas diarias a getProductList se respete aunque
 * la aplicacion se reinicie.
 */
@Repository
public class RegistroSincronizacionJdbc {

    private final JdbcTemplate jdbc;

    public RegistroSincronizacionJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Instant> ultimaExitosa() {
        Timestamp ultima = jdbc.queryForObject(
                "SELECT MAX(terminado_en) FROM sincronizacion_catalogo WHERE exitosa = TRUE", Timestamp.class);
        return Optional.ofNullable(ultima).map(Timestamp::toInstant);
    }

    /** Cuenta los intentos (exitosos o no): cada uno fue una llamada al proveedor. */
    public int intentosDesde(Instant desde) {
        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sincronizacion_catalogo WHERE iniciado_en >= ?", Integer.class,
                Timestamp.from(desde));
        return total == null ? 0 : total;
    }

    /** Se registra ANTES de llamar al proveedor: si el proceso muere, el intento igual cuenta. */
    public long registrarInicio(Instant ahora) {
        Long id = jdbc.queryForObject(
                "INSERT INTO sincronizacion_catalogo (iniciado_en) VALUES (?) RETURNING id", Long.class,
                Timestamp.from(ahora));
        if (id == null) {
            throw new IllegalStateException("No se pudo registrar el inicio de la sincronizacion");
        }
        return id;
    }

    public void registrarFin(long id, Instant ahora, boolean exitosa, int productos, String detalle) {
        jdbc.update("""
                UPDATE sincronizacion_catalogo
                SET terminado_en = ?, exitosa = ?, productos = ?, detalle = ?
                WHERE id = ?
                """, Timestamp.from(ahora), exitosa, productos,
                detalle == null || detalle.length() <= 80 ? detalle : detalle.substring(0, 80), id);
    }
}
