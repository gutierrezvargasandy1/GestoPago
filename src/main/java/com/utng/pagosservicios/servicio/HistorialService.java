package com.utng.pagosservicios.servicio;

import com.utng.pagosservicios.dominio.catalogo.Producto;
import com.utng.pagosservicios.dominio.pago.EstadoTransaccion;
import com.utng.pagosservicios.dominio.pago.MotivoResolucion;
import com.utng.pagosservicios.dominio.pago.TransaccionPago;
import com.utng.pagosservicios.repositorio.ClienteRepository;
import com.utng.pagosservicios.repositorio.ProductoRepository;
import com.utng.pagosservicios.repositorio.TransaccionPagoRepository;
import com.utng.pagosservicios.servicio.excepcion.CodigoError;
import com.utng.pagosservicios.servicio.excepcion.SolicitudInvalidaException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Compras del cliente.
 * <ul>
 *   <li>Historial ({@link #compras}): solo lo que el proveedor confirmo
 *       (CONFIRMADA). Las pendientes o reversadas no son compras realizadas.</li>
 *   <li>Busqueda ({@link #buscar}): todas sus operaciones, en cualquier estado
 *       (exitosa, pendiente, rechazada o error de comunicacion), con filtros
 *       por estado, fechas y texto.</li>
 * </ul>
 */
@Service
public class HistorialService {

    /** Maximo de operaciones que devuelve una busqueda. */
    static final int MAXIMO_RESULTADOS = 200;
    static final int MAXIMO_TEXTO = 100;
    /** Filas que se leen de la base antes de aplicar el filtro de texto. */
    static final int MAXIMO_CONSULTA = 500;

    /**
     * Como se agrupan los estados internos en lo que ve el cliente. Una
     * operacion REVERSADA porque no hubo comunicacion con el proveedor se
     * distingue de una que el proveedor rechazo.
     */
    public enum Grupo {
        TODAS(null, EnumSet.allOf(EstadoTransaccion.class)),
        EXITOSAS("EXITOSA", EnumSet.of(EstadoTransaccion.CONFIRMADA)),
        PENDIENTES("PENDIENTE", EnumSet.of(EstadoTransaccion.DEBITADA, EstadoTransaccion.ENVIADA,
                EstadoTransaccion.EN_CONCILIACION, EstadoTransaccion.REVISION_MANUAL)),
        RECHAZADAS("RECHAZADA", EnumSet.of(EstadoTransaccion.REVERSADA)),
        ERRORES_COMUNICACION("ERROR_COMUNICACION", EnumSet.of(EstadoTransaccion.REVERSADA));

        /** Motivos de reverso que significan que el proveedor nunca recibio o proceso la operacion. */
        private static final Set<MotivoResolucion> SIN_COMUNICACION =
                EnumSet.of(MotivoResolucion.PROVEEDOR_NO_DISPONIBLE, MotivoResolucion.NUNCA_ENVIADA);

        private final String codigo;
        private final Set<EstadoTransaccion> estados;

        Grupo(String codigo, Set<EstadoTransaccion> estados) {
            this.codigo = codigo;
            this.estados = estados;
        }

        public Set<EstadoTransaccion> estados() {
            return estados;
        }

        boolean incluye(Compra compra) {
            return codigo == null || codigo.equals(compra.grupo());
        }

        /** EXITOSA, PENDIENTE, RECHAZADA o ERROR_COMUNICACION: el estado que la app muestra. */
        public static String de(EstadoTransaccion estado, MotivoResolucion motivo) {
            return switch (estado) {
                case CONFIRMADA -> "EXITOSA";
                case REVERSADA -> motivo != null && SIN_COMUNICACION.contains(motivo)
                        ? "ERROR_COMUNICACION" : "RECHAZADA";
                case DEBITADA, ENVIADA, EN_CONCILIACION, REVISION_MANUAL -> "PENDIENTE";
            };
        }
    }

    public record Compra(Long transaccionId, Instant fecha, Instant creadaEn, String servicio, String producto,
                         String referencia, BigDecimal monto, BigDecimal comision, BigDecimal montoTotal,
                         String estado, String grupo, String motivo, String folioProveedor,
                         String autorizacion, String pin, String instrucciones) {
    }

    private final ClienteRepository clientes;
    private final TransaccionPagoRepository transacciones;
    private final ProductoRepository productos;
    private final ZoneId zona;

    public HistorialService(ClienteRepository clientes, TransaccionPagoRepository transacciones,
                            ProductoRepository productos,
                            @Value("${pagos.zona-horaria:America/Mexico_City}") ZoneId zona) {
        this.clientes = clientes;
        this.transacciones = transacciones;
        this.productos = productos;
        this.zona = zona;
    }

    /** Historial: compras CONFIRMADA, la mas reciente primero. */
    @Transactional(readOnly = true)
    public List<Compra> compras(String usuario) {
        Long clienteId = clienteId(usuario);
        if (clienteId == null) {
            return List.of();
        }
        return convertir(transacciones
                .findTop100ByClienteIdAndEstadoOrderByResueltoEnDesc(clienteId, EstadoTransaccion.CONFIRMADA));
    }

    /**
     * Todas las operaciones del cliente con filtros. Las fechas son dias
     * completos en la zona horaria del negocio (desde y hasta incluidos).
     */
    @Transactional(readOnly = true)
    public List<Compra> buscar(String usuario, Grupo grupo, String texto, LocalDate desde, LocalDate hasta) {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new SolicitudInvalidaException(CodigoError.SOLICITUD_INVALIDA,
                    "La fecha inicial no puede ser posterior a la fecha final.");
        }
        String filtro = texto == null ? "" : texto.strip().toLowerCase(Locale.ROOT);
        if (filtro.length() > MAXIMO_TEXTO) {
            throw new SolicitudInvalidaException(CodigoError.SOLICITUD_INVALIDA,
                    "El texto de búsqueda es demasiado largo (máximo " + MAXIMO_TEXTO + " caracteres).");
        }
        Long clienteId = clienteId(usuario);
        if (clienteId == null) {
            return List.of();
        }
        Instant inicio = desde == null ? Instant.EPOCH : desde.atStartOfDay(zona).toInstant();
        Instant fin = hasta == null
                ? Instant.now().plusSeconds(86_400)
                : hasta.plusDays(1).atStartOfDay(zona).toInstant();
        Grupo elegido = grupo == null ? Grupo.TODAS : grupo;
        List<TransaccionPago> encontradas = transacciones.buscarDelCliente(clienteId,
                elegido.estados(), inicio, fin, PageRequest.of(0, MAXIMO_CONSULTA));
        return convertir(encontradas).stream()
                .filter(elegido::incluye)
                .filter(compra -> filtro.isEmpty() || coincide(compra, filtro))
                .limit(MAXIMO_RESULTADOS)
                .toList();
    }

    private Long clienteId(String usuario) {
        return clientes.findByUsuario(usuario).map(c -> c.getId()).orElse(null);
    }

    private List<Compra> convertir(List<TransaccionPago> lista) {
        Map<Long, Producto> porId = productos.findAllById(
                        lista.stream().map(TransaccionPago::getProductoId).distinct().toList())
                .stream().collect(Collectors.toMap(Producto::getId, Function.identity()));
        return lista.stream().map(tx -> {
            Producto producto = porId.get(tx.getProductoId());
            boolean confirmada = tx.getEstado() == EstadoTransaccion.CONFIRMADA;
            return new Compra(tx.getId(),
                    tx.getResueltoEn() != null ? tx.getResueltoEn() : tx.getCreadoEn(),
                    tx.getCreadoEn(),
                    producto == null ? "" : producto.getServicio().getNombre(),
                    producto == null ? "" : producto.getNombre(),
                    tx.getReferenciaServicio(), tx.getMonto(), tx.getComision(), tx.getMontoTotal(),
                    tx.getEstado().name(), Grupo.de(tx.getEstado(), tx.getMotivo()),
                    tx.getMotivo() == null ? null : tx.getMotivo().getDescripcion(),
                    // Folio, autorizacion y PIN solo existen en una compra confirmada.
                    confirmada ? tx.getFolioProveedor() : null,
                    confirmada ? tx.getAutorizacionProveedor() : null,
                    confirmada ? tx.getPinCanje() : null,
                    confirmada ? tx.getInstruccionesCanje() : null);
        }).toList();
    }

    /** Busca en producto, servicio, referencia, folio, autorizacion, numero de operacion y estado. */
    private static boolean coincide(Compra compra, String filtro) {
        return Stream.of(compra.producto(), compra.servicio(), compra.referencia(), compra.folioProveedor(),
                        compra.autorizacion(), String.valueOf(compra.transaccionId()), compra.grupo())
                .anyMatch(valor -> valor != null && valor.toLowerCase(Locale.ROOT).contains(filtro));
    }
}
