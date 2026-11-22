package com.utng.pagosservicios.repositorio;

import com.utng.pagosservicios.dominio.cliente.Plataforma;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

/** Sesiones de los clientes (tabla sesion). */
@Repository
public class RepositorioSesiones {

    public record SesionAbierta(String sid, Plataforma plataforma, Instant creadaEn, Instant ultimaActividad,
                                Instant expiraEn) {
        @Override
        public String toString() {
            return "SesionAbierta[sid=****, plataforma=" + plataforma + ", expiraEn=" + expiraEn + "]";
        }
    }

    private final JdbcTemplate jdbc;

    public RepositorioSesiones(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Cierra como EXPIRADA lo que vencio o quedo inactivo; libera el lugar de la sesion unica. */
    public void cerrarVencidas(long clienteId, Instant ahora, Instant inactivaDesde) {
        jdbc.update("""
                UPDATE sesion SET cerrada_en = ?, motivo_cierre = 'EXPIRADA'
                WHERE cliente_id = ? AND cerrada_en IS NULL AND (expira_en <= ? OR ultima_actividad <= ?)
                """, ts(ahora), clienteId, ts(ahora), ts(inactivaDesde));
    }

    /** La sesion abierta del cliente, bloqueada hasta terminar la transaccion. */
    public Optional<SesionAbierta> abiertaParaActualizar(long clienteId) {
        return jdbc.query("""
                SELECT sid, plataforma, creada_en, ultima_actividad, expira_en FROM sesion
                WHERE cliente_id = ? AND cerrada_en IS NULL FOR UPDATE
                """, (rs, i) -> new SesionAbierta(rs.getString(1), Plataforma.valueOf(rs.getString(2)),
                rs.getTimestamp(3).toInstant(), rs.getTimestamp(4).toInstant(), rs.getTimestamp(5).toInstant()),
                clienteId).stream().findFirst();
    }

    public Optional<SesionAbierta> buscar(String sid) {
        return jdbc.query("""
                SELECT sid, plataforma, creada_en, ultima_actividad, expira_en FROM sesion
                WHERE sid = ? AND cerrada_en IS NULL
                """, (rs, i) -> new SesionAbierta(rs.getString(1), Plataforma.valueOf(rs.getString(2)),
                rs.getTimestamp(3).toInstant(), rs.getTimestamp(4).toInstant(), rs.getTimestamp(5).toInstant()),
                sid).stream().findFirst();
    }

    public void insertar(String sid, long clienteId, Plataforma plataforma, Instant ahora, Instant expira) {
        jdbc.update("""
                INSERT INTO sesion (sid, cliente_id, plataforma, creada_en, ultima_actividad, expira_en)
                VALUES (?, ?, ?, ?, ?, ?)
                """, sid, clienteId, plataforma.name(), ts(ahora), ts(ahora), ts(expira));
    }

    public int cerrarDelCliente(long clienteId, String motivo, Instant ahora) {
        return jdbc.update("UPDATE sesion SET cerrada_en = ?, motivo_cierre = ? WHERE cliente_id = ? AND cerrada_en IS NULL",
                ts(ahora), motivo, clienteId);
    }

    public int cerrar(String sid, String motivo, Instant ahora) {
        return jdbc.update("UPDATE sesion SET cerrada_en = ?, motivo_cierre = ? WHERE sid = ? AND cerrada_en IS NULL",
                ts(ahora), motivo, sid);
    }

    /**
     * Un solo UPDATE valida y renueva la actividad: si la sesion esta cerrada,
     * vencida o inactiva no se toca ninguna fila y el token se rechaza.
     */
    public boolean registrarActividad(String sid, Instant ahora, Instant inactivaDesde) {
        return jdbc.update("""
                UPDATE sesion SET ultima_actividad = ?
                WHERE sid = ? AND cerrada_en IS NULL AND expira_en > ? AND ultima_actividad > ?
                """, ts(ahora), sid, ts(ahora), ts(inactivaDesde)) == 1;
    }

    private static Timestamp ts(Instant instante) {
        return Timestamp.from(instante);
    }
}
