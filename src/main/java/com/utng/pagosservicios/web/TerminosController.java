package com.utng.pagosservicios.web;

import com.utng.pagosservicios.servicio.TerminosService;
import com.utng.pagosservicios.web.dto.TerminosResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Terminos y condiciones vigentes. Es publico: se leen antes de crear la cuenta. */
@RestController
@RequestMapping("/api/v1/terminos")
public class TerminosController {

    private final TerminosService terminos;

    public TerminosController(TerminosService terminos) {
        this.terminos = terminos;
    }

    @GetMapping
    public TerminosResponse vigentes() {
        return TerminosResponse.de(terminos.vigentes());
    }
}
