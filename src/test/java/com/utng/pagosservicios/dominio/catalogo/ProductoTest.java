package com.utng.pagosservicios.dominio.catalogo;

import com.utng.pagosservicios.Fixtures;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static com.utng.pagosservicios.dominio.catalogo.Producto.ResultadoMonto.DEMASIADOS_DECIMALES;
import static com.utng.pagosservicios.dominio.catalogo.Producto.ResultadoMonto.FUERA_DE_RANGO;
import static com.utng.pagosservicios.dominio.catalogo.Producto.ResultadoMonto.NO_COINCIDE_CON_MONTO_FIJO;
import static com.utng.pagosservicios.dominio.catalogo.Producto.ResultadoMonto.NO_RESPETA_MULTIPLO;
import static com.utng.pagosservicios.dominio.catalogo.Producto.ResultadoMonto.VALIDO;
import static org.assertj.core.api.Assertions.assertThat;

class ProductoTest {

    @Nested
    class MontoFijo {

        private final Producto telcel = Fixtures.telcel100();

        @Test
        void aceptaElMontoExactoSinImportarLaEscala() {
            assertThat(telcel.validarMonto(new BigDecimal("100"))).isEqualTo(VALIDO);
            assertThat(telcel.validarMonto(new BigDecimal("100.00"))).isEqualTo(VALIDO);
        }

        @ParameterizedTest
        @ValueSource(strings = {"99.99", "100.01", "50.00", "1000.00"})
        void rechazaCualquierOtroMonto(String monto) {
            assertThat(telcel.validarMonto(new BigDecimal(monto))).isEqualTo(NO_COINCIDE_CON_MONTO_FIJO);
        }

        @ParameterizedTest
        @ValueSource(strings = {"0", "-100.00"})
        void rechazaCeroYNegativos(String monto) {
            assertThat(telcel.validarMonto(new BigDecimal(monto))).isEqualTo(NO_COINCIDE_CON_MONTO_FIJO);
        }
    }

    @Nested
    class MontoAbierto {

        private final Producto cfe = Fixtures.cfe();

        @ParameterizedTest
        @CsvSource({"10.00", "10.50", "250.00", "9999.50", "10000.00"})
        void aceptaMontosDentroDelRangoYMultiplos(String monto) {
            assertThat(cfe.validarMonto(new BigDecimal(monto))).isEqualTo(VALIDO);
        }

        @ParameterizedTest
        @CsvSource({"9.50", "10000.50", "0", "-20.00"})
        void rechazaMontosFueraDeRango(String monto) {
            assertThat(cfe.validarMonto(new BigDecimal(monto))).isEqualTo(FUERA_DE_RANGO);
        }

        @ParameterizedTest
        @CsvSource({"10.25", "100.10", "333.33"})
        void rechazaMontosQueNoSonMultiplo(String monto) {
            assertThat(cfe.validarMonto(new BigDecimal(monto))).isEqualTo(NO_RESPETA_MULTIPLO);
        }

        @Test
        void rechazaMasDeDosDecimales() {
            assertThat(cfe.validarMonto(new BigDecimal("100.005"))).isEqualTo(DEMASIADOS_DECIMALES);
        }

        @Test
        void elTotalIncluyeLaComision() {
            assertThat(cfe.totalACobrar(new BigDecimal("250.50"))).isEqualByComparingTo("258.50");
        }
    }

    @Nested
    class Referencia {

        private final Producto telcel = Fixtures.telcel100();

        @Test
        void aceptaDiezDigitos() {
            assertThat(telcel.referenciaValida("5512345678")).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {"551234567", "55123456789", "55123A5678", " ", ""})
        void rechazaLongitudOFormatoIncorrecto(String referencia) {
            assertThat(telcel.referenciaValida(referencia)).isFalse();
        }

        @Test
        void rechazaNulo() {
            assertThat(telcel.referenciaValida(null)).isFalse();
        }
    }

    @Test
    void noEstaDisponibleSiElProductoOElServicioEstanInactivos() {
        Producto producto = Fixtures.telcel100();
        assertThat(producto.disponible()).isTrue();

        producto.desactivar();
        assertThat(producto.disponible()).isFalse();

        Producto otro = Fixtures.telcel100();
        otro.getServicio().desactivar();
        assertThat(otro.disponible()).isFalse();
    }
}
