package com.utng.pagosservicios.servicio;

import com.utng.pagosservicios.dominio.catalogo.Producto;
import com.utng.pagosservicios.dominio.pago.EstadoTransaccion;
import com.utng.pagosservicios.dominio.pago.TransaccionPago;
import com.utng.pagosservicios.repositorio.ClienteRepository;
import com.utng.pagosservicios.repositorio.ProductoRepository;
import com.utng.pagosservicios.repositorio.TransaccionPagoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Historial de compras: solo lo que GestoPago confirmo (CONFIRMADA). Las
 * pendientes, rechazadas o reversadas no son compras realizadas.
 */
@Service
public class HistorialService {

    public record Compra(Long transaccionId, Instant fecha, String servicio, String producto, String referencia,
                         BigDecimal monto, BigDecimal comision, BigDecimal montoTotal, String folioProveedor,
                         String autorizacion, String pin, String instrucciones) {
    }

    private final ClienteRepository clientes;
    private final TransaccionPagoRepository transacciones;
    private final ProductoRepository productos;

    public HistorialService(ClienteRepository clientes, TransaccionPagoRepository transacciones,
                            ProductoRepository productos) {
        this.clientes = clientes;
        this.transacciones = transacciones;
        this.productos = productos;
    }

    @Transactional(readOnly = true)
    public List<Compra> compras(String usuario) {
        Long clienteId = clientes.findByUsuario(usuario).map(c -> c.getId()).orElse(null);
        if (clienteId == null) {
            return List.of();
        }
        List<TransaccionPago> confirmadas = transacciones
                .findTop100ByClienteIdAndEstadoOrderByResueltoEnDesc(clienteId, EstadoTransaccion.CONFIRMADA);
        Map<Long, Producto> porId = productos.findAllById(
                        confirmadas.stream().map(TransaccionPago::getProductoId).distinct().toList())
                .stream().collect(Collectors.toMap(Producto::getId, Function.identity()));
        return confirmadas.stream().map(tx -> {
            Producto producto = porId.get(tx.getProductoId());
            return new Compra(tx.getId(), tx.getResueltoEn(),
                    producto == null ? "" : producto.getServicio().getNombre(),
                    producto == null ? "" : producto.getNombre(),
                    tx.getReferenciaServicio(), tx.getMonto(), tx.getComision(), tx.getMontoTotal(),
                    tx.getFolioProveedor(), tx.getAutorizacionProveedor(), tx.getPinCanje(),
                    tx.getInstruccionesCanje());
        }).toList();
    }
}
