package com.utng.pagosservicios.servicio;

import com.utng.pagosservicios.config.PagosProperties;
import com.utng.pagosservicios.dominio.catalogo.Producto;
import com.utng.pagosservicios.dominio.cuenta.Cuenta;
import com.utng.pagosservicios.dominio.cuenta.MovimientoCuenta;
import com.utng.pagosservicios.dominio.pago.EstadoTransaccion;
import com.utng.pagosservicios.dominio.pago.MotivoResolucion;
import com.utng.pagosservicios.dominio.pago.SolicitudPago;
import com.utng.pagosservicios.dominio.pago.TransaccionPago;
import com.utng.pagosservicios.repositorio.CuentaRepository;
import com.utng.pagosservicios.repositorio.MovimientoCuentaRepository;
import com.utng.pagosservicios.repositorio.TransaccionPagoRepository;
import com.utng.pagosservicios.servicio.excepcion.AccesoDenegadoException;
import com.utng.pagosservicios.servicio.excepcion.CodigoError;
import com.utng.pagosservicios.servicio.excepcion.ReglaNegocioException;
import com.utng.pagosservicios.util.Enmascarar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Operaciones que mueven dinero. Cada metodo es UNA transaccion de base de
 * datos corta y nunca incluye llamadas de red: mantener el bloqueo de una
 * cuenta mientras se espera al proveedor (hasta 20 s) frenaria todas las
 * operaciones de esa cuenta y agotaria el pool de conexiones.
 */
@Service
public class FondosService {

    private static final Logger log = LoggerFactory.getLogger(FondosService.class);

    private final CuentaRepository cuentas;
    private final TransaccionPagoRepository transacciones;
    private final MovimientoCuentaRepository movimientos;
    private final PagosProperties propiedades;
    private final Clock reloj;

    public FondosService(CuentaRepository cuentas, TransaccionPagoRepository transacciones,
                         MovimientoCuentaRepository movimientos, PagosProperties propiedades, Clock reloj) {
        this.cuentas = cuentas;
        this.transacciones = transacciones;
        this.movimientos = movimientos;
        this.propiedades = propiedades;
        this.reloj = reloj;
    }

    /**
     * Transaccion 1: bloquea la cuenta, valida estado, limite y saldo, registra
     * la transaccion y descuenta el dinero. Todo o nada.
     *
     * <p>El INSERT de la transaccion va antes del debito y con flush inmediato:
     * si otra peticion ya uso la misma clave de idempotencia, el UNIQUE falla
     * aqui y la transaccion completa se revierte sin haber tocado el saldo.
     */
    @Transactional
    public TransaccionPago reservar(Long clienteId, String claveIdempotencia, SolicitudPago solicitud,
                                    Producto producto, String traceId) {
        Cuenta cuenta = cuentas.bloquearPorNumeroYCliente(solicitud.numeroCuenta(), clienteId)
                // Misma respuesta si no existe o es ajena: no se revela que cuentas existen.
                .orElseThrow(() -> new AccesoDenegadoException(CodigoError.CUENTA_NO_AUTORIZADA,
                        "La cuenta indicada no existe o no pertenece al cliente autenticado."));

        if (!cuenta.permiteOperar()) {
            throw new ReglaNegocioException(CodigoError.CUENTA_INACTIVA, "La cuenta no está activa.");
        }

        BigDecimal total = producto.totalACobrar(solicitud.monto());
        BigDecimal comprometidoHoy = montoComprometidoHoy(cuenta.getId());
        if (comprometidoHoy.add(total).compareTo(cuenta.getLimiteDiario()) > 0) {
            throw new ReglaNegocioException(CodigoError.LIMITE_DIARIO_EXCEDIDO,
                    "La operación excede el límite diario de la cuenta.");
        }
        if (!cuenta.saldoSuficiente(total)) {
            throw new ReglaNegocioException(CodigoError.SALDO_INSUFICIENTE,
                    "Saldo insuficiente para cubrir el pago y la comisión.");
        }

        Instant ahora = reloj.instant();
        TransaccionPago transaccion = transacciones.saveAndFlush(TransaccionPago.reservar(
                solicitud, claveIdempotencia, cuenta, producto, traceId, ahora,
                propiedades.conciliacion().margenInicial()));
        cuenta.debitar(total);
        movimientos.save(MovimientoCuenta.cargo(cuenta, transaccion.getId(), total, ahora));

        log.info("Transaccion {} reservada: cuenta={}, producto={}, total={}",
                transaccion.getId(), Enmascarar.ultimos(cuenta.getNumero()), producto.getIdProducto(), total);
        return transaccion;
    }

    /**
     * Devuelve el dinero y cierra la transaccion como REVERSADA. La maquina de
     * estados impide reversar algo ya confirmado o ya reversado, y el UNIQUE
     * (transaccion, tipo) de movimiento_cuenta impide un segundo abono aunque
     * dos procesos lo intenten a la vez.
     */
    @Transactional
    public TransaccionPago reversar(Long transaccionId, MotivoResolucion motivo, String codigoResultado) {
        TransaccionPago transaccion = transacciones.findById(transaccionId)
                .orElseThrow(() -> new IllegalStateException("No existe la transaccion " + transaccionId));
        Cuenta cuenta = cuentas.bloquearPorId(transaccion.getCuentaId())
                .orElseThrow(() -> new IllegalStateException("No existe la cuenta de la transaccion " + transaccionId));

        Instant ahora = reloj.instant();
        transaccion.reversar(motivo, codigoResultado, ahora);
        cuenta.abonar(transaccion.getMontoTotal());
        movimientos.save(MovimientoCuenta.abono(cuenta, transaccion.getId(), transaccion.getMontoTotal(), ahora));

        log.info("Transaccion {} reversada ({}): se devolvieron {} a la cuenta {}",
                transaccionId, motivo, transaccion.getMontoTotal(), Enmascarar.ultimos(cuenta.getNumero()));
        return transaccion;
    }

    private BigDecimal montoComprometidoHoy(Long cuentaId) {
        ZoneId zona = propiedades.zona();
        Instant inicioDelDia = LocalDate.ofInstant(reloj.instant(), zona).atStartOfDay(zona).toInstant();
        BigDecimal suma = transacciones.sumarComprometidoDesde(cuentaId, inicioDelDia, EstadoTransaccion.REVERSADA);
        return suma == null ? BigDecimal.ZERO : suma;
    }
}
