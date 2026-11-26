package com.utng.pagosservicios.web;

import com.utng.pagosservicios.seguridad.EmisorTokens;
import com.utng.pagosservicios.servicio.AutenticacionService;
import com.utng.pagosservicios.servicio.RegistroService;
import com.utng.pagosservicios.servicio.TerminosService;
import com.utng.pagosservicios.web.dto.CredencialesRequest;
import com.utng.pagosservicios.web.dto.PrimerPasoResponse;
import com.utng.pagosservicios.web.dto.RegistroRequest;
import com.utng.pagosservicios.web.dto.RegistroResponse;
import com.utng.pagosservicios.web.dto.SesionResponse;
import com.utng.pagosservicios.web.dto.VerificacionRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Autenticacion compartida por la app web y la movil: mismas credenciales,
 * doble factor validado en el servidor y una sola sesion activa por cliente.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AutenticacionController {

    private final AutenticacionService autenticacion;
    private final RegistroService registro;

    public AutenticacionController(AutenticacionService autenticacion, RegistroService registro) {
        this.autenticacion = autenticacion;
        this.registro = registro;
    }

    /** Alta de cliente (correo + aceptacion de terminos) con su cuenta. Despues se inicia sesion con POST /token. */
    @PostMapping("/registro")
    public ResponseEntity<RegistroResponse> registrar(@Valid @RequestBody RegistroRequest solicitud) {
        RegistroService.ClienteRegistrado registrado =
                registro.registrar(solicitud.correo(), solicitud.password(), solicitud.nombre(), TerminosService.VERSION);
        return ResponseEntity.status(HttpStatus.CREATED).body(RegistroResponse.de(registrado));
    }

    /** Paso 1: usuario y contrasena. Devuelve un desafio; todavia no hay acceso. */
    @PostMapping("/token")
    public ResponseEntity<PrimerPasoResponse> iniciar(@Valid @RequestBody CredencialesRequest credenciales) {
        AutenticacionService.PrimerPaso paso = autenticacion.iniciar(credenciales.usuario(), credenciales.password());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(PrimerPasoResponse.de(paso));
    }

    /** Paso 2: codigo de 6 digitos. Abre la sesion y entrega el token de acceso. */
    @PostMapping("/2fa")
    public ResponseEntity<SesionResponse> verificar(@Valid @RequestBody VerificacionRequest solicitud) {
        AutenticacionService.SesionIniciada sesion = autenticacion.verificar(solicitud.desafio(),
                solicitud.codigo(), solicitud.plataforma(), solicitud.forzar());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(SesionResponse.de(sesion));
    }

    /** Cierra la sesion en el servidor: el token deja de servir aunque no haya vencido. */
    @PostMapping("/logout")
    public ResponseEntity<Void> cerrarSesion(@AuthenticationPrincipal Jwt token) {
        autenticacion.cerrarSesion(token.getClaimAsString(EmisorTokens.CLAIM_SESION));
        return ResponseEntity.noContent().build();
    }
}
