package com.utng.pagosservicios.web.error;

import com.utng.pagosservicios.servicio.excepcion.CodigoError;
import com.utng.pagosservicios.servicio.excepcion.PagoException;
import com.utng.pagosservicios.util.DescripcionError;
import com.utng.pagosservicios.util.Trazabilidad;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Manejo centralizado de errores. Todas las respuestas de error usan el formato
 * ProblemDetail (RFC 9457) con un "codigo" estable que el cliente puede
 * programar y un "traceId" para soporte.
 *
 * <p>Nunca se devuelven mensajes de excepciones internas, trazas, SQL ni datos
 * de la peticion: los mensajes salen de nuestras propias excepciones, que se
 * redactan para el cliente.
 */
@RestControllerAdvice
public class ManejadorGlobalExcepciones {

    private static final Logger log = LoggerFactory.getLogger(ManejadorGlobalExcepciones.class);

    @ExceptionHandler(PagoException.class)
    public ResponseEntity<ProblemDetail> negocio(PagoException ex, HttpServletRequest solicitud) {
        HttpStatus status = estadoHttp(ex.getCodigo());
        log.info("Solicitud rechazada {} {}: {} ({})", solicitud.getMethod(), solicitud.getRequestURI(),
                ex.getCodigo(), status.value());
        ProblemDetail problema = problema(status, ex.getCodigo(), ex.getMessage(), solicitud);
        if (ex.getTransaccionId() != null) {
            problema.setProperty("transaccionId", ex.getTransaccionId());
        }
        ResponseEntity.BodyBuilder respuesta = ResponseEntity.status(status);
        if (status == HttpStatus.SERVICE_UNAVAILABLE) {
            respuesta.header(HttpHeaders.RETRY_AFTER, "30");
        }
        return respuesta.body(problema);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> validacion(MethodArgumentNotValidException ex, HttpServletRequest solicitud) {
        ProblemDetail problema = problema(HttpStatus.BAD_REQUEST, CodigoError.SOLICITUD_INVALIDA,
                "La solicitud tiene campos inválidos.", solicitud);
        // Se informa campo y regla incumplida, nunca el valor recibido.
        List<Map<String, String>> errores = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of(
                        "campo", error.getField(),
                        "mensaje", Objects.requireNonNullElse(error.getDefaultMessage(), "Valor inválido")))
                .toList();
        problema.setProperty("errores", errores);
        return ResponseEntity.badRequest().body(problema);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> cuerpoIlegible(HttpMessageNotReadableException ex, HttpServletRequest solicitud) {
        // El mensaje del parser puede contener fragmentos del cuerpo; no se reenvia.
        return ResponseEntity.badRequest().body(problema(HttpStatus.BAD_REQUEST, CodigoError.SOLICITUD_INVALIDA,
                "El cuerpo no es un JSON válido, tiene tipos incorrectos o incluye campos no permitidos.", solicitud));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ProblemDetail> encabezadoFaltante(MissingRequestHeaderException ex, HttpServletRequest solicitud) {
        return ResponseEntity.badRequest().body(problema(HttpStatus.BAD_REQUEST, CodigoError.SOLICITUD_INVALIDA,
                "Falta el encabezado obligatorio " + ex.getHeaderName() + ".", solicitud));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> tipoInvalido(MethodArgumentTypeMismatchException ex, HttpServletRequest solicitud) {
        return ResponseEntity.badRequest().body(problema(HttpStatus.BAD_REQUEST, CodigoError.SOLICITUD_INVALIDA,
                "El parámetro '" + ex.getName() + "' tiene un formato inválido.", solicitud));
    }

    /** Llega desde el filtro de seguridad a traves de DelegadorErroresSeguridad. */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> noAutenticado(AuthenticationException ex, HttpServletRequest solicitud) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .body(problema(HttpStatus.UNAUTHORIZED, CodigoError.NO_AUTENTICADO,
                        "Se requiere un token válido y vigente en el encabezado Authorization.", solicitud));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> accesoDenegado(AccessDeniedException ex, HttpServletRequest solicitud) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problema(HttpStatus.FORBIDDEN,
                CodigoError.ACCESO_DENEGADO, "El token no tiene permiso para esta operación.", solicitud));
    }

    /** Candado de cuenta ocupado mas de 3 s, o version cambiada por otro proceso. Nada se aplico. */
    @ExceptionHandler({OptimisticLockingFailureException.class, PessimisticLockingFailureException.class})
    public ResponseEntity<ProblemDetail> concurrencia(RuntimeException ex, HttpServletRequest solicitud) {
        log.warn("Conflicto de concurrencia en {} {}: {}", solicitud.getMethod(), solicitud.getRequestURI(),
                DescripcionError.de(ex));
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problema(HttpStatus.CONFLICT,
                CodigoError.OPERACION_CONCURRENTE,
                "Otra operación sobre la misma cuenta está en curso. Intente de nuevo en unos segundos.", solicitud));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> general(Exception ex, HttpServletRequest solicitud) {
        if (ex instanceof ErrorResponse errorSpring) {
            // Excepciones estandar de Spring MVC (ruta inexistente, metodo no permitido,
            // tipo de contenido...): se conserva su codigo HTTP con un mensaje propio.
            HttpStatusCode status = errorSpring.getStatusCode();
            return ResponseEntity.status(status)
                    .headers(errorSpring.getHeaders())
                    .body(problema(status, codigoPara(status), mensajePara(status), solicitud));
        }
        log.error("Error no controlado en {} {}: {}", solicitud.getMethod(), solicitud.getRequestURI(),
                DescripcionError.de(ex));
        log.debug("Detalle del error no controlado", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problema(
                HttpStatus.INTERNAL_SERVER_ERROR, CodigoError.ERROR_INTERNO,
                "Ocurrió un error interno. Si estaba realizando un pago, consulte su estado "
                        + "con la misma clave de idempotencia antes de reintentar.", solicitud));
    }

    /** Traduccion de codigos de negocio a HTTP. El switch exhaustivo obliga a mapear cada codigo nuevo. */
    static HttpStatus estadoHttp(CodigoError codigo) {
        return switch (codigo) {
            case SOLICITUD_INVALIDA, CLAVE_IDEMPOTENCIA_INVALIDA -> HttpStatus.BAD_REQUEST;
            case NO_AUTENTICADO, CREDENCIALES_INVALIDAS -> HttpStatus.UNAUTHORIZED;
            case ACCESO_DENEGADO, CLIENTE_NO_HABILITADO, CUENTA_NO_AUTORIZADA -> HttpStatus.FORBIDDEN;
            case RECURSO_NO_ENCONTRADO, PAGO_NO_ENCONTRADO -> HttpStatus.NOT_FOUND;
            case METODO_NO_PERMITIDO -> HttpStatus.METHOD_NOT_ALLOWED;
            case TIPO_CONTENIDO_NO_SOPORTADO -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
            case CLAVE_IDEMPOTENCIA_REUTILIZADA, PAGO_EN_PROCESO, OPERACION_CONCURRENTE, PAGO_DUPLICADO,
                 USUARIO_YA_REGISTRADO -> HttpStatus.CONFLICT;
            case CUENTA_INACTIVA, SALDO_INSUFICIENTE, LIMITE_DIARIO_EXCEDIDO, PRODUCTO_NO_DISPONIBLE,
                 MONTO_NO_PERMITIDO, REFERENCIA_INVALIDA, PAGO_RECHAZADO -> HttpStatus.UNPROCESSABLE_CONTENT;
            case PROVEEDOR_NO_DISPONIBLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case ERROR_INTERNO -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    private static CodigoError codigoPara(HttpStatusCode status) {
        return switch (status.value()) {
            case 404 -> CodigoError.RECURSO_NO_ENCONTRADO;
            case 405 -> CodigoError.METODO_NO_PERMITIDO;
            case 415 -> CodigoError.TIPO_CONTENIDO_NO_SOPORTADO;
            default -> status.is4xxClientError() ? CodigoError.SOLICITUD_INVALIDA : CodigoError.ERROR_INTERNO;
        };
    }

    private static String mensajePara(HttpStatusCode status) {
        return switch (status.value()) {
            case 404 -> "El recurso solicitado no existe.";
            case 405 -> "El método HTTP no está permitido para este recurso.";
            case 415 -> "Tipo de contenido no soportado; use application/json.";
            default -> status.is4xxClientError() ? "La solicitud no es válida." : "Ocurrió un error interno.";
        };
    }

    static ProblemDetail problema(HttpStatusCode status, CodigoError codigo, String detalle,
                                  HttpServletRequest solicitud) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalle);
        problema.setTitle(codigo.getTitulo());
        problema.setType(URI.create("urn:pagos:error:" + codigo.name().toLowerCase(Locale.ROOT).replace('_', '-')));
        try {
            problema.setInstance(URI.create(solicitud.getRequestURI()));
        } catch (IllegalArgumentException e) {
            // ruta con caracteres no validos para una URI: se omite instance
        }
        problema.setProperty("codigo", codigo.name());
        String traceId = MDC.get(Trazabilidad.CLAVE_MDC);
        if (traceId != null) {
            problema.setProperty("traceId", traceId);
        }
        problema.setProperty("timestamp", Instant.now().toString());
        return problema;
    }
}
