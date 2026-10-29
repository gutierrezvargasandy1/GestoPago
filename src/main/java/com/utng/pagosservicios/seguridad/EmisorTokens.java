package com.utng.pagosservicios.seguridad;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * Emite los JWT de los clientes. Vigencia corta (15 min) para limitar el dano
 * si un token se filtra. El permiso "pagos" viaja como scope; ademas el servicio
 * vuelve a revisar en base de datos que el cliente siga habilitado.
 */
@Component
public class EmisorTokens {

    public static final String SCOPE_PAGOS = "pagos";

    private final JwtEncoder codificador;
    private final SeguridadProperties.Jwt configuracion;
    private final Clock reloj;

    public EmisorTokens(JwtEncoder codificador, SeguridadProperties propiedades, Clock reloj) {
        this.codificador = codificador;
        this.configuracion = propiedades.jwt();
        this.reloj = reloj;
    }

    public TokenEmitido emitir(String usuario, boolean puedePagar) {
        Instant ahora = reloj.instant();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(configuracion.emisor())
                .subject(usuario)
                .issuedAt(ahora)
                .expiresAt(ahora.plus(configuracion.vigencia()));
        if (puedePagar) {
            claims.claim("scope", SCOPE_PAGOS);
        }
        JwsHeader cabecera = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        String valor = codificador.encode(JwtEncoderParameters.from(cabecera, claims.build())).getTokenValue();
        return new TokenEmitido(valor, configuracion.vigencia());
    }
}
