package com.utng.pagosservicios.proveedor;

/**
 * Lo que el cliente necesita para usar una tarjeta de regalo: el PIN y las
 * instrucciones de canje. El PIN equivale a dinero; nunca se imprime en logs.
 */
public record DatosCanje(String pin, String instrucciones) {

    /** Devuelve null si el proveedor no envio datos de canje. */
    public static DatosCanje de(String pin, String instrucciones) {
        boolean sinPin = pin == null || pin.isBlank();
        boolean sinInstrucciones = instrucciones == null || instrucciones.isBlank();
        return sinPin && sinInstrucciones ? null : new DatosCanje(sinPin ? null : pin, sinInstrucciones ? null : instrucciones);
    }

    @Override
    public String toString() {
        return "DatosCanje[pin=" + (pin == null ? "(sin PIN)" : "****") + "]";
    }
}
