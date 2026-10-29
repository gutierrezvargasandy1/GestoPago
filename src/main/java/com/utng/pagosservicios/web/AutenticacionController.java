package com.utng.pagosservicios.web;

import com.utng.pagosservicios.seguridad.TokenEmitido;
import com.utng.pagosservicios.servicio.AutenticacionService;
import com.utng.pagosservicios.servicio.RegistroService;
import com.utng.pagosservicios.web.dto.CredencialesRequest;
import com.utng.pagosservicios.web.dto.RegistroRequest;
import com.utng.pagosservicios.web.dto.RegistroResponse;
import com.utng.pagosservicios.web.dto.TokenResponse;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AutenticacionController {

    private final AutenticacionService autenticacion;
    private final RegistroService registro;

    public AutenticacionController(AutenticacionService autenticacion, RegistroService registro) {
        this.autenticacion = autenticacion;
        this.registro = registro;
    }

    /** Alta de cliente con su cuenta. Despues se inicia sesion con POST /token. */
    @PostMapping("/registro")
    public ResponseEntity<RegistroResponse> registrar(@Valid @RequestBody RegistroRequest solicitud) {
        RegistroService.ClienteRegistrado registrado =
                registro.registrar(solicitud.usuario(), solicitud.password(), solicitud.nombre());
        return ResponseEntity.status(HttpStatus.CREATED).body(RegistroResponse.de(registrado));
    }

    /** Cache-Control: no-store evita que un proxy o el navegador guarden el token. */
    @PostMapping("/token")
    public ResponseEntity<TokenResponse> emitirToken(@Valid @RequestBody CredencialesRequest credenciales) {
        TokenEmitido token = autenticacion.autenticar(credenciales.usuario(), credenciales.password());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new TokenResponse(token.valor(), "Bearer", token.vigencia().toSeconds()));
    }
}