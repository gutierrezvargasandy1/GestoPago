package com.utng.pagosservicios.proveedor.gestopago;

import com.utng.pagosservicios.dominio.catalogo.Modalidad;
import com.utng.pagosservicios.proveedor.DatosCanje;
import com.utng.pagosservicios.proveedor.OrdenPago;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/** Ningun objeto que contenga secretos debe revelarlos al convertirse en texto (logs, depuracion). */
class CredencialesNoExpuestasTest {

    @Test
    void lasPropiedadesNoMuestranTokenApiKeyContrasenaNiLlaveAes() {
        GestopagoProperties propiedades = PropiedadesDePrueba.gestopago("https://proveedor", "", "token-super-secreto");
        String texto = propiedades.toString();
        assertThat(texto).doesNotContain("token-super-secreto").doesNotContain("secreto+distribuidor")
                .doesNotContain("DIST-1").doesNotContain(PropiedadesDePrueba.API_KEY)
                .doesNotContain(MensajeCifrado.LLAVE_PRUEBAS).doesNotContain(MensajeCifrado.IV_PRUEBAS)
                .contains("****");
    }

    @Test
    void elTokenEstaticoNoSeMuestraYToleraElPrefijoBearer() {
        TokenEstatico token = new TokenEstatico("Bearer abc.def.ghi ");
        assertThat(token.token()).isEqualTo("abc.def.ghi");
        assertThat(token.toString()).doesNotContain("abc.def.ghi");
    }

    @Test
    void laRespuestaDeAutenticacionNoMuestraElToken() {
        assertThat(new RespuestaAutenticacion("tkn-123", null, null, 60L, true).toString()).doesNotContain("tkn-123");
    }

    @Test
    void elCifradorNoMuestraLaLlave() {
        CifradorAes cifrador = new CifradorAes(MensajeCifrado.LLAVE_PRUEBAS, MensajeCifrado.IV_PRUEBAS);
        assertThat(cifrador.toString()).doesNotContain(MensajeCifrado.LLAVE_PRUEBAS)
                .doesNotContain(MensajeCifrado.IV_PRUEBAS);
    }

    @Test
    void elPinNoApareceEnLosLogs() {
        assertThat(new DatosCanje("1234-5678", "Canje").toString()).doesNotContain("1234-5678");
    }

    @Test
    void laOrdenYLaSolicitudNoMuestranLaReferenciaCompleta() {
        OrdenPago orden = new OrdenPago("123456789012345", 1, 2, Modalidad.RECARGA, "5512345678",
                new BigDecimal("100.00"), Instant.parse("2026-10-27T18:00:00Z"));
        assertThat(orden.toString()).doesNotContain("5512345678").contains("5678");
        assertThat(SolicitudTransaccion.de(orden, "API-PAGOS", ZoneId.of("America/Mexico_City")).toString())
                .doesNotContain("5512345678");
    }
}
