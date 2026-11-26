package com.utng.pagosservicios.proveedor.demo;

import com.utng.pagosservicios.proveedor.OrdenPago;
import com.utng.pagosservicios.proveedor.ProveedorPagos;
import com.utng.pagosservicios.proveedor.ResultadoConsulta;
import com.utng.pagosservicios.proveedor.ResultadoEnvio;
import com.utng.pagosservicios.util.Enmascarar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.time.Duration;

/**
 * Proveedor de DEMOSTRACION: se usa cuando GestoPago no esta disponible para
 * poder mostrar cada estado de una compra. No hace ninguna recarga ni pago real.
 *
 * <p>El resultado depende de los ultimos 4 digitos del telefono o de la
 * referencia (igual que el simulador de PuntoRed del proyecto):
 * <pre>
 *   ...0000  rechazada (codigo 02, saldo insuficiente en el proveedor)  -> se devuelve el dinero
 *   ...0006  rechazada por duplicada (codigo 06)                         -> se devuelve el dinero
 *   ...5000  error de comunicacion: el proveedor no respondio            -> no se cobra
 *   ...9999  pendiente: sin respuesta a tiempo; al conciliar se CONFIRMA
 *   ...8282  pendiente: sin respuesta (82); al conciliar NO estaba aplicada -> se devuelve
 *   ...7777  pendiente: la consulta nunca es concluyente -> revision manual
 *   otra     exitosa (codigo 01)
 * </pre>
 * La decision se toma con la referencia y no con memoria, asi una operacion
 * pendiente se resuelve igual aunque el servidor se reinicie.
 */
public class ProveedorDemostracion implements ProveedorPagos {

    private static final Logger log = LoggerFactory.getLogger(ProveedorDemostracion.class);

    private final Duration demora;
    private final SecureRandom aleatorio = new SecureRandom();

    public ProveedorDemostracion(Duration demora) {
        this.demora = demora;
    }

    @Override
    public boolean disponible() {
        return true;
    }

    @Override
    public ResultadoEnvio enviar(OrdenPago orden) {
        esperar(demora);
        String terminacion = terminacion(orden.referencia());
        ResultadoEnvio resultado = switch (terminacion) {
            case "0000" -> ResultadoEnvio.rechazada("02");
            case "0006" -> ResultadoEnvio.duplicada("06");
            case "5000" -> ResultadoEnvio.noProcesada("SIN_COMUNICACION");
            case "9999", "7777" -> ResultadoEnvio.indeterminada("SIN_RESPUESTA");
            case "8282" -> ResultadoEnvio.indeterminada("82");
            default -> ResultadoEnvio.aprobada("01", folio(), autorizacion());
        };
        log.info("Modo demostracion: orden {} (referencia {}) -> {}", orden.referenciaExterna(),
                Enmascarar.ultimos(orden.referencia()), resultado.desenlace());
        return resultado;
    }

    @Override
    public ResultadoConsulta consultar(OrdenPago orden) {
        esperar(demora.dividedBy(3));
        return switch (terminacion(orden.referencia())) {
            case "8282" -> ResultadoConsulta.noEncontrada("04");
            case "7777" -> ResultadoConsulta.indeterminada("SIN_RESPUESTA");
            case "0000" -> ResultadoConsulta.rechazada("02");
            default -> ResultadoConsulta.aprobada("01", folio(), autorizacion());
        };
    }

    static String terminacion(String referencia) {
        if (referencia == null) {
            return "";
        }
        String limpia = referencia.strip();
        return limpia.length() <= 4 ? limpia : limpia.substring(limpia.length() - 4);
    }

    /** El prefijo DEMO deja claro en el historial que la operacion fue simulada. */
    private String folio() {
        return "DEMO-" + digitos(9);
    }

    private String autorizacion() {
        return "DEMO" + digitos(6);
    }

    private String digitos(int cantidad) {
        StringBuilder texto = new StringBuilder(cantidad);
        for (int i = 0; i < cantidad; i++) {
            texto.append(aleatorio.nextInt(10));
        }
        return texto.toString();
    }

    private static void esperar(Duration duracion) {
        if (duracion.isZero() || duracion.isNegative()) {
            return;
        }
        try {
            Thread.sleep(duracion);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
