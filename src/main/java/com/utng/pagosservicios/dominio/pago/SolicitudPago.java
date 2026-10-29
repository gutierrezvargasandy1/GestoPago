package com.utng.pagosservicios.dominio.pago;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Instruccion de pago tal como la pide el cliente. */
public record SolicitudPago(String numeroCuenta, String idServicio, String idProducto,
                            String referencia, BigDecimal monto) {

    public SolicitudPago {
        Objects.requireNonNull(numeroCuenta, "numeroCuenta");
        Objects.requireNonNull(idServicio, "idServicio");
        Objects.requireNonNull(idProducto, "idProducto");
        Objects.requireNonNull(referencia, "referencia");
        Objects.requireNonNull(monto, "monto");
    }

    /**
     * SHA-256 de los parametros en forma canonica. Si un cliente reutiliza una
     * clave de idempotencia con datos distintos, la huella no coincide y la
     * peticion se rechaza en lugar de devolver el resultado de otra operacion.
     * El monto se normaliza para que 100 y 100.00 produzcan la misma huella.
     */
    public String huella() {
        String canonica = String.join("|", numeroCuenta, idServicio, idProducto, referencia,
                monto.stripTrailingZeros().toPlainString());
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha256.digest(canonica.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible en la JVM", e);
        }
    }
}
