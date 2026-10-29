package com.utng.pagosservicios.proveedor.gestopago;

import com.utng.pagosservicios.dominio.catalogo.Modalidad;
import com.utng.pagosservicios.proveedor.BitacoraIntercambios;
import com.utng.pagosservicios.proveedor.BitacoraIntercambios.Intercambio;
import com.utng.pagosservicios.proveedor.BitacoraIntercambios.Operacion;
import com.utng.pagosservicios.proveedor.CatalogoObtenido;
import com.utng.pagosservicios.proveedor.CatalogoProveedor;
import com.utng.pagosservicios.proveedor.ProductoProveedor;
import com.utng.pagosservicios.util.Trazabilidad;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.w3c.dom.Element;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Lee el catalogo de PuntoRed (GET /sistema/service/getProductList.do):
 *
 * <pre>
 * &lt;RESPONSE&gt;
 *   &lt;MENSAJE&gt;&lt;CODIGO&gt;01&lt;/CODIGO&gt;&lt;TEXTO&gt;...&lt;/TEXTO&gt;&lt;/MENSAJE&gt;
 *   &lt;PRODUCTOS&gt;
 *     &lt;producto servicio=".." producto=".." idServicio="1" idProducto="10"
 *               idCatTipoServicio=".." tipoFront=".." precio="20.00" ...&gt;
 *       &lt;legend&gt;...&lt;/legend&gt;
 *     &lt;/producto&gt;
 *   &lt;/PRODUCTOS&gt;
 * &lt;/RESPONSE&gt;
 * </pre>
 *
 * El proveedor limita esta llamada a 3 por dia; el control de frecuencia vive
 * en la sincronizacion, aqui solo se hace la llamada y se traduce el resultado.
 * No pasa por el circuit breaker de los pagos: un catalogo caido no debe
 * impedir cobrar.
 */
public class GestopagoCatalogo implements CatalogoProveedor {

    private static final Logger log = LoggerFactory.getLogger(GestopagoCatalogo.class);

    /** El catalogo completo puede ocupar varios cientos de KB. */
    static final int MAX_BYTES_CATALOGO = 8 * 1024 * 1024;
    private static final int INTENTOS_MAXIMOS = 2;

    private final ConectorGestopago conector;
    private final GestopagoProperties propiedades;
    private final CredencialGestopago credencial;
    private final CodigosGestopago codigos;
    private final BitacoraIntercambios bitacora;
    private final Clock reloj;

    GestopagoCatalogo(ConectorGestopago conector, GestopagoProperties propiedades, CredencialGestopago credencial,
                      BitacoraIntercambios bitacora, Clock reloj) {
        this.conector = conector;
        this.propiedades = propiedades;
        this.credencial = credencial;
        this.codigos = new CodigosGestopago(propiedades.codigos());
        this.bitacora = bitacora;
        this.reloj = reloj;
    }

    @Override
    public CatalogoObtenido obtenerCatalogo() {
        for (int intento = 1; ; intento++) {
            String token;
            try {
                token = credencial.token();
            } catch (RuntimeException e) {
                log.warn("Sin credencial para consultar el catalogo de Gestopago ({})", ClasificadorFallos.describir(e));
                return CatalogoObtenido.fallido("SIN_CREDENCIAL");
            }
            Instant iniciado = reloj.instant();
            long inicio = System.nanoTime();
            RespuestaHttp respuesta;
            try {
                respuesta = conector.get(propiedades.endpoints().catalogo(), token, MAX_BYTES_CATALOGO);
            } catch (RuntimeException e) {
                registrar(intento, null, null, null, ClasificadorFallos.describir(e), inicio, iniciado);
                return CatalogoObtenido.fallido(ClasificadorFallos.describir(e));
            }
            if (respuesta.credencialRechazada()) {
                registrar(intento, respuesta.status(), null, null, null, inicio, iniciado);
                credencial.invalidar();
                if (intento < INTENTOS_MAXIMOS && credencial.renovable()) {
                    continue;
                }
                return CatalogoObtenido.fallido("HTTP_" + respuesta.status());
            }
            CatalogoObtenido catalogo = interpretar(respuesta);
            registrar(intento, respuesta.status(), catalogo.exitoso() ? "01" : null,
                    catalogo.exitoso() ? catalogo.productos().size() + " productos" : catalogo.detalle(),
                    null, inicio, iniciado);
            return catalogo;
        }
    }

