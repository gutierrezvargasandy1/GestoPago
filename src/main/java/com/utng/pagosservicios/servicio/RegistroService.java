package com.utng.pagosservicios.servicio;

import com.utng.pagosservicios.dominio.cliente.Cliente;
import com.utng.pagosservicios.dominio.cuenta.Cuenta;
import com.utng.pagosservicios.repositorio.ClienteRepository;
import com.utng.pagosservicios.repositorio.CuentaRepository;
import com.utng.pagosservicios.servicio.excepcion.CodigoError;
import com.utng.pagosservicios.servicio.excepcion.ConflictoException;
import com.utng.pagosservicios.servicio.excepcion.SolicitudInvalidaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * Alta de clientes: crea el cliente (contrasena con BCrypt) y su cuenta en una
 * sola transaccion; si algo falla no queda un cliente sin cuenta.
 */
@Service
public class RegistroService {

    private static final Logger log = LoggerFactory.getLogger(RegistroService.class);
    /** BCrypt solo usa los primeros 72 bytes; Spring Security rechaza contrasenas mas largas. */
    private static final int MAX_BYTES_PASSWORD = 72;
    private static final int INTENTOS_NUMERO_CUENTA = 5;

    private final ClienteRepository clientes;
    private final CuentaRepository cuentas;
    private final PasswordEncoder codificador;
    private final BigDecimal saldoInicial;
    private final BigDecimal limiteDiario;
    private final SecureRandom aleatorio = new SecureRandom();

    public record ClienteRegistrado(Cliente cliente, Cuenta cuenta) {
    }

    public RegistroService(ClienteRepository clientes, CuentaRepository cuentas, PasswordEncoder codificador,
                           @Value("${pagos.registro.saldo-inicial:0.00}") BigDecimal saldoInicial,
                           @Value("${pagos.registro.limite-diario:5000.00}") BigDecimal limiteDiario) {
        if (saldoInicial.signum() < 0 || limiteDiario.signum() <= 0) {
            throw new IllegalStateException("pagos.registro: el saldo inicial no puede ser negativo y el limite debe ser mayor a cero");
        }
        this.clientes = clientes;
        this.cuentas = cuentas;
        this.codificador = codificador;
        this.saldoInicial = saldoInicial;
        this.limiteDiario = limiteDiario;
    }

    @Transactional
    public ClienteRegistrado registrar(String usuario, String password, String nombre) {
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES_PASSWORD) {
            throw new SolicitudInvalidaException(CodigoError.SOLICITUD_INVALIDA,
                    "La contraseña es demasiado larga (máximo 72 bytes).");
        }
        if (clientes.existsByUsuario(usuario)) {
            throw usuarioOcupado();
        }
        Cliente cliente;
        try {
            // saveAndFlush: si otra peticion registro el mismo usuario al mismo tiempo,
            // el UNIQUE de la base salta aqui y no al final de la transaccion.
            cliente = clientes.saveAndFlush(new Cliente(usuario, codificador.encode(password), nombre.strip()));
        } catch (DataIntegrityViolationException e) {
            throw usuarioOcupado();
        }
        Cuenta cuenta = cuentas.save(new Cuenta(cliente.getId(), nuevoNumeroCuenta(), saldoInicial, limiteDiario));
        log.info("Cliente registrado con id {}", cliente.getId());
        return new ClienteRegistrado(cliente, cuenta);
    }

    /** 10 digitos aleatorios (SecureRandom: no se pueden adivinar cuentas ajenas en orden). */
    private String nuevoNumeroCuenta() {
        for (int intento = 0; intento < INTENTOS_NUMERO_CUENTA; intento++) {
            String numero = String.valueOf(1_000_000_000L + (long) (aleatorio.nextDouble() * 9_000_000_000L));
            if (!cuentas.existsByNumero(numero)) {
                return numero;
            }
        }
        throw new IllegalStateException("No se pudo generar un numero de cuenta libre");
    }

    private static ConflictoException usuarioOcupado() {
        return new ConflictoException(CodigoError.USUARIO_YA_REGISTRADO, "El usuario ya está registrado.");
    }
}