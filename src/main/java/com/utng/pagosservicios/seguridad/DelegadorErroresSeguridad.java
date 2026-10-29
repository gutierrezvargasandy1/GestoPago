package com.utng.pagosservicios.seguridad;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Los errores 401/403 de Spring Security ocurren en filtros, antes de llegar a
 * los controladores, y por eso @RestControllerAdvice no los ve. Esta clase los
 * reenvia al mecanismo de resolucion de excepciones de Spring MVC para que
 * tambien salgan con el formato ProblemDetail del manejador global.
 */
@Component
public class DelegadorErroresSeguridad implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final HandlerExceptionResolver resolutor;

    public DelegadorErroresSeguridad(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolutor) {
        this.resolutor = resolutor;
    }

    @Override
    public void commence(HttpServletRequest solicitud, HttpServletResponse respuesta,
                         AuthenticationException excepcion) {
        resolutor.resolveException(solicitud, respuesta, null, excepcion);
    }

    @Override
    public void handle(HttpServletRequest solicitud, HttpServletResponse respuesta,
                       AccessDeniedException excepcion) {
        resolutor.resolveException(solicitud, respuesta, null, excepcion);
    }
}
