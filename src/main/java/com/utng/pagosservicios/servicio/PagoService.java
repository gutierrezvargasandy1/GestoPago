package com.utng.pagosservicios.servicio;

import com.utng.pagosservicios.dominio.catalogo.Producto;
import com.utng.pagosservicios.dominio.cliente.Cliente;
import com.utng.pagosservicios.dominio.pago.MotivoResolucion;
import com.utng.pagosservicios.dominio.pago.SolicitudPago;
import com.utng.pagosservicios.dominio.pago.TransaccionPago;
import com.utng.pagosservicios.proveedor.OrdenPago;
import com.utng.pagosservicios.proveedor.ProveedorPagos;
import com.utng.pagosservicios.proveedor.ResultadoEnvio;
import com.utng.pagosservicios.repositorio.ClienteRepository;
import com.utng.pagosservicios.repositorio.TransaccionPagoRepository;
import com.utng.pagosservicios.servicio.excepcion.AccesoDenegadoException;
import com.utng.pagosservicios.servicio.excepcion.CodigoError;
import com.utng.pagosservicios.servicio.excepcion.ConflictoException;
import com.utng.pagosservicios.servicio.excepcion.PagoDuplicadoException;
import com.utng.pagosservicios.servicio.excepcion.PagoRechazadoException;
import com.utng.pagosservicios.servicio.excepcion.ProveedorNoDisponibleException;
import com.utng.pagosservicios.servicio.excepcion.RecursoNoEncontradoException;
import com.utng.pagosservicios.util.DescripcionError;
import com.utng.pagosservicios.util.Trazabilidad;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

/**
 * Orquesta el pago de un servicio de principio a fin.
 *
 * <p>Este metodo NO es @Transactional a proposito. El flujo usa tres
 * transacciones de base de datos cortas y la llamada al proveedor queda fuera
 * de todas ellas:
 * <ol>
 *   <li>Reservar: bloquear cuenta, validar, descontar (estado DEBITADA).</li>
 *   <li>Marcar como ENVIADA (antes de llamar al proveedor).</li>
 *   <li>Resolver segun la respuesta: CONFIRMADA, REVERSADA o EN_CONCILIACION.</li>
 * </ol>
 * Si el proceso muere entre pasos, el estado guardado dice exactamente que
 * hacer: DEBITADA se reversa sin consultar; ENVIADA se consulta al proveedor.
 */
@Service
public class PagoService {

    private static final Logger log = LoggerFactory.getLogger(PagoService.class);

    private final ClienteRepository clientes;
    private final TransaccionPagoRepository transacciones;
    private final ValidadorPago validador;
    private final FondosService fondos;
    private final TransaccionPagoService estados;
    private final ProveedorPagos proveedor;

    public PagoService(ClienteRepository clientes, TransaccionPagoRepository transacciones,
                       ValidadorPago validador, FondosService fondos,
                       TransaccionPagoService estados, ProveedorPagos proveedor) {
        this.clientes = clientes;
        this.transacciones = transacciones;
        this.validador = validador;
        this.fondos = fondos;
        this.estados = estados;
        this.proveedor = proveedor;
    }

    public ResultadoPago pagar(String usuario, ClaveIdempotencia clave, SolicitudPago solicitud) {
        Cliente cliente = clienteHabilitado(usuario);
        String huella = solicitud.huella();

        // 1. Idempotencia: si la clave ya se uso, no se ejecuta nada nuevo.
        Optional<TransaccionPago> previa =
                transacciones.findByClienteIdAndClaveIdempotencia(cliente.getId(), clave.valor());
        if (previa.isPresent()) {
            return repetir(previa.get(), huella);
        }

        // 2. Catalogo, monto y referencia (sin bloquear nada todavia).
        Producto producto = validador.validar(solicitud);

        // 3. Si ya se sabe que el proveedor no responde, no se toca el saldo.
        if (!proveedor.disponible()) {
            log.warn("Circuito hacia el proveedor abierto: el pago se rechaza sin tocar el saldo");
            throw new ProveedorNoDisponibleException(null);
        }

        // 4. Transaccion 1: descontar el dinero.
        TransaccionPago reservada;
        try {
            reservada = fondos.reservar(cliente.getId(), clave.valor(), solicitud, producto,
                    MDC.get(Trazabilidad.CLAVE_MDC));
        } catch (DataIntegrityViolationException e) {
            // Otra peticion con la misma clave inserto primero: el UNIQUE lo impidio.
            return transacciones.findByClienteIdAndClaveIdempotencia(cliente.getId(), clave.valor())
                    .map(existente -> repetir(existente, huella))
                    .orElseThrow(() -> e);
        }

        // 5. Transacciones 2 y 3: enviar y resolver.
        return enviarYResolver(reservada, producto);
    }

