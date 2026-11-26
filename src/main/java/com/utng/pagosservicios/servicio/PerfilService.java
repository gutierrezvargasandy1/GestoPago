package com.utng.pagosservicios.servicio;

import com.utng.pagosservicios.dominio.cliente.Cliente;
import com.utng.pagosservicios.dominio.cuenta.Cuenta;
import com.utng.pagosservicios.repositorio.ClienteRepository;
import com.utng.pagosservicios.repositorio.CuentaRepository;
import com.utng.pagosservicios.repositorio.RepositorioSesiones.SesionAbierta;
import com.utng.pagosservicios.servicio.excepcion.CodigoError;
import com.utng.pagosservicios.servicio.excepcion.ConflictoException;
import com.utng.pagosservicios.servicio.excepcion.RecursoNoEncontradoException;
import com.utng.pagosservicios.servicio.excepcion.ReglaNegocioException;
import com.utng.pagosservicios.servicio.excepcion.SolicitudInvalidaException;
import com.utng.pagosservicios.util.Correos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * Datos del cliente autenticado (lo que las apps necesitan para comprar) y la
 * edicion de su perfil: nombre, correo y contrasena.
 */
@Service
public class PerfilService {

    private static final Logger log = LoggerFactory.getLogger(PerfilService.class);
    private static final int MAX_BYTES_PASSWORD = 72;

    public record Perfil(Cliente cliente, List<Cuenta> cuentas, Optional<SesionAbierta> sesion,
                         long minutosInactividad, boolean modoDemostracion) {
    }

    private final ClienteRepository clientes;
    private final CuentaRepository cuentas;
    private final SesionService sesiones;
    private final PasswordEncoder codificador;
    private final boolean modoDemostracion;

    public PerfilService(ClienteRepository clientes, CuentaRepository cuentas, SesionService sesiones,
                         PasswordEncoder codificador,
                         @Value("${pagos.demostracion.activa:false}") boolean modoDemostracion) {
        this.clientes = clientes;
        this.cuentas = cuentas;
        this.sesiones = sesiones;
        this.codificador = codificador;
        this.modoDemostracion = modoDemostracion;
    }

    @Transactional(readOnly = true)
    public Perfil perfil(String usuario, String sid) {
        return armar(cliente(usuario), sid);
    }

    @Transactional
    public Perfil actualizarNombre(String usuario, String sid, String nombre) {
        Cliente cliente = cliente(usuario);
        cliente.cambiarNombre(nombre.strip());
        return armar(cliente, sid);
    }

    /**
     * El correo es el usuario y el subject del token: despues de cambiarlo se
     * cierra la sesion y hay que entrar con el correo nuevo. Se pide la
     * contrasena para que nadie con el dispositivo desbloqueado pueda quedarse
     * con la cuenta.
     */
    @Transactional
    public void cambiarCorreo(String usuario, String sid, String nuevoCorreo, String password) {
        Cliente cliente = cliente(usuario);
        exigirContrasena(cliente, password);
        String nuevo = Correos.normalizar(nuevoCorreo);
        if (nuevo.equals(cliente.getUsuario())) {
            throw new SolicitudInvalidaException(CodigoError.SOLICITUD_INVALIDA, "Ese ya es tu correo actual.");
        }
        if (clientes.existsByUsuario(nuevo)) {
            throw correoOcupado();
        }
        cliente.cambiarUsuario(nuevo);
        try {
            clientes.saveAndFlush(cliente);
        } catch (DataIntegrityViolationException e) {
            throw correoOcupado();
        }
        sesiones.cerrar(sid);
        log.info("El cliente {} cambio su correo; su sesion se cerro", cliente.getId());
    }

    @Transactional
    public void cambiarContrasena(String usuario, String actual, String nueva) {
        Cliente cliente = cliente(usuario);
        exigirContrasena(cliente, actual);
        if (nueva.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES_PASSWORD) {
            throw new SolicitudInvalidaException(CodigoError.SOLICITUD_INVALIDA,
                    "La contraseña es demasiado larga (máximo 72 bytes).");
        }
        if (codificador.matches(nueva, cliente.getPasswordHash())) {
            throw new SolicitudInvalidaException(CodigoError.SOLICITUD_INVALIDA,
                    "La nueva contraseña debe ser distinta de la actual.");
        }
        cliente.cambiarPasswordHash(codificador.encode(nueva));
        log.info("El cliente {} cambio su contrasena", cliente.getId());
    }

    private Perfil armar(Cliente cliente, String sid) {
        return new Perfil(cliente, cuentas.findByClienteIdOrderByIdAsc(cliente.getId()), sesiones.buscar(sid),
                sesiones.minutosDeInactividad(), modoDemostracion);
    }

    private Cliente cliente(String usuario) {
        return clientes.findByUsuario(usuario)
                .orElseThrow(() -> new RecursoNoEncontradoException(CodigoError.RECURSO_NO_ENCONTRADO,
                        "No se encontró el cliente."));
    }

    /** 422 y no 401: un 401 haria que la app diera por terminada la sesion. */
    private void exigirContrasena(Cliente cliente, String password) {
        if (password == null || !codificador.matches(password, cliente.getPasswordHash())) {
            throw new ReglaNegocioException(CodigoError.CONTRASENA_INCORRECTA, "La contraseña actual no es correcta.");
        }
    }

    private static ConflictoException correoOcupado() {
        return new ConflictoException(CodigoError.USUARIO_YA_REGISTRADO, "Ese correo ya está registrado.");
    }
}
