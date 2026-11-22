package com.utng.pagosservicios.seguridad;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Emite dos tipos de JWT:
 * <ul>
 *   <li><b>Desafio</b> (5 min, audiencia "2fa"): prueba que la contrasena fue
 *       correcta. Solo sirve para POST /api/v1/auth/2fa; no abre ninguna ruta.</li>
 *   <li><b>Acceso</b>: lleva el sid de la sesion. Cada peticion comprueba en la
 *       base que esa sesion siga abierta (ValidadorSesion).</li>
 * </ul>
 */
@Component
public class EmisorTokens {

    public static final String SCOPE_PAGOS = "pagos";
    public static final String AUDIENCIA_DESAFIO = "2fa";
    public static final String CLAIM_SESION = "sid";

    private final JwtEncoder codificador;
    private final SeguridadProperties.Jwt configuracion;
    private final Clock reloj;

    public EmisorTokens(JwtEncoder codificador, SeguridadProperties propiedades, Clock reloj) {
        this.codificador = codificador;
        this.configuracion = propiedades.jwt();
        this.reloj = reloj;
    }

    public TokenEmitido emitirDesafio(String usuario) {
        Instant ahora = reloj.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(configuracion.emisor())
                .subject(usuario)
                .audience(List.of(AUDIENCIA_DESAFIO))
                .issuedAt(ahora)
                .expiresAt(ahora.plus(configuracion.vigencia()))
                .build();
        return new TokenEmitido(firmar(claims), configuracion.vigencia());
    }

    public TokenEmitido emitirAcceso(String usuario, boolean puedePagar, String sid, Instant expira) {
        Instant ahora = reloj.instant();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(configuracion.emisor())
                .subject(usuario)
                .issuedAt(ahora)
                .expiresAt(expira)
                .claim(CLAIM_SESION, sid);
        if (puedePagar) {
            claims.claim("scope", SCOPE_PAGOS);
        }
        return new TokenEmitido(firmar(claims.build()), Duration.between(ahora, expira));
    }

    private String firmar(JwtClaimsSet claims) {
        JwsHeader cabecera = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        return codificador.encode(JwtEncoderParameters.from(cabecera, claims)).getTokenValue();
    }
}
