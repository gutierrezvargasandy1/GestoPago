package com.utng.pagosservicios.proveedor.gestopago;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Clasifica los codigos de PuntoRed segun la configuracion.
 *
 * <p>Un codigo que no aparece en ninguna lista es DESCONOCIDO y el flujo lo
 * trata como resultado indeterminado. Es la decision segura: la documentacion
 * dice que todo codigo distinto de 01 es un error, pero un error no prueba que
 * la operacion no se aplico (el 82 es un error y puede haberse aplicado).
 * Tomar un codigo desconocido como rechazo devolveria dinero que el proveedor
 * quiza si cobro.
 *
 * <p>Los codigos de sendTx y los de confirmTx se clasifican por separado: el
 * mismo numero no significa lo mismo en las dos operaciones.
 */
final class CodigosGestopago {

    enum Envio { APROBADA, RECHAZADA, DUPLICADA, SIN_RESPUESTA, DESCONOCIDA }

    enum Consulta { APLICADA, NO_APLICADA, DESCONOCIDA }

    private final Set<String> aprobada;
    private final Set<String> rechazada;
    private final Set<String> duplicada;
    private final Set<String> sinRespuesta;
    private final Set<String> consultaAplicada;
    private final Set<String> consultaNoAplicada;

    CodigosGestopago(GestopagoProperties.Codigos codigos) {
        this.aprobada = normalizar(codigos.aprobada());
        this.rechazada = normalizar(codigos.rechazada());
        this.duplicada = normalizar(codigos.duplicada());
        this.sinRespuesta = normalizar(codigos.sinRespuesta());
        this.consultaAplicada = normalizar(codigos.consultaAplicada());
        this.consultaNoAplicada = normalizar(codigos.consultaNoAplicada());
        exigirDisjuntos("envio", List.of(aprobada, rechazada, duplicada, sinRespuesta));
        exigirDisjuntos("consulta", List.of(consultaAplicada, consultaNoAplicada));
    }

    Envio clasificarEnvio(String codigo) {
        String valor = normalizar(codigo);
        if (valor == null) {
            return Envio.DESCONOCIDA;
        }
        if (aprobada.contains(valor)) {
            return Envio.APROBADA;
        }
        if (rechazada.contains(valor)) {
            return Envio.RECHAZADA;
        }
        if (duplicada.contains(valor)) {
            return Envio.DUPLICADA;
        }
        if (sinRespuesta.contains(valor)) {
            return Envio.SIN_RESPUESTA;
        }
        return Envio.DESCONOCIDA;
    }

    Consulta clasificarConsulta(String codigo) {
        String valor = normalizar(codigo);
        if (valor == null) {
            return Consulta.DESCONOCIDA;
        }
        if (consultaAplicada.contains(valor)) {
            return Consulta.APLICADA;
        }
        if (consultaNoAplicada.contains(valor)) {
            return Consulta.NO_APLICADA;
        }
        return Consulta.DESCONOCIDA;
    }

    private static void exigirDisjuntos(String operacion, List<Set<String>> grupos) {
        Set<String> vistos = new HashSet<>();
        for (Set<String> grupo : grupos) {
            for (String codigo : grupo) {
                if (!vistos.add(codigo)) {
                    throw new IllegalStateException("El codigo de Gestopago '" + codigo
                            + "' aparece en mas de una lista de " + operacion + " en gestopago.codigos");
                }
            }
        }
    }

    private static String normalizar(String codigo) {
        if (codigo == null) {
            return null;
        }
        String valor = codigo.strip();
        return valor.isEmpty() ? null : valor;
    }

    private static Set<String> normalizar(List<String> valores) {
        return valores.stream()
                .map(CodigosGestopago::normalizar)
                .filter(valor -> valor != null)
                .collect(Collectors.toUnmodifiableSet());
    }
}
