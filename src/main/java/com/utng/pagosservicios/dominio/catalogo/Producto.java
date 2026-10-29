package com.utng.pagosservicios.dominio.catalogo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Producto del catalogo y sus restricciones de monto y referencia.
 *
 * <p>Responde al requisito "verificar que el monto solicitado corresponda con
 * los valores y las restricciones permitidas": el monto de la peticion es una
 * propuesta del cliente; la autoridad sobre que monto es valido esta aqui.
 */
@Entity
@Table(name = "producto")
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "servicio_id", nullable = false)
    private Servicio servicio;

    /** Codigo con el que los clientes de la API piden el producto. */
    @Column(name = "id_producto", nullable = false, length = 50)
    private String idProducto;

    /** Identificador numerico del producto en PuntoRed; es el que viaja en sendTx. */
    @Column(name = "id_producto_proveedor")
    private Integer idProductoProveedor;

    @Column(nullable = false, length = 200)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Modalidad modalidad;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_monto", nullable = false, length = 20)
    private TipoMonto tipoMonto;

    @Column(name = "monto_fijo", precision = 15, scale = 2)
    private BigDecimal montoFijo;

    @Column(name = "monto_minimo", precision = 15, scale = 2)
    private BigDecimal montoMinimo;

    @Column(name = "monto_maximo", precision = 15, scale = 2)
    private BigDecimal montoMaximo;

    @Column(precision = 15, scale = 2)
    private BigDecimal multiplo;

    @Column(name = "longitud_referencia")
    private Integer longitudReferencia;

    @Column(name = "patron_referencia", length = 100)
    private String patronReferencia;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal comision;

    @Column(nullable = false)
    private boolean activo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrigenCatalogo origen;

    /** Instrucciones que publica el proveedor (p. ej. como canjear un PIN). */
    @Column(length = 500)
    private String leyenda;

    @Column(name = "sincronizado_en")
    private Instant sincronizadoEn;

    /** Resultado de validar un monto: el motivo concreto permite un mensaje util. */
    public enum ResultadoMonto {
        VALIDO,
        NO_COINCIDE_CON_MONTO_FIJO,
        FUERA_DE_RANGO,
        NO_RESPETA_MULTIPLO,
        DEMASIADOS_DECIMALES
    }

    protected Producto() {
        // requerido por JPA
    }

    private Producto(Servicio servicio, String idProducto, String nombre, TipoMonto tipoMonto, BigDecimal comision) {
        this.servicio = Objects.requireNonNull(servicio);
        this.idProducto = Objects.requireNonNull(idProducto);
        this.nombre = nombre;
        this.tipoMonto = tipoMonto;
        this.modalidad = tipoMonto == TipoMonto.FIJO ? Modalidad.RECARGA : Modalidad.PAGO_SERVICIO;
        this.comision = comision == null ? BigDecimal.ZERO : comision;
        this.activo = true;
        this.origen = OrigenCatalogo.SEMILLA;
    }

    public static Producto deMontoFijo(Servicio servicio, String idProducto, String nombre,
                                       BigDecimal montoFijo, BigDecimal comision) {
        Producto producto = new Producto(servicio, idProducto, nombre, TipoMonto.FIJO, comision);
        producto.montoFijo = Objects.requireNonNull(montoFijo);
        return producto;
    }

    public static Producto deMontoAbierto(Servicio servicio, String idProducto, String nombre,
                                          BigDecimal minimo, BigDecimal maximo, BigDecimal multiplo,
                                          BigDecimal comision) {
        Producto producto = new Producto(servicio, idProducto, nombre, TipoMonto.ABIERTO, comision);
        producto.montoMinimo = Objects.requireNonNull(minimo);
        producto.montoMaximo = Objects.requireNonNull(maximo);
        producto.multiplo = multiplo;
        return producto;
    }

    /**
     * Producto publicado por el proveedor. Su codigo en la API es el numero del
     * proveedor y la comision propia empieza en cero (se configura aparte).
     */
    public static Producto desdeProveedor(Servicio servicio, int idProductoProveedor, String nombre,
                                          CondicionesVenta condiciones, String leyenda, Instant ahora) {
        Producto producto = new Producto(servicio, String.valueOf(idProductoProveedor), nombre,
                condiciones.tipoMonto(), BigDecimal.ZERO);
        producto.idProductoProveedor = idProductoProveedor;
        producto.origen = OrigenCatalogo.PROVEEDOR;
        producto.aplicar(condiciones);
        producto.leyenda = leyenda;
        producto.sincronizadoEn = ahora;
        return producto;
    }

    /**
     * Actualiza nombre, precio y reglas con lo que publica el proveedor. La
     * comision no se toca: es una decision de negocio propia, no del proveedor.
     */
    public void actualizarDesdeProveedor(String nuevoNombre, CondicionesVenta condiciones, String nuevaLeyenda,
                                         Instant ahora) {
        this.nombre = nuevoNombre;
        aplicar(condiciones);
        this.leyenda = nuevaLeyenda;
        this.activo = true;
        this.sincronizadoEn = ahora;
    }

    /** Aplica todas las condiciones juntas, para no dejar un monto fijo junto a un rango viejo. */
    private void aplicar(CondicionesVenta condiciones) {
        this.modalidad = condiciones.modalidad();
        this.tipoMonto = condiciones.tipoMonto();
        this.montoFijo = condiciones.montoFijo();
        this.montoMinimo = condiciones.montoMinimo();
        this.montoMaximo = condiciones.montoMaximo();
        this.multiplo = condiciones.multiplo();
        this.longitudReferencia = condiciones.longitudReferencia();
        this.patronReferencia = condiciones.patronReferencia();
    }

    public void definirFormatoReferencia(Integer longitud, String patron) {
        this.longitudReferencia = longitud;
        this.patronReferencia = patron;
    }

    public void desactivar() {
        activo = false;
    }

    /**
     * El producto se puede vender si el y su servicio estan activos y si se sabe
     * con que identificadores pedirlo al proveedor.
     */
    public boolean disponible() {
        return activo && servicio.isActivo()
                && idProductoProveedor != null && servicio.getIdServicioProveedor() != null;
    }

    public boolean esDeEjemplo() {
        return origen == OrigenCatalogo.SEMILLA;
    }

    public ResultadoMonto validarMonto(BigDecimal monto) {
        if (monto == null || monto.signum() <= 0) {
            return tipoMonto == TipoMonto.FIJO
                    ? ResultadoMonto.NO_COINCIDE_CON_MONTO_FIJO
                    : ResultadoMonto.FUERA_DE_RANGO;
        }
        if (monto.stripTrailingZeros().scale() > 2) {
            return ResultadoMonto.DEMASIADOS_DECIMALES;
        }
        if (tipoMonto == TipoMonto.FIJO) {
            // compareTo y no equals: equals de BigDecimal considera distintos 100 y 100.00.
            return montoFijo.compareTo(monto) == 0
                    ? ResultadoMonto.VALIDO
                    : ResultadoMonto.NO_COINCIDE_CON_MONTO_FIJO;
        }
        if (monto.compareTo(montoMinimo) < 0 || monto.compareTo(montoMaximo) > 0) {
            return ResultadoMonto.FUERA_DE_RANGO;
        }
        if (multiplo != null && monto.remainder(multiplo).signum() != 0) {
            return ResultadoMonto.NO_RESPETA_MULTIPLO;
        }
        return ResultadoMonto.VALIDO;
    }

    public boolean referenciaValida(String referencia) {
        if (referencia == null || referencia.isBlank()) {
            return false;
        }
        if (longitudReferencia != null && referencia.length() != longitudReferencia) {
            return false;
        }
        return patronReferencia == null || Pattern.matches(patronReferencia, referencia);
    }

    /** Lo que realmente sale de la cuenta del cliente: el pago mas la comision. */
    public BigDecimal totalACobrar(BigDecimal monto) {
        return monto.add(comision);
    }

    public Long getId() {
        return id;
    }

    public Servicio getServicio() {
        return servicio;
    }

    public String getIdProducto() {
        return idProducto;
    }

    public Integer getIdProductoProveedor() {
        return idProductoProveedor;
    }

    public String getNombre() {
        return nombre;
    }

    public Modalidad getModalidad() {
        return modalidad;
    }

    public TipoMonto getTipoMonto() {
        return tipoMonto;
    }

    public BigDecimal getMontoFijo() {
        return montoFijo;
    }

    public BigDecimal getMontoMinimo() {
        return montoMinimo;
    }

    public BigDecimal getMontoMaximo() {
        return montoMaximo;
    }

    public BigDecimal getMultiplo() {
        return multiplo;
    }

    public Integer getLongitudReferencia() {
        return longitudReferencia;
    }

    public String getPatronReferencia() {
        return patronReferencia;
    }

    public BigDecimal getComision() {
        return comision;
    }

    public boolean isActivo() {
        return activo;
    }

    public OrigenCatalogo getOrigen() {
        return origen;
    }

    public String getLeyenda() {
        return leyenda;
    }

    public Instant getSincronizadoEn() {
        return sincronizadoEn;
    }
}
