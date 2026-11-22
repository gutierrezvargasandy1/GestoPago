package com.utng.pagosservicios.web;

import com.utng.pagosservicios.seguridad.EmisorTokens;
import com.utng.pagosservicios.servicio.PerfilService;
import com.utng.pagosservicios.web.dto.PerfilResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/perfil")
public class PerfilController {

    private final PerfilService perfiles;

    public PerfilController(PerfilService perfiles) {
        this.perfiles = perfiles;
    }

    /** Cliente, sus cuentas con saldo y la sesion actual. */
    @GetMapping
    public PerfilResponse perfil(@AuthenticationPrincipal Jwt token) {
        return PerfilResponse.de(perfiles.perfil(token.getSubject(), token.getClaimAsString(EmisorTokens.CLAIM_SESION)));
    }
}
