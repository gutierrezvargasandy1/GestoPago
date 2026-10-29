package com.utng.pagosservicios.dominio.cuenta;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Cuenta de la que se descuenta el pago.
 *
 * <p>No tiene setters: el saldo solo cambia a traves de {@link #debitar} y
 * {@link #abonar}, que validan sus reglas. Asi nadie puede hacer
 * {@code cuenta.setSaldo(...)} y saltarse la validacion de fondos.
 *
 * <p>El dinero se maneja con {@link BigDecimal}: un {@code double} no
 * representa exactamente valores como 0.10 y el error se acumula.
 */
@Entity
@Table(name = "cuenta")
public class Cuenta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cliente_id", nullable = false)
    private Long clienteId;

    @Column(nullable = false, unique = true, length = 20)
    private String numero;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal saldo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoCuenta estado;

    @Column(name = "limite_diario", nullable = false, precision = 15, scale = 2)
    private BigDecimal limiteDiario;

    /**
     * Bloqueo optimista como segunda red: el flujo de pago toma un bloqueo
     * pesimista, pero si alguna ruta modificara la cuenta sin el, la version
     * detecta la escritura concurrente.
     */
    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    protected Cuenta() {
        // requerido por JPA
    }

    public Cuenta(Long clienteId, String numero, BigDecimal saldoInicial, BigDecimal limiteDiario) {
        this.clienteId = Objects.requireNonNull(clienteId);
        this.numero = Objects.requireNonNull(numero);
        this.saldo = Objects.requireNonNull(saldoInicial);
        this.limiteDiario = Objects.requireNonNull(limiteDiario);
        this.estado = EstadoCuenta.ACTIVA;
        this.creadoEn = Instant.now();
    }

    public boolean perteneceA(Long idCliente) {
        return clienteId.equals(idCliente);
    }

    public boolean permiteOperar() {
        return estado.permiteOperar();
    }

    public boolean saldoSuficiente(BigDecimal monto) {
        return saldo.compareTo(monto) >= 0;
    }

    /**
     * Descuenta el monto. Lanza excepcion si no alcanza: un debito que "no paso"
     * en silencio seria un descuadre.
     */
    public void debitar(BigDecimal monto) {
        exigirPositivo(monto);
        if (!saldoSuficiente(monto)) {
            throw new IllegalStateException("Saldo insuficiente en la cuenta " + id);
        }
        saldo = saldo.subtract(monto);
    }

    /** Devuelve el monto a la cuenta (reverso). */
    public void abonar(BigDecimal monto) {
        exigirPositivo(monto);
        saldo = saldo.add(monto);
    }

    public void bloquear() {
        estado = EstadoCuenta.BLOQUEADA;
    }

    private static void exigirPositivo(BigDecimal monto) {
        if (monto == null || monto.signum() <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor a cero");
        }
    }

    public Long getId() {
        return id;
    }

    public Long getClienteId() {
        return clienteId;
    }

    public String getNumero() {
        return numero;
    }

    public BigDecimal getSaldo() {
        return saldo;
    }

    public EstadoCuenta getEstado() {
        return estado;
    }

    public BigDecimal getLimiteDiario() {
        return limiteDiario;
    }

    public Long getVersion() {
        return version;
    }
}
