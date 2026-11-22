package com.utng.pagosservicios.seguridad;

import com.utng.pagosservicios.servicio.excepcion.CodigoError;
import com.utng.pagosservicios.servicio.excepcion.DosPasosException;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Collection;

/**
 * Valida el desafio que se entrega tras la contrasena. Es un decodificador
 * aparte (no un bean JwtDecoder) para que un desafio nunca se acepte como token
 * de acceso ni un token de acceso como desafio.
 */
@Component
public class VerificadorDesafios {

    private final NimbusJwtDecoder decodificador;

    public VerificadorDesafios(SecretKey claveJwt, SeguridadProperties propiedades) {
        decodificador = NimbusJwtDecoder.withSecretKey(claveJwt).macAlgorithm(MacAlgorithm.HS256).build();
        decodificador.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(propiedades.jwt().emisor()),
                new JwtClaimValidator<Object>("aud",
                        aud -> aud instanceof Collection<?> lista && lista.contains(EmisorTokens.AUDIENCIA_DESAFIO))));
    }

    /** Usuario del desafio, o 401 si esta vencido, alterado o no es un desafio. */
    public String usuarioDe(String desafio) {
        try {
            Jwt jwt = decodificador.decode(desafio);
            return jwt.getSubject();
        } catch (JwtException e) {
            throw new DosPasosException(CodigoError.DESAFIO_INVALIDO,
                    "La verificación expiró o no es válida. Vuelve a iniciar sesión.");
        }
    }
}
