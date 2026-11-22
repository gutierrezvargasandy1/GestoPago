package com.utng.pagosservicios.servicio;

import com.utng.pagosservicios.dominio.cliente.Cliente;
import com.utng.pagosservicios.dominio.cuenta.Cuenta;
import com.utng.pagosservicios.repositorio.ClienteRepository;
import com.utng.pagosservicios.repositorio.CuentaRepository;
import com.utng.pagosservicios.repositorio.RepositorioSesiones.SesionAbierta;
import com.utng.pagosservicios.servicio.excepcion.CodigoError;
import com.utng.pagosservicios.servicio.excepcion.RecursoNoEncontradoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** Datos del cliente autenticado: lo que las apps necesitan para comprar. */
@Service
public class PerfilService {

    public record Perfil(Cliente cliente, List<Cuenta> cuentas, Optional<SesionAbierta> sesion,
                         long minutosInactividad) {
    }

    private final ClienteRepository clientes;
    private final CuentaRepository cuentas;
    private final SesionService sesiones;

    public PerfilService(ClienteRepository clientes, CuentaRepository cuentas, SesionService sesiones) {
        this.clientes = clientes;
        this.cuentas = cuentas;
        this.sesiones = sesiones;
    }

    @Transactional(readOnly = true)
    public Perfil perfil(String usuario, String sid) {
        Cliente cliente = clientes.findByUsuario(usuario)
                .orElseThrow(() -> new RecursoNoEncontradoException(CodigoError.RECURSO_NO_ENCONTRADO,
                        "No se encontró el cliente."));
        return new Perfil(cliente, cuentas.findByClienteIdOrderByIdAsc(cliente.getId()), sesiones.buscar(sid),
                sesiones.minutosDeInactividad());
    }
}
