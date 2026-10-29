package com.utng.pagosservicios.proveedor.gestopago;

import java.time.Duration;
import java.util.List;

final class PropiedadesDePrueba {

    static final String ENVIO = "/sistema/service/sendTx.do";
    static final String CONSULTA = "/sistema/service/confirmTx.do";
    static final String AUTENTICACION = "/sistema/app/jwt-gp/authenticate/";
    static final String CATALOGO = "/sistema/service/getProductList.do";
    static final String API_KEY = "clave-api-unitaria";

    private PropiedadesDePrueba() {
    }

    static GestopagoProperties gestopago(String urlBase, String rutaConsulta, String token) {
        return gestopago(urlBase, rutaConsulta, token, false, List.of());
    }

    static GestopagoProperties gestopago(String urlBase, String rutaConsulta, String token,
                                         boolean credencialesEnUrl, List<Integer> tiposFrontPagoServicio) {
        return new GestopagoProperties(urlBase, token, API_KEY,
                new GestopagoProperties.Autenticacion("DIST-1", "DISP-1", "secreto+distribuidor", Duration.ofHours(23),
                        credencialesEnUrl),
                new GestopagoProperties.Cifrado(MensajeCifrado.LLAVE_PRUEBAS, MensajeCifrado.IV_PRUEBAS),
                "API-PAGOS",
                "America/Mexico_City",
                new GestopagoProperties.Endpoints(ENVIO, AUTENTICACION, CATALOGO, rutaConsulta),
                new GestopagoProperties.Timeouts(Duration.ofMillis(300), Duration.ofMillis(800)),
                new GestopagoProperties.Codigos(List.of("01"), List.of("02"), List.of("06"), List.of("82"),
                        List.of("01"), List.of("04")),
                new GestopagoProperties.Catalogo(tiposFrontPagoServicio));
    }
}
