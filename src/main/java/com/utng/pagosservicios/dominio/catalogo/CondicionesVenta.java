package com.utng.pagosservicios.dominio.catalogo;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Reglas con las que se vende un producto: modalidad, monto permitido y formato
 * de la referencia. La sincronizacion del catalogo las calcula a partir de lo
 * que publica el proveedor y el producto las aplica completas, sin mezclar
 * valores viejos con nuevos.
 */
public record CondicionesVenta(Modalidad modalidad, TipoMonto tipoMonto, BigDecimal montoFijo,
                               BigDecimal montoMinimo, BigDecimal montoMaximo, BigDecimal multiplo,
                               Integer longitudReferencia, String patronReferencia) {

    public CondicionesVenta {
        Objects.requireNonNull(modalidad, "modalidad");
        Objects.requireNonNull(tipoMonto, "tipoMonto");
        if (tipoMonto == TipoMonto.FIJO && (montoFijo == null || montoFijo.signum() <= 0)) {
            throw new IllegalArgumentException("Un producto de monto fijo necesita un monto mayor a cero");
        }
        if (tipoMonto == TipoMonto.ABIERTO && (montoMinimo == null || montoMaximo == null
                || montoMinimo.signum() <= 0 || montoMinimo.compareTo(montoMaximo) > 0)) {
            throw new IllegalArgumentException("Un producto de monto abierto necesita un rango valido");
        }
        if (modalidad == Modalidad.RECARGA && tipoMonto != TipoMonto.FIJO) {
            throw new IllegalArgumentException("En una recarga el precio lo fija el producto");
        }
    }

    public static CondicionesVenta montoFijo(Modalidad modalidad, BigDecimal monto,
                                             Integer longitudReferencia, String patronReferencia) {
        return new CondicionesVenta(modalidad, TipoMonto.FIJO, monto, null, null, null,
                longitudReferencia, patronReferencia);
    }

    public static CondicionesVenta montoAbierto(Modalidad modalidad, BigDecimal minimo, BigDecimal maximo,
                                                BigDecimal multiplo, Integer longitudReferencia,
                                                String patronReferencia) {
        return new CondicionesVenta(modalidad, TipoMonto.ABIERTO, null, minimo, maximo, multiplo,
                longitudReferencia, patronReferencia);
    }
}
