package com.utng.pagosservicios.proveedor.gestopago;

/** Origen del token Bearer que se envia al proveedor. */
public interface CredencialGestopago {

    /** Token vigente. Nunca debe registrarse en logs. */
    String token();

    /** Descarta el token en cache (p. ej. tras un 403 "EXPIRED") para obtener uno nuevo. */
    default void invalidar() {
    }

    /**
     * true si, tras invalidarla, puede obtener un token nuevo por si misma. Solo
     * entonces tiene sentido repetir una peticion que el proveedor rechazo por
     * credencial (un token fijo seguiria siendo el mismo token vencido).
     */
    default boolean renovable() {
        return false;
    }
}
