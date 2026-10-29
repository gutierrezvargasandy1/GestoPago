package com.utng.pagosservicios.dominio.cliente;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "cliente")
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Identificador con el que el cliente inicia sesion (subject del JWT). */
    @Column(nullable = false, unique = true, length = 100)
    private String usuario;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 200)
    private String nombre;

    @Column(nullable = false)
    private boolean activo;

    @Column(name = "pagos_habilitados", nullable = false)
    private boolean pagosHabilitados;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    protected Cliente() {
        // requerido por JPA
    }

    public Cliente(String usuario, String passwordHash, String nombre) {
        this.usuario = usuario;
        this.passwordHash = passwordHash;
        this.nombre = nombre;
        this.activo = true;
        this.pagosHabilitados = true;
        this.creadoEn = Instant.now();
    }

    /** Un cliente puede pagar solo si esta activo y tiene habilitada la operacion. */
    public boolean puedePagar() {
        return activo && pagosHabilitados;
    }

    public Long getId() {
        return id;
    }

    public String getUsuario() {
        return usuario;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getNombre() {
        return nombre;
    }

    public boolean isActivo() {
        return activo;
    }

    public boolean isPagosHabilitados() {
        return pagosHabilitados;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