    private CatalogoObtenido interpretar(RespuestaHttp respuesta) {
        if (!respuesta.exitosa()) {
            return CatalogoObtenido.fallido("HTTP_" + respuesta.status());
        }
        Optional<DocumentoXml> documento = DocumentoXml.leer(respuesta.cuerpo());
        if (documento.isEmpty()) {
            return CatalogoObtenido.fallido("RESPUESTA_ILEGIBLE");
        }
        String codigo = documento.get().texto("MENSAJE", "CODIGO").orElse(null);
        if (codigos.clasificarEnvio(codigo) != CodigosGestopago.Envio.APROBADA) {
            return CatalogoObtenido.fallido("CODIGO_" + GestopagoClient.sanear(codigo, 20));
        }
        List<ProductoProveedor> productos = new ArrayList<>();
        int descartados = 0;
        for (Element elemento : documento.get().elementos("PRODUCTOS", "producto")) {
            Optional<ProductoProveedor> producto = convertir(elemento);
            if (producto.isPresent()) {
                productos.add(producto.get());
            } else {
                descartados++;
            }
        }
        if (descartados > 0) {
            log.warn("Se descartaron {} productos del catalogo de Gestopago por datos incompletos", descartados);
        }
        if (productos.isEmpty()) {
            // Un catalogo vacio desactivaria todo: se trata como fallo, no como "no hay productos".
            return CatalogoObtenido.fallido("CATALOGO_VACIO");
        }
        return CatalogoObtenido.exitoso(productos);
    }

    Optional<ProductoProveedor> convertir(Element elemento) {
        Optional<Integer> idServicio = entero(DocumentoXml.dato(elemento, "idServicio"));
        Optional<Integer> idProducto = entero(DocumentoXml.dato(elemento, "idProducto"));
        if (idServicio.isEmpty() || idProducto.isEmpty()) {
            return Optional.empty();
        }
        String servicio = GestopagoClient.textoLibre(DocumentoXml.dato(elemento, "servicio")
                .orElse("Servicio " + idServicio.get()), 200);
        String producto = GestopagoClient.textoLibre(DocumentoXml.dato(elemento, "producto")
                .orElse("Producto " + idProducto.get()), 200);
        BigDecimal precio = decimal(DocumentoXml.dato(elemento, "precio")).orElse(BigDecimal.ZERO);
        Optional<Integer> tipoFront = entero(DocumentoXml.dato(elemento, "tipoFront"));
        String categoria = entero(DocumentoXml.dato(elemento, "idCatTipoServicio"))
                .map(tipo -> "TIPO_" + tipo).orElse(null);
        String leyenda = GestopagoClient.textoLibre(DocumentoXml.dato(elemento, "legend").orElse(null), 500);
        return Optional.of(new ProductoProveedor(idServicio.get(), servicio, categoria, idProducto.get(), producto,
                modalidad(precio, tipoFront), precio, leyenda));
    }

    /**
     * Una recarga necesita precio: en sendTx no viaja monto para ella. Un producto
     * sin precio, o con un tipoFront configurado como pago de servicio, se pide
     * con referencia y montoPago.
     */
    private Modalidad modalidad(BigDecimal precio, Optional<Integer> tipoFront) {
        boolean marcadoComoServicio = tipoFront.map(propiedades.catalogo().tiposFrontPagoServicio()::contains)
                .orElse(false);
        return marcadoComoServicio || precio.signum() <= 0 ? Modalidad.PAGO_SERVICIO : Modalidad.RECARGA;
    }

    private void registrar(int intento, Integer status, String codigo, String resumen, String error,
                           long inicio, Instant iniciado) {
        try {
            bitacora.registrar(new Intercambio(Operacion.CATALOGO, null, intento, null, status, codigo, resumen,
                    error, (System.nanoTime() - inicio) / 1_000_000, iniciado, MDC.get(Trazabilidad.CLAVE_MDC)));
        } catch (RuntimeException e) {
            log.warn("No se pudo registrar en la bitacora la consulta del catalogo ({})",
                    ClasificadorFallos.describir(e));
        }
    }

    private static Optional<Integer> entero(Optional<String> valor) {
        try {
            return valor.map(String::strip).map(Integer::valueOf);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static Optional<BigDecimal> decimal(Optional<String> valor) {
        try {
            return valor.map(texto -> texto.strip().replace(",", "")).map(BigDecimal::new)
                    .filter(numero -> numero.signum() >= 0)
                    .map(numero -> numero.setScale(2, java.math.RoundingMode.HALF_UP));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
