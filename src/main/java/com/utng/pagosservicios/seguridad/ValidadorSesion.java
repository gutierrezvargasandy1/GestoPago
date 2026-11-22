package com.utng.pagosservicios.seguridad;

import com.utng.pagosservicios.servicio.SesionService;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Un JWT bien firmado no basta: su sesion debe seguir abierta en la base. Asi
 * el servidor administra las sesiones de forma centralizada: cerrar sesion,
 * abrir otra en otro dispositivo o la inactividad invalidan el token al
 * instante, aunque todavia no haya vencido.
 */
@Component
public class ValidadorSesion implements OAuth2TokenValidator<Jwt> {

    /** Marca que el manejador de errores usa para responder SESION_FINALIZADA. */
    public static final String SESION_FINALIZADA = "SESION_FINALIZADA";

    private final SesionService sesiones;

    public ValidadorSesion(SesionService sesiones) {
        this.sesiones = sesiones;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        String sid = token.getClaimAsString(EmisorTokens.CLAIM_SESION);
        if (sid == null || !sesiones.registrarActividad(sid)) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token",
                    SESION_FINALIZADA + ": la sesion ya no esta activa", null));
        }
        return OAuth2TokenValidatorResult.success();
    }
}
