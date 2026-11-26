package com.utng.pagosservicios.web.dto;

import com.utng.pagosservicios.servicio.TerminosService.Terminos;

import java.time.LocalDate;
import java.util.List;

public record TerminosResponse(String version, LocalDate vigentesDesde, List<SeccionInfo> secciones) {

    public record SeccionInfo(String titulo, String texto) {
    }

    public static TerminosResponse de(Terminos terminos) {
        return new TerminosResponse(terminos.version(), terminos.vigentesDesde(),
                terminos.secciones().stream().map(s -> new SeccionInfo(s.titulo(), s.texto())).toList());
    }
}
