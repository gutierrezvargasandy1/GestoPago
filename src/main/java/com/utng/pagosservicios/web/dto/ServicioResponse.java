package com.utng.pagosservicios.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.utng.pagosservicios.dominio.catalogo.Servicio;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ServicioResponse(String idServicio, String nombre, String categoria) {

    public static ServicioResponse de(Servicio servicio) {
        return new ServicioResponse(servicio.getIdServicio(), servicio.getNombre(), servicio.getCategoria());
    }
}
