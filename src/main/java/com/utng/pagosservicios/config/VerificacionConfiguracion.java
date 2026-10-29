package com.utng.pagosservicios.config;

import com.utng.pagosservicios.proveedor.gestopago.GestopagoProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Validaciones que cruzan grupos de propiedades. Si fallan, la aplicacion no
 * arranca: es preferible a operar con una configuracion que puede perder dinero.
 */
@Configuration
public class VerificacionConfiguracion {

    public VerificacionConfiguracion(PagosProperties pagos, GestopagoProperties gestopago) {
        Duration esperaMaximaLlamada = gestopago.timeout().conexion().plus(gestopago.timeout().lectura());
        PagosProperties.Conciliacion conciliacion = pagos.conciliacion();
        // Si la conciliacion consultara una operacion mientras la peticion original
        // aun espera respuesta, podria resolverla con informacion incompleta.
        if (conciliacion.margenInicial().compareTo(esperaMaximaLlamada) <= 0) {
            throw new IllegalStateException("pagos.conciliacion.margen-inicial (" + conciliacion.margenInicial()
                    + ") debe ser mayor que gestopago.timeout.conexion + gestopago.timeout.lectura ("
                    + esperaMaximaLlamada + ")");
        }
        if (conciliacion.vigenciaConsulta().compareTo(conciliacion.margenInicial()) <= 0) {
            throw new IllegalStateException("pagos.conciliacion.vigencia-consulta debe ser mayor que "
                    + "pagos.conciliacion.margen-inicial: si no, ninguna operacion alcanzaria a consultarse");
        }
        PagosProperties.Catalogo catalogo = pagos.catalogo();
        if (catalogo.montoMinimoServicios().compareTo(catalogo.montoMaximoServicios()) > 0) {
            throw new IllegalStateException("pagos.catalogo.monto-minimo-servicios no puede ser mayor que "
                    + "pagos.catalogo.monto-maximo-servicios");
        }
    }
}
