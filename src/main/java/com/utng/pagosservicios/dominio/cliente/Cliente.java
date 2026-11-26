package com.utng.pagosservicios.dominio.cliente;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Duration;
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

    /** Secreto del segundo factor en Base32. Nunca sale en respuestas despues de activarse. */
    @Column(name = "totp_secreto", length = 64)
    private String totpSecreto;

    @Column(name = "totp_activo", nullable = false)
    private boolean totpActivo;

    @Column(name = "totp_ultimo_paso")
    private Long totpUltimoPaso;

    @Column(name = "totp_intentos", nullable = false)
    private int totpIntentos;

    @Column(name = "totp_bloqueado_hasta")
    private Instant totpBloqueadoHasta;

    /** Version de los terminos y condiciones que acepto al registrarse. */
    @Column(name = "terminos_version", length = 20)
    private String terminosVersion;

    @Column(name = "terminos_aceptados_en")
    private Instant terminosAceptadosEn;

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

    /** El secreto se genera una vez; mientras no se active, se puede volver a mostrar. */
    public void asignarSecretoTotp(String secreto) {
        if (totpSecreto == null) {
            totpSecreto = secreto;
        }
    }

    public boolean bloqueadoPorIntentos(Instant ahora) {
        return totpBloqueadoHasta != null && ahora.isBefore(totpBloqueadoHasta);
    }

    /** Tras demasiados codigos incorrectos el segundo factor se bloquea un tiempo (fuerza bruta). */
    public void registrarFalloTotp(Instant ahora, int maximoIntentos, Duration bloqueo) {
        totpIntentos++;
        if (totpIntentos >= maximoIntentos) {
            totpBloqueadoHasta = ahora.plus(bloqueo);
            totpIntentos = 0;
        }
    }

    public void registrarExitoTotp(long paso) {
        totpActivo = true;
        totpUltimoPaso = paso;
        totpIntentos = 0;
        totpBloqueadoHasta = null;
    }

    public void aceptarTerminos(String version, Instant momento) {
        this.terminosVersion = version;
        this.terminosAceptadosEn = momento;
    }

    public void cambiarNombre(String nuevoNombre) {
        this.nombre = nuevoNombre;
    }

    /** El usuario (correo) es el subject del JWT: quien lo cambia debe volver a iniciar sesion. */
    public void cambiarUsuario(String nuevoUsuario) {
        this.usuario = nuevoUsuario;
    }

    public void cambiarPasswordHash(String nuevoHash) {
        this.passwordHash = nuevoHash;
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

    public String getTotpSecreto() {
        return totpSecreto;
    }

    public boolean isTotpActivo() {
        return totpActivo;
    }

    public Long getTotpUltimoPaso() {
        return totpUltimoPaso;
    }

    public String getTerminosVersion() {
        return terminosVersion;
    }

    public Instant getTerminosAceptadosEn() {
        return terminosAceptadosEn;
    }
}
