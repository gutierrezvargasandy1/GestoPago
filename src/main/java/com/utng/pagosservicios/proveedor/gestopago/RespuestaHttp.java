package com.utng.pagosservicios.proveedor.gestopago;

import java.nio.charset.StandardCharsets;

/**
 * Estado y cuerpo crudo de una respuesta del proveedor. El cuerpo se guarda en
 * bytes: el parser XML respeta asi la codificacion que declare el documento.
 */
record RespuestaHttp(int status, byte[] cuerpo) {

    RespuestaHttp {
        cuerpo = cuerpo == null ? new byte[0] : cuerpo;
    }

    boolean exitosa() {
        return status >= 200 && status < 300;
    }

    /** 401 o 403: el proveedor rechazo la credencial antes de procesar nada. */
    boolean credencialRechazada() {
        return status == 401 || status == 403;
    }

    String texto() {
        return new String(cuerpo, StandardCharsets.UTF_8);
    }
}
