package com.utng.pagosservicios.dominio.catalogo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "servicio")
public class Servicio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Codigo con el que los clientes de la API piden el servicio. */
    @Column(name = "id_servicio", nullable = false, unique = true, length = 50)
    private String idServicio;

    /** Identificador numerico del servicio en PuntoRed; es el que viaja en sendTx. */
    @Column(name = "id_servicio_proveedor")
    private Integer idServicioProveedor;

    @Column(nullable = false, length = 200)
    private String nombre;

    @Column(length = 50)
    private String categoria;

    @Column(nullable = false)
    private boolean activo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrigenCatalogo origen;

    @Column(name = "sincronizado_en")
    private Instant sincronizadoEn;

    protected Servicio() {
        // requerido por JPA
    }

    /** Servicio del catalogo de ejemplo. */
    public Servicio(String idServicio, String nombre, String categoria) {
        this.idServicio = Objects.requireNonNull(idServicio);
        this.nombre = nombre;
        this.categoria = categoria;
        this.activo = true;
        this.origen = OrigenCatalogo.SEMILLA;
    }

    /** Servicio publicado por el proveedor. Su codigo en la API es el mismo numero del proveedor. */
    public static Servicio desdeProveedor(int idServicioProveedor, String nombre, String categoria, Instant ahora) {
        Servicio servicio = new Servicio(String.valueOf(idServicioProveedor), nombre, categoria);
        servicio.idServicioProveedor = idServicioProveedor;
        servicio.origen = OrigenCatalogo.PROVEEDOR;
        servicio.sincronizadoEn = ahora;
        return servicio;
    }

    public void actualizarDesdeProveedor(String nuevoNombre, String nuevaCategoria, Instant ahora) {
        this.nombre = nuevoNombre;
        this.categoria = nuevaCategoria;
        this.activo = true;
        this.sincronizadoEn = ahora;
    }

    public void desactivar() {
        activo = false;
    }

    public Long getId() {
        return id;
    }

    public String getIdServicio() {
        return idServicio;
    }

    public Integer getIdServicioProveedor() {
        return idServicioProveedor;
    }

    public String getNombre() {
        return nombre;
    }

    public String getCategoria() {
        return categoria;
    }

    public boolean isActivo() {
        return activo;
    }

    public OrigenCatalogo getOrigen() {
        return origen;
    }

    public Instant getSincronizadoEn() {
        return sincronizadoEn;
    }
}
