package com.utng.pagosservicios.servicio.excepcion;

import com.utng.pagosservicios.repositorio.RepositorioSesiones.SesionAbierta;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Ya hay una sesion abierta en otra plataforma (409): el cliente decide si la cierra. */
public class SesionActivaException extends ConflictoException {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.of("America/Mexico_City"));

    public SesionActivaException(SesionAbierta anterior) {
        super(CodigoError.SESION_ACTIVA, anterior == null
                ? "Ya hay una sesión abierta con esta cuenta. Ciérrala para continuar."
                : "Ya tienes una sesión abierta en " + anterior.plataforma().getDescripcion()
                        + " (última actividad: " + HORA.format(anterior.ultimaActividad())
                        + "). Ciérrala para entrar aquí.");
    }
}
