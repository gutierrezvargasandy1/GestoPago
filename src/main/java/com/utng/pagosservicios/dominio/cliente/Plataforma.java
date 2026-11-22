package com.utng.pagosservicios.dominio.cliente;

/** Desde donde se abrio la sesion. */
public enum Plataforma {
    WEB("la aplicación web"),
    MOVIL("la aplicación móvil");

    private final String descripcion;

    Plataforma(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