    public ResultadoPago consultar(String usuario, Long transaccionId) {
        return clientes.findByUsuario(usuario)
                .flatMap(cliente -> transacciones.findByIdAndClienteId(transaccionId, cliente.getId()))
                .map(transaccion -> ResultadoPago.de(transaccion, false))
                .orElseThrow(() -> new RecursoNoEncontradoException(CodigoError.PAGO_NO_ENCONTRADO,
                        "No existe un pago con ese identificador para el cliente autenticado."));
    }

    private ResultadoPago enviarYResolver(TransaccionPago reservada, Producto producto) {
        TransaccionPago enviada = estados.marcarEnviada(reservada.getId());
        Long id = enviada.getId();
        ResultadoEnvio resultado = invocarProveedor(OrdenPago.de(enviada, producto));
        log.info("Transaccion {}: el proveedor respondio {} ({})", id, resultado.desenlace(), resultado.codigo());

        return switch (resultado.desenlace()) {
            case APROBADA -> ResultadoPago.de(estados.confirmar(id, resultado.codigo(), resultado.folio(),
                    resultado.autorizacion(), resultado.canje()), false);
            case RECHAZADA -> {
                fondos.reversar(id, MotivoResolucion.RECHAZADA_POR_PROVEEDOR, resultado.codigo());
                throw new PagoRechazadoException(id, resultado.codigo());
            }
            case DUPLICADA -> {
                fondos.reversar(id, MotivoResolucion.DUPLICADA_EN_PROVEEDOR, resultado.codigo());
                throw new PagoDuplicadoException(id);
            }
            case NO_PROCESADA -> {
                fondos.reversar(id, MotivoResolucion.PROVEEDOR_NO_DISPONIBLE, resultado.codigo());
                throw new ProveedorNoDisponibleException(id);
            }
            // Un timeout no significa que el pago fallo: el dinero se retiene y se concilia.
            case INDETERMINADA -> ResultadoPago.de(estados.enviarAConciliacion(id, resultado.codigo()), false);
        };
    }

    /**
     * Ultima red: si el adaptador falla de una forma no prevista, eso no prueba
     * que el proveedor no cobro. Se trata como indeterminado, nunca como rechazo.
     */
    private ResultadoEnvio invocarProveedor(OrdenPago orden) {
        try {
            return Objects.requireNonNullElse(proveedor.enviar(orden),
                    ResultadoEnvio.indeterminada("SIN_RESPUESTA"));
        } catch (RuntimeException e) {
            log.error("Error inesperado al invocar al proveedor para la orden {}: {}",
                    orden.referenciaExterna(), DescripcionError.de(e));
            return ResultadoEnvio.indeterminada("ERROR_INESPERADO");
        }
    }

    private ResultadoPago repetir(TransaccionPago existente, String huella) {
        if (!existente.getHuellaSolicitud().equals(huella)) {
            throw new ConflictoException(CodigoError.CLAVE_IDEMPOTENCIA_REUTILIZADA,
                    "La clave de idempotencia ya se usó con datos distintos. Genere una clave nueva para cada pago.");
        }
        if (existente.getEstado().enCurso()) {
            throw new ConflictoException(CodigoError.PAGO_EN_PROCESO,
                    "Ya hay una operación en curso con esta clave. Consulte su estado antes de reintentar.",
                    existente.getId());
        }
        log.info("Clave de idempotencia repetida: se devuelve la transaccion {} sin volver a cobrar",
                existente.getId());
        return ResultadoPago.de(existente, true);
    }

    private Cliente clienteHabilitado(String usuario) {
        // Se revisa en cada operacion: el permiso pudo revocarse despues de emitir el token.
        return clientes.findByUsuario(usuario)
                .filter(Cliente::puedePagar)
                .orElseThrow(() -> new AccesoDenegadoException(CodigoError.CLIENTE_NO_HABILITADO,
                        "El cliente no está habilitado para realizar pagos."));
    }
}
