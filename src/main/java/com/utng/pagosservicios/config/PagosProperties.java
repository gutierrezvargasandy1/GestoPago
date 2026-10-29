package com.utng.pagosservicios.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZoneId;

@Validated
@ConfigurationProperties(prefix = "pagos")
public record PagosProperties(@NotBlank String zonaHoraria,
                              @NotNull @Valid Conciliacion conciliacion,
                              @NotNull @Valid Catalogo catalogo,
                              @NotNull @Valid BaseDeDatos baseDeDatos) {

    public PagosProperties {
        if (zonaHoraria != null) {
            ZoneId.of(zonaHoraria); // falla al arrancar si la zona no existe
        }
    }

    /** Zona en la que se calcula "hoy" para el limite diario. */
    public ZoneId zona() {
        return ZoneId.of(zonaHoraria);
    }

    /**
     * @param vigenciaConsulta tiempo, desde el envio, durante el que el proveedor
     *                         permite preguntar por una operacion (confirmTx: 24 h).
     *                         Despues ya no se consulta: pasa a revision manual.
     */
    public record Conciliacion(
            boolean habilitada,
            @NotNull Duration intervalo,
            @Positive int tamanoLote,
            @NotNull Duration margenInicial,
            @NotNull Duration esperaBase,
            @NotNull Duration esperaMaxima,
            @Positive int maxIntentos,
            @Positive int confirmacionesNoEncontrada,
            @NotNull Duration arrendamiento,
            @NotNull Duration vigenciaConsulta) {
    }

    /**
     * @param permitirEjemplo           true solo con el simulador y en pruebas: habilita el
     *                                  catalogo de ejemplo, cuyos identificadores no existen
     *                                  en el proveedor real
     * @param frecuenciaMinima          tiempo minimo entre sincronizaciones exitosas
     * @param maxLlamadasPorDia         limite del proveedor para getProductList en 24 h
     * @param montoMinimoServicios      rango que se aplica a los productos sin precio (pago
     *                                  de servicio con monto capturado por el cliente)
     */
    public record Catalogo(
            boolean permitirEjemplo,
            boolean sincronizacionHabilitada,
            @NotBlank String cron,
            @NotNull Duration frecuenciaMinima,
            @Positive int maxLlamadasPorDia,
            @NotNull @Positive BigDecimal montoMinimoServicios,
            @NotNull @Positive BigDecimal montoMaximoServicios) {
    }

    /**
     * Esquema donde viven las tablas. Se valida porque su valor termina dentro de
     * una sentencia SQL (SET search_path).
     */
    public record BaseDeDatos(
            @NotBlank @Pattern(regexp = "^[a-z_][a-z0-9_]{0,62}$",
                    message = "debe ser un identificador de PostgreSQL en minusculas") String esquema) {
    }
}
