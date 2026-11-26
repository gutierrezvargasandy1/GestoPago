package com.utng.pagosservicios.servicio;

import com.utng.pagosservicios.dominio.cliente.Cliente;
import com.utng.pagosservicios.dominio.cliente.Plataforma;
import com.utng.pagosservicios.repositorio.ClienteRepository;
import com.utng.pagosservicios.repositorio.RepositorioSesiones.SesionAbierta;
import com.utng.pagosservicios.seguridad.EmisorTokens;
import com.utng.pagosservicios.seguridad.TokenEmitido;
import com.utng.pagosservicios.seguridad.Totp;
import com.utng.pagosservicios.seguridad.VerificadorDesafios;
import com.utng.pagosservicios.servicio.excepcion.AutenticacionFallidaException;
import com.utng.pagosservicios.servicio.excepcion.CodigoError;
import com.utng.pagosservicios.servicio.excepcion.DosPasosException;
import com.utng.pagosservicios.util.Correos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * Inicio de sesion en dos pasos, el mismo para web y movil:
 * <ol>
 *   <li>Usuario y contrasena: devuelve un desafio de 5 minutos (no da acceso).
 *       La primera vez incluye el secreto para registrar la app autenticadora.</li>
 *   <li>Desafio + codigo de 6 digitos: el servidor valida el codigo, abre la
 *       sesion (unica por cliente) y entrega el token de acceso.</li>
 * </ol>
 */
@Service
public class AutenticacionService {

    private static final Logger log = LoggerFactory.getLogger(AutenticacionService.class);
    private static final String EMISOR_TOTP = "GestoPago UTNG";
    private static final int MAXIMO_INTENTOS = 5;
    private static final Duration BLOQUEO = Duration.ofMinutes(5);

    public record PrimerPaso(TokenEmitido desafio, boolean requiereConfiguracion, String secreto, String otpauthUri) {
        @Override
        public String toString() {
            return "PrimerPaso[desafio=****, requiereConfiguracion=" + requiereConfiguracion + "]";
        }
    }

    public record SesionIniciada(TokenEmitido token, Plataforma plataforma, Instant expiraEn) {
    }

    private final ClienteRepository clientes;
    private final PasswordEncoder codificador;
    private final EmisorTokens emisor;
    private final VerificadorDesafios desafios;
    private final SesionService sesiones;
    private final Clock reloj;
    /** Hash contra el que se compara cuando el usuario no existe (ver iniciar). */
    private final String hashFicticio;

    public AutenticacionService(ClienteRepository clientes, PasswordEncoder codificador, EmisorTokens emisor,
                                VerificadorDesafios desafios, SesionService sesiones, Clock reloj) {
        this.clientes = clientes;
        this.codificador = codificador;
        this.emisor = emisor;
        this.desafios = desafios;
        this.sesiones = sesiones;
        this.reloj = reloj;
        this.hashFicticio = codificador.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public PrimerPaso iniciar(String correo, String password) {
        // El correo se compara sin espacios y en minusculas (asi se guarda al registrarse).
        String usuario = Correos.normalizar(correo);
        Optional<Cliente> encontrado = clientes.findByUsuario(usuario);
        // Siempre se ejecuta BCrypt, exista o no el usuario: si solo se calculara
        // cuando existe, el tiempo de respuesta revelaria que usuarios son validos.
        String hash = encontrado.map(Cliente::getPasswordHash).orElse(hashFicticio);
        boolean coincide = codificador.matches(password, hash);
        if (encontrado.isEmpty() || !coincide || !encontrado.get().isActivo()) {
            log.info("Autenticacion fallida para el usuario '{}'", limpiar(usuario));
            throw new AutenticacionFallidaException();
        }
        Cliente cliente = encontrado.get();
        cliente.asignarSecretoTotp(Totp.generarSecreto());
        TokenEmitido desafio = emisor.emitirDesafio(cliente.getUsuario());
        if (cliente.isTotpActivo()) {
            return new PrimerPaso(desafio, false, null, null);
        }
        // Primera vez: se entrega el secreto para que el cliente lo registre en su app.
        return new PrimerPaso(desafio, true, cliente.getTotpSecreto(),
                Totp.uri(EMISOR_TOTP, cliente.getUsuario(), cliente.getTotpSecreto()));
    }

    /**
     * Un codigo incorrecto se registra aunque se lance la excepcion
     * (noRollbackFor); un conflicto de sesion revierte todo, asi el codigo se
     * puede reenviar con forzar=true sin contar como usado.
     */
    @Transactional(noRollbackFor = DosPasosException.class)
    public SesionIniciada verificar(String desafio, String codigo, Plataforma plataforma, boolean forzar) {
        String usuario = desafios.usuarioDe(desafio);
        Cliente cliente = clientes.findByUsuario(usuario)
                .filter(Cliente::isActivo)
                .orElseThrow(() -> new DosPasosException(CodigoError.DESAFIO_INVALIDO,
                        "La verificación expiró o no es válida. Vuelve a iniciar sesión."));
        Instant ahora = reloj.instant();
        if (cliente.bloqueadoPorIntentos(ahora)) {
            throw new DosPasosException(CodigoError.CODIGO_2FA_INVALIDO,
                    "Demasiados códigos incorrectos. Espera 5 minutos e inicia sesión de nuevo.");
        }
        OptionalLong paso = Totp.verificar(cliente.getTotpSecreto(), codigo, ahora, cliente.getTotpUltimoPaso());
        if (paso.isEmpty()) {
            cliente.registrarFalloTotp(ahora, MAXIMO_INTENTOS, BLOQUEO);
            log.info("Codigo de doble factor incorrecto para '{}'", limpiar(usuario));
            throw new DosPasosException(CodigoError.CODIGO_2FA_INVALIDO,
                    "El código no es correcto o ya expiró. Usa el código que muestra tu app ahora.");
        }
        SesionAbierta sesion = sesiones.abrir(cliente.getId(), plataforma, forzar);
        cliente.registrarExitoTotp(paso.getAsLong());
        TokenEmitido token = emisor.emitirAcceso(cliente.getUsuario(), cliente.puedePagar(), sesion.sid(),
                sesion.expiraEn());
        log.info("Sesion iniciada en {} para el cliente {}", plataforma, cliente.getId());
        return new SesionIniciada(token, plataforma, sesion.expiraEn());
    }

    public void cerrarSesion(String sid) {
        if (sid != null) {
            sesiones.cerrar(sid);
        }
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
