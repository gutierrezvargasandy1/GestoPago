package com.utng.pagosservicios.web.error;

import com.utng.pagosservicios.servicio.excepcion.CodigoError;
import com.utng.pagosservicios.servicio.excepcion.ConflictoException;
import com.utng.pagosservicios.servicio.excepcion.ProveedorNoDisponibleException;
import com.utng.pagosservicios.servicio.excepcion.ReglaNegocioException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ManejadorGlobalExcepcionesTest {

    private final ManejadorGlobalExcepciones manejador = new ManejadorGlobalExcepciones();
    private final MockHttpServletRequest solicitud = new MockHttpServletRequest("POST", "/api/v1/pagos");

    @Test
    void reglaDeNegocioEs422ConCodigoEstable() {
        ResponseEntity<ProblemDetail> respuesta = manejador.negocio(
                new ReglaNegocioException(CodigoError.SALDO_INSUFICIENTE, "Saldo insuficiente."), solicitud);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(422);
        ProblemDetail cuerpo = respuesta.getBody();
        assertThat(cuerpo).isNotNull();
        assertThat(cuerpo.getProperties()).containsEntry("codigo", "SALDO_INSUFICIENTE");
        assertThat(cuerpo.getDetail()).isEqualTo("Saldo insuficiente.");
    }

    @Test
    void conflictoIncluyeLaTransaccionRelacionada() {
        ResponseEntity<ProblemDetail> respuesta = manejador.negocio(
                new ConflictoException(CodigoError.PAGO_EN_PROCESO, "En curso.", 42L), solicitud);
        assertThat(respuesta.getStatusCode().value()).isEqualTo(409);
        assertThat(respuesta.getBody().getProperties()).containsEntry("transaccionId", 42L);
    }

    @Test
    void proveedorNoDisponibleIndicaCuandoReintentar() {
        ResponseEntity<ProblemDetail> respuesta = manejador.negocio(new ProveedorNoDisponibleException(null), solicitud);
        assertThat(respuesta.getStatusCode().value()).isEqualTo(503);
        assertThat(respuesta.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("30");
    }

    @Test
    void unErrorInesperadoNoExponeDetallesInternos() {
        DataIntegrityViolationException interna = new DataIntegrityViolationException(
                "ERROR: new row violates check constraint \"chk_cuenta_saldo\" Failing row contains (1, 7, 1000000001, -5.00)");

        ResponseEntity<ProblemDetail> respuesta = manejador.general(interna, solicitud);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(500);
        ProblemDetail cuerpo = respuesta.getBody();
        assertThat(cuerpo.getDetail()).doesNotContain("chk_cuenta_saldo").doesNotContain("1000000001");
        assertThat(cuerpo.getProperties()).containsEntry("codigo", "ERROR_INTERNO");
    }

    @ParameterizedTest
    @EnumSource(CodigoError.class)
    void todoCodigoTieneUnEstadoHttp(CodigoError codigo) {
        HttpStatus status = ManejadorGlobalExcepciones.estadoHttp(codigo);
        assertThat(status.value()).isBetween(400, 599);
    }
}
