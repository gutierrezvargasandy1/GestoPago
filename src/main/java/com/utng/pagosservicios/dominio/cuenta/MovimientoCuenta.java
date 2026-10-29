package com.utng.pagosservicios.dominio.cuenta;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Asiento del ledger. Solo se inserta, nunca se modifica ni se borra: la suma de
 * los movimientos permite reconstruir y auditar el saldo de cualquier cuenta.
 */
@Entity
@Table(name = "movimiento_cuenta")
public class MovimientoCuenta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cuenta_id", nullable = false, updatable = false)
    private Long cuentaId;

    @Column(name = "transaccion_pago_id", updatable = false)
    private Long transaccionPagoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private TipoMovimiento tipo;

    @Column(nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal monto;

    @Column(name = "saldo_resultante", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal saldoResultante;

    @Column(length = 200, updatable = false)
    private String descripcion;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    protected MovimientoCuenta() {
        // requerido por JPA
    }

    private MovimientoCuenta(Cuenta cuenta, Long transaccionPagoId, TipoMovimiento tipo,
                             BigDecimal monto, String descripcion, Instant creadoEn) {
        this.cuentaId = cuenta.getId();
        this.transaccionPagoId = transaccionPagoId;
        this.tipo = tipo;
        this.monto = monto;
        this.saldoResultante = cuenta.getSaldo();
        this.descripcion = descripcion;
        this.creadoEn = creadoEn;
    }

    /** Debe crearse despues de {@link Cuenta#debitar} para registrar el saldo resultante. */
    public static MovimientoCuenta cargo(Cuenta cuenta, Long transaccionPagoId, BigDecimal monto, Instant ahora) {
        return new MovimientoCuenta(cuenta, transaccionPagoId, TipoMovimiento.CARGO, monto,
                "Pago de servicio, transaccion " + transaccionPagoId, ahora);
    }

    /** Debe crearse despues de {@link Cuenta#abonar} para registrar el saldo resultante. */
    public static MovimientoCuenta abono(Cuenta cuenta, Long transaccionPagoId, BigDecimal monto, Instant ahora) {
        return new MovimientoCuenta(cuenta, transaccionPagoId, TipoMovimiento.ABONO, monto,
                "Reverso de pago de servicio, transaccion " + transaccionPagoId, ahora);
    }

    public Long getId() {
        return id;
    }

    public Long getCuentaId() {
        return cuentaId;
    }

    public Long getTransaccionPagoId() {
        return transaccionPagoId;
    }

    public TipoMovimiento getTipo() {
        return tipo;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public BigDecimal getSaldoResultante() {
        return saldoResultante;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
