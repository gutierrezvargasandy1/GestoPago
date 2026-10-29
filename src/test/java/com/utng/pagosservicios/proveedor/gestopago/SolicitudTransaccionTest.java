package com.utng.pagosservicios.proveedor.gestopago;

import com.jayway.jsonpath.JsonPath;
import com.utng.pagosservicios.dominio.catalogo.Modalidad;
import com.utng.pagosservicios.proveedor.OrdenPago;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SolicitudTransaccionTest {

    private static final ZoneId MEXICO = ZoneId.of("America/Mexico_City");
    private static final Instant MOMENTO = Instant.parse("2026-10-27T18:00:05Z");
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void recarga_soloLlevaLosCamposDeTiempoAire() {
        OrdenPago orden = new OrdenPago("123456789012345", 1001, 2003, Modalidad.RECARGA, "5512345678",
                new BigDecimal("100.00"), MOMENTO);

        Map<String, Object> campos = JsonPath.read(json.writeValueAsString(SolicitudTransaccion.de(orden, "CAJA-1", MEXICO)), "$");

        assertThat(campos).containsEntry("idProducto", 2003).containsEntry("idServicio", 1001)
                .containsEntry("telefono", "5512345678").containsEntry("upc", "123456789012345")
                .containsEntry("unidad", "CAJA-1").containsEntry("horaLocal", "20261027120005")
                .doesNotContainKey("referencia").doesNotContainKey("montoPago");
    }

    @Test
    void pagoDeServicio_llevaReferenciaYMontoConDosDecimales() {
        OrdenPago orden = new OrdenPago("123456789012345", 1003, 3001, Modalidad.PAGO_SERVICIO, "123456789012",
                new BigDecimal("250.5"), MOMENTO);

        String texto = json.writeValueAsString(SolicitudTransaccion.de(orden, "CAJA-1", MEXICO));

        assertThat(texto).contains("\"montoPago\":250.50").contains("\"referencia\":\"123456789012\"")
                .doesNotContain("telefono");
    }

    @Test
    void laHoraLocalUsaLaZonaConfigurada() {
        OrdenPago orden = new OrdenPago("123456789012345", 1, 2, Modalidad.RECARGA, "5512345678",
                new BigDecimal("20.00"), Instant.parse("2026-01-01T05:59:59Z"));
        assertThat(SolicitudTransaccion.de(orden, "CAJA-1", MEXICO).horaLocal()).isEqualTo("20251231235959");
    }

    @Test
    void sinIdentificadoresDelProveedorNoSePuedeArmar() {
        OrdenPago orden = new OrdenPago("123456789012345", null, 2, Modalidad.RECARGA, "5512345678",
                new BigDecimal("20.00"), MOMENTO);
        assertThatThrownBy(() -> SolicitudTransaccion.de(orden, "CAJA-1", MEXICO))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void laCopiaParaLaBitacoraEnmascaraTelefonoYReferencia() {
        OrdenPago orden = new OrdenPago("123456789012345", 1, 2, Modalidad.RECARGA, "5512345678",
                new BigDecimal("20.00"), MOMENTO);
        SolicitudTransaccion enmascarada = SolicitudTransaccion.de(orden, "CAJA-1", MEXICO).enmascarada();
        assertThat(enmascarada.telefono()).isEqualTo("******5678");
        assertThat(enmascarada.upc()).isEqualTo("123456789012345");
    }
}
