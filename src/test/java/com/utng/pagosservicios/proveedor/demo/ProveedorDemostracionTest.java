package com.utng.pagosservicios.proveedor.demo;

import com.utng.pagosservicios.dominio.catalogo.Modalidad;
import com.utng.pagosservicios.proveedor.OrdenPago;
import com.utng.pagosservicios.proveedor.ResultadoConsulta;
import com.utng.pagosservicios.proveedor.ResultadoEnvio.Desenlace;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ProveedorDemostracionTest {

    private final ProveedorDemostracion proveedor = new ProveedorDemostracion(Duration.ZERO);

    private static OrdenPago orden(String referencia) {
        return new OrdenPago("UPC123", 1001, 2001, Modalidad.RECARGA, referencia, new BigDecimal("50.00"),
                Instant.parse("2026-11-26T18:00:00Z"));
    }

    @Test
    void cadaTerminacionProduceSuEstado() {
        assertThat(proveedor.enviar(orden("4421234567")).desenlace()).isEqualTo(Desenlace.APROBADA);
        assertThat(proveedor.enviar(orden("4420000000")).desenlace()).isEqualTo(Desenlace.RECHAZADA);
        assertThat(proveedor.enviar(orden("4420000006")).desenlace()).isEqualTo(Desenlace.DUPLICADA);
        assertThat(proveedor.enviar(orden("4421235000")).desenlace()).isEqualTo(Desenlace.NO_PROCESADA);
        assertThat(proveedor.enviar(orden("4421239999")).desenlace()).isEqualTo(Desenlace.INDETERMINADA);
        assertThat(proveedor.enviar(orden("4421238282")).desenlace()).isEqualTo(Desenlace.INDETERMINADA);
        assertThat(proveedor.enviar(orden("4421237777")).desenlace()).isEqualTo(Desenlace.INDETERMINADA);
    }

    @Test
    void lasPendientesSeResuelvenAlConsultar() {
        assertThat(proveedor.consultar(orden("4421239999")).estado()).isEqualTo(ResultadoConsulta.Estado.APROBADA);
        assertThat(proveedor.consultar(orden("4421238282")).estado()).isEqualTo(ResultadoConsulta.Estado.NO_ENCONTRADA);
        assertThat(proveedor.consultar(orden("4421237777")).estado()).isEqualTo(ResultadoConsulta.Estado.INDETERMINADA);
    }

    @Test
    void elFolioDeUnaCompraAprobadaDiceQueEsDemostracion() {
        var resultado = proveedor.enviar(orden("4421234567"));
        assertThat(resultado.folio()).startsWith("DEMO-");
        assertThat(resultado.autorizacion()).startsWith("DEMO");
        assertThat(resultado.codigo()).isEqualTo("01");
    }
}
