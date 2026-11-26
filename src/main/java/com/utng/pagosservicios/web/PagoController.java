package com.utng.pagosservicios.web;

import com.utng.pagosservicios.dominio.pago.EstadoTransaccion;
import com.utng.pagosservicios.servicio.ClaveIdempotencia;
import com.utng.pagosservicios.servicio.HistorialService;
import com.utng.pagosservicios.servicio.PagoService;
import com.utng.pagosservicios.servicio.ResultadoPago;
import com.utng.pagosservicios.web.dto.CompraResponse;
import com.utng.pagosservicios.web.dto.PagoResponse;
import com.utng.pagosservicios.web.dto.SolicitudPagoRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.format.annotation.DateTimeFormat;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

/**
 * Capa delgada: recibe, delega y traduce a HTTP. La logica vive en PagoService.
 *
 * <p>Codigos de respuesta exitosos:
 * <ul>
 *   <li>201 Created: el pago se confirmo.</li>
 *   <li>202 Accepted: resultado pendiente (EN_CONCILIACION o REVISION_MANUAL);
 *       el monto queda retenido.</li>
 *   <li>200 OK + Idempotent-Replayed: la clave ya se habia usado; se devuelve la
 *       operacion original sin volver a cobrar.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/pagos")
public class PagoController {

    static final String ENCABEZADO_IDEMPOTENCIA = "Idempotency-Key";
    static final String ENCABEZADO_REPETIDA = "Idempotent-Replayed";

    private final PagoService pagos;
    private final HistorialService historial;

    public PagoController(PagoService pagos, HistorialService historial) {
        this.pagos = pagos;
        this.historial = historial;
    }

    @PostMapping
    public ResponseEntity<PagoResponse> pagar(@RequestHeader(ENCABEZADO_IDEMPOTENCIA) String clave,
                                              @Valid @RequestBody SolicitudPagoRequest solicitud,
                                              Authentication autenticacion) {
        ResultadoPago resultado = pagos.pagar(autenticacion.getName(), ClaveIdempotencia.de(clave),
                solicitud.aSolicitud());

        ResponseEntity.BodyBuilder respuesta = ResponseEntity
                .status(estadoHttp(resultado))
                .location(URI.create("/api/v1/pagos/" + resultado.transaccionId()));
        if (resultado.repetido()) {
            respuesta.header(ENCABEZADO_REPETIDA, "true");
        }
        return respuesta.body(PagoResponse.de(resultado));
    }

    /**
     * Todas las compras del cliente, en cualquier estado, la mas reciente primero.
     *
     * @param estado TODAS, EXITOSAS, PENDIENTES o RECHAZADAS
     * @param texto  busca en producto, servicio, referencia, folio, autorizacion y numero de operacion
     * @param desde  fecha inicial (yyyy-MM-dd), incluida
     * @param hasta  fecha final (yyyy-MM-dd), incluida
     */
    @GetMapping
    public List<CompraResponse> buscar(
            @RequestParam(name = "estado", defaultValue = "TODAS") HistorialService.Grupo estado,
            @RequestParam(name = "texto", required = false) String texto,
            @RequestParam(name = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(name = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            Authentication autenticacion) {
        return historial.buscar(autenticacion.getName(), estado, texto, desde, hasta).stream()
                .map(CompraResponse::de).toList();
    }

    /** Compras exitosas del cliente (solo CONFIRMADA), la mas reciente primero. */
    @GetMapping("/historial")
    public List<CompraResponse> historial(Authentication autenticacion) {
        return historial.compras(autenticacion.getName()).stream().map(CompraResponse::de).toList();
    }

    @GetMapping("/{id}")
    public PagoResponse consultar(@PathVariable("id") Long id, Authentication autenticacion) {
        return PagoResponse.de(pagos.consultar(autenticacion.getName(), id));
    }

    private static HttpStatus estadoHttp(ResultadoPago resultado) {
        if (!resultado.estado().esTerminal()) {
            return HttpStatus.ACCEPTED;
        }
        if (resultado.repetido()) {
            return HttpStatus.OK;
        }
        return resultado.estado() == EstadoTransaccion.CONFIRMADA ? HttpStatus.CREATED : HttpStatus.OK;
    }
}
