package com.utng.pagosservicios.proveedor.gestopago;

/** Token entregado por el proveedor y configurado en la variable GESTOPAGO_TOKEN. */
final class TokenEstatico implements CredencialGestopago {

    private static final String PREFIJO = "Bearer ";

    private final String token;

    TokenEstatico(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("El token de Gestopago esta vacio");
        }
        String limpio = token.strip();
        // Tolera que se haya pegado el encabezado completo ("Bearer xyz").
        if (limpio.regionMatches(true, 0, PREFIJO, 0, PREFIJO.length())) {
            limpio = limpio.substring(PREFIJO.length()).strip();
        }
        this.token = limpio;
    }

    @Override
    public String token() {
        return token;
    }

    @Override
    public String toString() {
        return "TokenEstatico[****]";
    }
}
