package com.utng.pagosservicios.web;

import com.utng.pagosservicios.util.Trazabilidad;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Asigna un identificador a cada peticion, lo pone en el MDC (aparece en cada
 * linea de log), lo devuelve en X-Request-Id y se guarda en la transaccion.
 * Con el, un error que reporta un cliente se puede rastrear en los logs sin
 * pedirle datos sensibles.
 *
 * <p>Corre antes que Spring Security para que tambien los 401 lleven traza.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FiltroTrazabilidad extends OncePerRequestFilter {

    /** Solo se acepta un id entrante con formato seguro: evita inyectar texto en los logs. */
    private static final Pattern FORMATO = Pattern.compile("^[A-Za-z0-9-]{8,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest solicitud, HttpServletResponse respuesta,
                                    FilterChain cadena) throws ServletException, IOException {
        String entrante = solicitud.getHeader(Trazabilidad.ENCABEZADO);
        String traceId = entrante != null && FORMATO.matcher(entrante).matches()
                ? entrante
                : UUID.randomUUID().toString();
        MDC.put(Trazabilidad.CLAVE_MDC, traceId);
        respuesta.setHeader(Trazabilidad.ENCABEZADO, traceId);
        try {
            cadena.doFilter(solicitud, respuesta);
        } finally {
            MDC.remove(Trazabilidad.CLAVE_MDC);
        }
    }
}
