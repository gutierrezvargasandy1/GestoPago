package com.utng.pagosservicios.web;

import com.utng.pagosservicios.seguridad.EmisorTokens;
import com.utng.pagosservicios.servicio.PerfilService;
import com.utng.pagosservicios.web.dto.ActualizarPerfilRequest;
import com.utng.pagosservicios.web.dto.CambioContrasenaRequest;
import com.utng.pagosservicios.web.dto.CambioCorreoRequest;
import com.utng.pagosservicios.web.dto.PerfilResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
        return PerfilResponse.de(perfiles.perfil(token.getSubject(), sid(token)));
    }

    /** Edita el nombre. Devuelve el perfil actualizado. */
    @PutMapping
    public PerfilResponse actualizar(@AuthenticationPrincipal Jwt token,
                                     @Valid @RequestBody ActualizarPerfilRequest solicitud) {
        return PerfilResponse.de(perfiles.actualizarNombre(token.getSubject(), sid(token), solicitud.nombre()));
    }

    /** Cambia el correo (usuario). La sesion se cierra: hay que entrar con el correo nuevo. */
    @PutMapping("/correo")
    public ResponseEntity<Void> cambiarCorreo(@AuthenticationPrincipal Jwt token,
                                              @Valid @RequestBody CambioCorreoRequest solicitud) {
        perfiles.cambiarCorreo(token.getSubject(), sid(token), solicitud.correo(), solicitud.password());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/contrasena")
    public ResponseEntity<Void> cambiarContrasena(@AuthenticationPrincipal Jwt token,
                                                  @Valid @RequestBody CambioContrasenaRequest solicitud) {
        perfiles.cambiarContrasena(token.getSubject(), solicitud.actual(), solicitud.nueva());
        return ResponseEntity.noContent().build();
    }

    private static String sid(Jwt token) {
        return token.getClaimAsString(EmisorTokens.CLAIM_SESION);
    }
}
