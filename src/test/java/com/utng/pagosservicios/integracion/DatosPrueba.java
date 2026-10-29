package com.utng.pagosservicios.integracion;

import com.utng.pagosservicios.dominio.pago.GeneradorUpc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Prepara y consulta datos directamente con SQL, sin pasar por la logica que se esta probando. */
class DatosPrueba {

    static final String PASSWORD = "Clave-Segura-2026";

    private final JdbcTemplate jdbc;
    private final String hashPassword;

    DatosPrueba(JdbcTemplate jdbc, String hashPassword) {
        this.jdbc = jdbc;
        this.hashPassword = hashPassword;
    }

    static String hash(PasswordEncoder codificador) {
        return codificador.encode(PASSWORD);
    }

    /** Deja la base como recien migrada: sin clientes, sin operaciones y solo con el catalogo de ejemplo. */
    void limpiar() {
        jdbc.execute("TRUNCATE TABLE movimiento_cuenta, transaccion_pago, cuenta, cliente, bitacora_proveedor, "
                + "sincronizacion_catalogo RESTART IDENTITY CASCADE");
        jdbc.update("DELETE FROM producto WHERE origen = 'PROVEEDOR'");
        jdbc.update("DELETE FROM servicio WHERE origen = 'PROVEEDOR'");
    }

    long crearCliente(String usuario) {
        return crearCliente(usuario, true, true);
    }

    long crearCliente(String usuario, boolean activo, boolean pagosHabilitados) {
        return jdbc.queryForObject("""
                INSERT INTO cliente (usuario, password_hash, nombre, activo, pagos_habilitados)
                VALUES (?, ?, ?, ?, ?) RETURNING id
                """, Long.class, usuario, hashPassword, "Cliente " + usuario, activo, pagosHabilitados);
    }

    void crearCuenta(long clienteId, String numero, String saldo) {
        crearCuenta(clienteId, numero, saldo, "ACTIVA", "10000.00");
    }

    void crearCuenta(long clienteId, String numero, String saldo, String estado, String limiteDiario) {
        jdbc.update("INSERT INTO cuenta (cliente_id, numero, saldo, estado, limite_diario) VALUES (?, ?, ?, ?, ?)",
                clienteId, numero, new BigDecimal(saldo), estado, new BigDecimal(limiteDiario));
    }

    BigDecimal saldo(String numero) {
        return jdbc.queryForObject("SELECT saldo FROM cuenta WHERE numero = ?", BigDecimal.class, numero);
    }

    void fijarSaldo(String numero, String saldo) {
        jdbc.update("UPDATE cuenta SET saldo = ? WHERE numero = ?", new BigDecimal(saldo), numero);
    }

    int contarMovimientos(String numero, String tipo) {
        Integer total = jdbc.queryForObject("""
                SELECT COUNT(*) FROM movimiento_cuenta m JOIN cuenta c ON c.id = m.cuenta_id
                WHERE c.numero = ? AND m.tipo = ?
                """, Integer.class, numero, tipo);
        return total == null ? 0 : total;
    }

    int contarTransacciones() {
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM transaccion_pago", Integer.class);
        return total == null ? 0 : total;
    }

    String estado(long transaccionId) {
        return jdbc.queryForObject("SELECT estado FROM transaccion_pago WHERE id = ?", String.class, transaccionId);
    }

    String motivo(long transaccionId) {
        return jdbc.queryForObject("SELECT motivo FROM transaccion_pago WHERE id = ?", String.class, transaccionId);
    }

    String referenciaExterna(long transaccionId) {
        return jdbc.queryForObject("SELECT referencia_externa FROM transaccion_pago WHERE id = ?", String.class,
                transaccionId);
    }

    /** Simula el paso del tiempo: la transaccion queda lista para el siguiente intento de conciliacion. */
    void adelantarConciliacion(long transaccionId) {
        jdbc.update("UPDATE transaccion_pago SET proximo_intento_en = now() - interval '1 second' WHERE id = ?",
                transaccionId);
    }

    /** Simula que el envio ocurrio hace tantas horas (confirmTx solo responde durante 24 h). */
    void enviadaHace(long transaccionId, int horas) {
        jdbc.update("UPDATE transaccion_pago SET enviado_en = now() - make_interval(hours => ?) WHERE id = ?",
                horas, transaccionId);
    }

    /** Filas de la bitacora de intercambios con el proveedor, en orden. */
    List<Map<String, Object>> bitacora() {
        return jdbc.queryForList("""
                SELECT operacion, referencia_externa, intento, solicitud, status_http, codigo, respuesta, error
                FROM bitacora_proveedor ORDER BY id
                """);
    }

    /** Simula que la ultima sincronizacion del catalogo fue hace tantas horas. */
    void sincronizacionHace(int horas) {
        jdbc.update("""
                UPDATE sincronizacion_catalogo
                SET iniciado_en = now() - make_interval(hours => ?), terminado_en = now() - make_interval(hours => ?)
                """, horas, horas);
    }

    boolean productoActivo(String idServicio, String idProducto) {
        Boolean activo = jdbc.queryForObject("""
                SELECT p.activo FROM producto p JOIN servicio s ON s.id = p.servicio_id
                WHERE s.id_servicio = ? AND p.id_producto = ?
                """, Boolean.class, idServicio, idProducto);
        return Boolean.TRUE.equals(activo);
    }

    /**
     * Inserta una transaccion de Telcel $100 en el estado indicado. Si debitar es
     * true, tambien descuenta el saldo y registra el CARGO, como lo haria el flujo real.
     */
    long insertarTransaccion(long clienteId, String numeroCuenta, String clave, String huella,
                             String estado, boolean debitar) {
        BigDecimal monto = new BigDecimal("100.00");
        Long id = jdbc.queryForObject("""
                INSERT INTO transaccion_pago (cliente_id, cuenta_id, producto_id, clave_idempotencia, huella_solicitud,
                    referencia_externa, referencia_servicio, monto, comision, monto_total, estado,
                    proximo_intento_en, creado_en, enviado_en, actualizado_en)
                VALUES (?, (SELECT id FROM cuenta WHERE numero = ?), (SELECT id FROM producto WHERE id_producto = 'TEL_100'),
                    ?, ?, ?, '5512345678', ?, 0, ?, ?, now() + interval '1 hour', now(),
                    CASE WHEN ? = 'DEBITADA' THEN NULL ELSE now() END, now())
                RETURNING id
                """, Long.class, clienteId, numeroCuenta, clave, huella,
                GeneradorUpc.generar(), monto, monto, estado, estado);
        if (debitar) {
            jdbc.update("UPDATE cuenta SET saldo = saldo - ? WHERE numero = ?", monto, numeroCuenta);
            jdbc.update("""
                    INSERT INTO movimiento_cuenta (cuenta_id, transaccion_pago_id, tipo, monto, saldo_resultante, descripcion)
                    SELECT id, ?, 'CARGO', ?, saldo, 'Insertado por la prueba' FROM cuenta WHERE numero = ?
                    """, id, monto, numeroCuenta);
        }
        return id;
    }
}
