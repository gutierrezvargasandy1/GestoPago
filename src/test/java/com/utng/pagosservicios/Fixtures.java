package com.utng.pagosservicios;

import com.utng.pagosservicios.config.PagosProperties;
import com.utng.pagosservicios.dominio.catalogo.Producto;
import com.utng.pagosservicios.dominio.catalogo.Servicio;
import com.utng.pagosservicios.dominio.cliente.Cliente;
import com.utng.pagosservicios.dominio.cuenta.Cuenta;
import com.utng.pagosservicios.dominio.pago.EstadoTransaccion;
import com.utng.pagosservicios.dominio.pago.MotivoResolucion;
import com.utng.pagosservicios.dominio.pago.SolicitudPago;
import com.utng.pagosservicios.dominio.pago.TransaccionPago;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/** Objetos de dominio listos para pruebas unitarias. */
public final class Fixtures {

    public static final Instant AHORA = Instant.parse("2026-10-27T18:00:00Z");
    public static final Duration MARGEN = Duration.ofMinutes(2);

    private Fixtures() {
    }

    /** Servicio del catalogo de ejemplo, con el identificador que el simulador reconoce. */
    public static Servicio servicio(String idServicio, int idServicioProveedor) {
        Servicio servicio = new Servicio(idServicio, "Servicio " + idServicio, "PRUEBA");
        ReflectionTestUtils.setField(servicio, "id", 1L);
        ReflectionTestUtils.setField(servicio, "idServicioProveedor", idServicioProveedor);
        return servicio;
    }

    public static Producto telcel100() {
        Producto producto = Producto.deMontoFijo(servicio("TAE_TELCEL", 1001), "TEL_100", "Telcel $100",
                new BigDecimal("100.00"), BigDecimal.ZERO);
        producto.definirFormatoReferencia(10, "^[0-9]{10}$");
        ReflectionTestUtils.setField(producto, "id", 10L);
        ReflectionTestUtils.setField(producto, "idProductoProveedor", 2003);
        return producto;
    }

    public static Producto cfe() {
        Producto producto = Producto.deMontoAbierto(servicio("CFE", 1003), "CFE_PAGO", "Pago CFE",
                new BigDecimal("10.00"), new BigDecimal("10000.00"), new BigDecimal("0.50"), new BigDecimal("8.00"));
        producto.definirFormatoReferencia(12, "^[0-9]{12}$");
        ReflectionTestUtils.setField(producto, "id", 20L);
        ReflectionTestUtils.setField(producto, "idProductoProveedor", 3001);
        return producto;
    }

    public static Cliente cliente(long id, String usuario) {
        Cliente cliente = new Cliente(usuario, "$2a$10$hash", "Cliente " + usuario);
        ReflectionTestUtils.setField(cliente, "id", id);
        return cliente;
    }

    public static Cliente clienteSinPermisoDePagos(long id, String usuario) {
        Cliente cliente = cliente(id, usuario);
        ReflectionTestUtils.setField(cliente, "pagosHabilitados", false);
        return cliente;
    }

    public static Cuenta cuenta(long id, long clienteId, String saldo) {
        Cuenta cuenta = new Cuenta(clienteId, "1000000001", new BigDecimal(saldo), new BigDecimal("10000.00"));
        ReflectionTestUtils.setField(cuenta, "id", id);
        return cuenta;
    }

    public static SolicitudPago solicitudTelcel() {
        return new SolicitudPago("1000000001", "TAE_TELCEL", "TEL_100", "5512345678", new BigDecimal("100.00"));
    }

    /** Transaccion de Telcel $100 llevada al estado pedido por transiciones validas. */
    public static TransaccionPago transaccion(long id, EstadoTransaccion estado) {
        TransaccionPago transaccion = TransaccionPago.reservar(solicitudTelcel(), "clave-0001",
                cuenta(1L, 7L, "1000.00"), telcel100(), "traza-prueba", AHORA, MARGEN);
        ReflectionTestUtils.setField(transaccion, "id", id);
        switch (estado) {
            case DEBITADA -> {
                // estado inicial
            }
            case ENVIADA -> transaccion.marcarEnviada(AHORA, MARGEN);
            case EN_CONCILIACION -> {
                transaccion.marcarEnviada(AHORA, MARGEN);
                transaccion.enviarAConciliacion("TIMEOUT_LECTURA", AHORA, MARGEN);
            }
            case REVISION_MANUAL -> {
                transaccion.marcarEnviada(AHORA, MARGEN);
                transaccion.escalarARevisionManual(MotivoResolucion.INTENTOS_AGOTADOS, AHORA);
            }
            case CONFIRMADA -> {
                transaccion.marcarEnviada(AHORA, MARGEN);
                transaccion.confirmar("01", "F-1", "A-1", AHORA);
            }
            case REVERSADA -> transaccion.reversar(MotivoResolucion.NUNCA_ENVIADA, null, AHORA);
        }
        return transaccion;
    }

    public static TransaccionPago conIntentos(TransaccionPago transaccion, int intentos) {
        for (int i = 0; i < intentos; i++) {
            transaccion.registrarIntentoConsulta(AHORA, Duration.ofMinutes(5));
        }
        return transaccion;
    }

    public static PagosProperties propiedadesPagos(int maxIntentos, int confirmacionesNoEncontrada) {
        return propiedadesPagos(maxIntentos, confirmacionesNoEncontrada, true);
    }

    public static PagosProperties propiedadesPagos(boolean permitirEjemplo) {
        return propiedadesPagos(8, 2, permitirEjemplo);
    }

    private static PagosProperties propiedadesPagos(int maxIntentos, int confirmacionesNoEncontrada,
                                                    boolean permitirEjemplo) {
        return new PagosProperties("America/Mexico_City",
                new PagosProperties.Conciliacion(true, Duration.ofMinutes(1), 50, MARGEN, Duration.ofMinutes(1),
                        Duration.ofHours(1), maxIntentos, confirmacionesNoEncontrada, Duration.ofMinutes(5),
                        Duration.ofHours(23)),
                new PagosProperties.Catalogo(permitirEjemplo, false, "0 10 3 * * *", Duration.ofHours(23), 3,
                        new BigDecimal("1.00"), new BigDecimal("10000.00")),
                new PagosProperties.BaseDeDatos("public"));
    }
}
