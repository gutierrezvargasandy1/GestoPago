package com.utng.pagosservicios.dominio.pago;

import com.utng.pagosservicios.dominio.catalogo.Producto;
import com.utng.pagosservicios.dominio.cuenta.Cuenta;
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
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Registro de una operacion de pago de principio a fin. Es la fuente de verdad
 * cuando algo sale mal: en que estado quedo, que contesto el proveedor y
 * cuantas veces se ha intentado conciliar.
 *
 * <p>El estado solo cambia a traves de los metodos de negocio, que validan la
 * transicion contra {@link EstadoTransaccion#puedeTransicionarA}.
 */
@Entity
@Table(name = "transaccion_pago")
public class TransaccionPago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cliente_id", nullable = false, updatable = false)
    private Long clienteId;

    @Column(name = "cuenta_id", nullable = false, updatable = false)
    private Long cuentaId;

    @Column(name = "producto_id", nullable = false, updatable = false)
    private Long productoId;

    @Column(name = "clave_idempotencia", nullable = false, length = 80, updatable = false)
    private String claveIdempotencia;

    @Column(name = "huella_solicitud", nullable = false, length = 64, updatable = false)
    private String huellaSolicitud;

    /**
     * UPC: identificador que viaja al proveedor (campo "upc" de sendTx); con el
     * se detectan duplicados y se pregunta despues por el resultado.
     */
    @Column(name = "referencia_externa", nullable = false, length = 40, updatable = false)
    private String referenciaExterna;

    /** Referencia del servicio a pagar: telefono, numero de recibo, etc. */
    @Column(name = "referencia_servicio", nullable = false, length = 40, updatable = false)
    private String referenciaServicio;

    @Column(nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal monto;

    @Column(nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal comision;

    @Column(name = "monto_total", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal montoTotal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoTransaccion estado;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private MotivoResolucion motivo;

    @Column(name = "codigo_resultado", length = 40)
    private String codigoResultado;

    @Column(name = "folio_proveedor", length = 80)
    private String folioProveedor;

    @Column(name = "autorizacion_proveedor", length = 80)
    private String autorizacionProveedor;

    /** PIN de una tarjeta de regalo: es lo que el cliente compro. Solo lo ve su dueno. */
    @Column(name = "pin_canje", length = 100)
    private String pinCanje;

    @Column(name = "instrucciones_canje", length = 1000)
    private String instruccionesCanje;

    @Column(name = "intentos_consulta", nullable = false)
    private int intentosConsulta;

    @Column(name = "proximo_intento_en")
    private Instant proximoIntentoEn;

    @Column(name = "trace_id", length = 64, updatable = false)
    private String traceId;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "enviado_en")
    private Instant enviadoEn;

    @Column(name = "resuelto_en")
    private Instant resueltoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    @Version
    @Column(nullable = false)
    private Long version;

    protected TransaccionPago() {
        // requerido por JPA
    }

    /**
     * Crea la transaccion en estado DEBITADA. Se usa dentro de la misma
     * transaccion de base de datos que descuenta el saldo, asi que nunca existe
     * un registro DEBITADA sin el cargo correspondiente (ni al reves).
     */
    public static TransaccionPago reservar(SolicitudPago solicitud, String claveIdempotencia, Cuenta cuenta,
                                           Producto producto, String traceId, Instant ahora, Duration margenInicial) {
        TransaccionPago t = new TransaccionPago();
        t.clienteId = cuenta.getClienteId();
        t.cuentaId = cuenta.getId();
        t.productoId = producto.getId();
        t.claveIdempotencia = Objects.requireNonNull(claveIdempotencia);
        t.huellaSolicitud = solicitud.huella();
        t.referenciaExterna = GeneradorUpc.generar();
        t.referenciaServicio = solicitud.referencia();
        t.monto = solicitud.monto();
        t.comision = producto.getComision();
        t.montoTotal = producto.totalACobrar(solicitud.monto());
        t.estado = EstadoTransaccion.DEBITADA;
        t.traceId = traceId;
        t.creadoEn = ahora;
        t.actualizadoEn = ahora;
        // Si el proceso muere antes de enviar, la conciliacion la encontrara aqui.
        t.proximoIntentoEn = ahora.plus(margenInicial);
        return t;
    }

    /**
     * Se registra ANTES de llamar al proveedor. Asi, DEBITADA significa con
     * certeza "nunca se envio" y la conciliacion puede reversarla sin consultar.
     */
    public void marcarEnviada(Instant ahora, Duration margenInicial) {
        cambiarEstado(EstadoTransaccion.ENVIADA, ahora);
        // La hora de envio forma parte de la operacion (horaLocal de sendTx) y la
        // conciliacion la vuelve a enviar a confirmTx: se trunca a la precision que
        // guarda la base para que, al releerla, sea exactamente la misma.
        enviadoEn = ahora.truncatedTo(ChronoUnit.MICROS);
        proximoIntentoEn = ahora.plus(margenInicial);
    }

    public void confirmar(String codigo, String folio, String autorizacion, Instant ahora) {
        confirmar(codigo, folio, autorizacion, null, null, ahora);
    }

    public void confirmar(String codigo, String folio, String autorizacion, String pin, String instrucciones,
                          Instant ahora) {
        cambiarEstado(EstadoTransaccion.CONFIRMADA, ahora);
        motivo = null;
        codigoResultado = codigo;
        folioProveedor = folio;
        autorizacionProveedor = autorizacion;
        pinCanje = pin;
        instruccionesCanje = instrucciones;
        cerrar(ahora);
    }

    public void reversar(MotivoResolucion motivoReverso, String codigo, Instant ahora) {
        cambiarEstado(EstadoTransaccion.REVERSADA, ahora);
        motivo = Objects.requireNonNull(motivoReverso);
        if (codigo != null) {
            codigoResultado = codigo;
        }
        cerrar(ahora);
    }

    /** Resultado desconocido: el dinero queda retenido y se programa la consulta. */
    public void enviarAConciliacion(String codigo, Instant ahora, Duration margenInicial) {
        cambiarEstado(EstadoTransaccion.EN_CONCILIACION, ahora);
        motivo = MotivoResolucion.RESULTADO_INDETERMINADO;
        codigoResultado = codigo;
        proximoIntentoEn = ahora.plus(margenInicial);
    }

    public void escalarARevisionManual(MotivoResolucion motivoRevision, Instant ahora) {
        cambiarEstado(EstadoTransaccion.REVISION_MANUAL, ahora);
        motivo = Objects.requireNonNull(motivoRevision);
        proximoIntentoEn = null;
    }

    public boolean listaParaConciliar(Instant ahora) {
        return estado.conciliableAutomaticamente()
                && proximoIntentoEn != null
                && !proximoIntentoEn.isAfter(ahora);
    }

    /**
     * Toma la transaccion para un intento de conciliacion. El "arrendamiento"
     * evita que otro proceso la tome mientras este consulta al proveedor; si
     * este proceso muere, la transaccion vuelve a estar disponible al vencer.
     */
    public void registrarIntentoConsulta(Instant ahora, Duration arrendamiento) {
        intentosConsulta++;
        proximoIntentoEn = ahora.plus(arrendamiento);
        actualizadoEn = ahora;
    }

    /** Programa la siguiente consulta con espera exponencial (base, 2x, 4x... hasta el maximo). */
    public void programarSiguienteConsulta(String codigo, Instant ahora, Duration esperaBase, Duration esperaMaxima) {
        if (estado == EstadoTransaccion.ENVIADA) {
            cambiarEstado(EstadoTransaccion.EN_CONCILIACION, ahora);
            motivo = MotivoResolucion.RESULTADO_INDETERMINADO;
        }
        if (codigo != null) {
            codigoResultado = codigo;
        }
        proximoIntentoEn = ahora.plus(esperaExponencial(intentosConsulta, esperaBase, esperaMaxima));
        actualizadoEn = ahora;
    }

    static Duration esperaExponencial(int intentos, Duration base, Duration maxima) {
        if (base.isZero() || base.isNegative()) {
            return Duration.ZERO;
        }
        int exponente = Math.min(Math.max(intentos - 1, 0), 16);
        Duration espera = base.multipliedBy(1L << exponente);
        return espera.compareTo(maxima) > 0 ? maxima : espera;
    }

    private void cambiarEstado(EstadoTransaccion destino, Instant ahora) {
        if (!estado.puedeTransicionarA(destino)) {
            throw new IllegalStateException(
                    "Transicion invalida de " + estado + " a " + destino + " en la transaccion " + id);
        }
        estado = destino;
        actualizadoEn = ahora;
    }

    private void cerrar(Instant ahora) {
        resueltoEn = ahora;
        proximoIntentoEn = null;
    }

    public Long getId() {
        return id;
    }

    public Long getClienteId() {
        return clienteId;
    }

    public Long getCuentaId() {
        return cuentaId;
    }

    public Long getProductoId() {
        return productoId;
    }

    public String getClaveIdempotencia() {
        return claveIdempotencia;
    }

    public String getHuellaSolicitud() {
        return huellaSolicitud;
    }

    public String getReferenciaExterna() {
        return referenciaExterna;
    }

    public String getReferenciaServicio() {
        return referenciaServicio;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public BigDecimal getComision() {
        return comision;
    }

    public BigDecimal getMontoTotal() {
        return montoTotal;
    }

    public EstadoTransaccion getEstado() {
        return estado;
    }

    public MotivoResolucion getMotivo() {
        return motivo;
    }

    public String getCodigoResultado() {
        return codigoResultado;
    }

    public String getFolioProveedor() {
        return folioProveedor;
    }

    public String getAutorizacionProveedor() {
        return autorizacionProveedor;
    }

    public String getPinCanje() {
        return pinCanje;
    }

    public String getInstruccionesCanje() {
        return instruccionesCanje;
    }

    public int getIntentosConsulta() {
        return intentosConsulta;
    }

    public Instant getProximoIntentoEn() {
        return proximoIntentoEn;
    }

    public String getTraceId() {
        return traceId;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public Instant getEnviadoEn() {
        return enviadoEn;
    }

    public Instant getResueltoEn() {
        return resueltoEn;
    }

    public Instant getActualizadoEn() {
        return actualizadoEn;
    }

    public Long getVersion() {
        return version;
    }
}
