package com.utng.pagosservicios.servicio;

import com.utng.pagosservicios.dominio.cliente.Plataforma;
import com.utng.pagosservicios.repositorio.RepositorioSesiones;
import com.utng.pagosservicios.repositorio.RepositorioSesiones.SesionAbierta;
import com.utng.pagosservicios.seguridad.SeguridadProperties;
import com.utng.pagosservicios.servicio.excepcion.SesionActivaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Administracion centralizada de sesiones: un cliente tiene a lo mas una sesion
 * abierta, en la web o en el movil. Para entrar desde la otra plataforma debe
 * aceptar cerrar la anterior; el token de la sesion cerrada deja de servir.
 */
@Service
public class SesionService {

    private static final Logger log = LoggerFactory.getLogger(SesionService.class);

    private final RepositorioSesiones repositorio;
    private final SeguridadProperties.Sesion configuracion;
    private final Clock reloj;

    public SesionService(RepositorioSesiones repositorio, SeguridadProperties propiedades, Clock reloj) {
        this.repositorio = repositorio;
        this.configuracion = propiedades.sesion();
        this.reloj = reloj;
    }

    /**
     * @param forzar true cuando el cliente ya acepto cerrar la sesion de la otra plataforma
     * @throws SesionActivaException si hay una sesion abierta y no se acepto cerrarla
     */
    @Transactional
    public SesionAbierta abrir(long clienteId, Plataforma plataforma, boolean forzar) {
        Instant ahora = reloj.instant();
        repositorio.cerrarVencidas(clienteId, ahora, ahora.minus(configuracion.inactividad()));
        Optional<SesionAbierta> anterior = repositorio.abiertaParaActualizar(clienteId);
        if (anterior.isPresent()) {
            if (!forzar) {
                throw new SesionActivaException(anterior.get());
            }
            repositorio.cerrarDelCliente(clienteId, "REEMPLAZADA", ahora);
            log.info("Sesion de {} reemplazada por una nueva en {}", anterior.get().plataforma(), plataforma);
        }
        String sid = UUID.randomUUID().toString();
        Instant expira = ahora.plus(configuracion.duracionMaxima());
        try {
            repositorio.insertar(sid, clienteId, plataforma, ahora, expira);
        } catch (DuplicateKeyException e) {
            // Otro inicio de sesion gano la carrera: el indice unico parcial lo impidio.
            throw new SesionActivaException(null);
        }
        return new SesionAbierta(sid, plataforma, ahora, ahora, expira);
    }

    /** Se llama en cada peticion autenticada (ValidadorSesion). */
    @Transactional
    public boolean registrarActividad(String sid) {
        Instant ahora = reloj.instant();
        return repositorio.registrarActividad(sid, ahora, ahora.minus(configuracion.inactividad()));
    }

    @Transactional
    public void cerrar(String sid) {
        repositorio.cerrar(sid, "CIERRE_USUARIO", reloj.instant());
    }

    @Transactional(readOnly = true)
    public Optional<SesionAbierta> buscar(String sid) {
        return repositorio.buscar(sid);
    }

    public long minutosDeInactividad() {
        return configuracion.inactividad().toMinutes();
    }
}
