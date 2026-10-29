package com.utng.pagosservicios.servicio;

import com.utng.pagosservicios.dominio.cliente.Cliente;
import com.utng.pagosservicios.repositorio.ClienteRepository;
import com.utng.pagosservicios.seguridad.EmisorTokens;
import com.utng.pagosservicios.seguridad.TokenEmitido;
import com.utng.pagosservicios.servicio.excepcion.AutenticacionFallidaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class AutenticacionService {

    private static final Logger log = LoggerFactory.getLogger(AutenticacionService.class);

    private final ClienteRepository clientes;
    private final PasswordEncoder codificador;
    private final EmisorTokens emisor;
    /** Hash contra el que se compara cuando el usuario no existe (ver autenticar). */
    private final String hashFicticio;

    public AutenticacionService(ClienteRepository clientes, PasswordEncoder codificador, EmisorTokens emisor) {
        this.clientes = clientes;
        this.codificador = codificador;
        this.emisor = emisor;
        this.hashFicticio = codificador.encode(UUID.randomUUID().toString());
    }

    @Transactional(readOnly = true)
    public TokenEmitido autenticar(String usuario, String password) {
        Optional<Cliente> cliente = clientes.findByUsuario(usuario);
        // Siempre se ejecuta BCrypt, exista o no el usuario: si solo se calculara
        // cuando existe, el tiempo de respuesta revelaria que usuarios son validos.
        String hash = cliente.map(Cliente::getPasswordHash).orElse(hashFicticio);
        boolean coincide = codificador.matches(password, hash);

        if (cliente.isEmpty() || !coincide || !cliente.get().isActivo()) {
            log.info("Autenticacion fallida para el usuario '{}'", limpiar(usuario));
            throw new AutenticacionFallidaException();
        }
        return emisor.emitir(cliente.get().getUsuario(), cliente.get().puedePagar());
    }

    /** Evita inyeccion de lineas falsas en el log a traves del nombre de usuario. */
    private static String limpiar(String valor) {
        if (valor == null) {
            return "";
        }
        String limpio = valor.replaceAll("[^A-Za-z0-9._@-]", "_");
        return limpio.length() > 50 ? limpio.substring(0, 50) : limpio;
    }
}
