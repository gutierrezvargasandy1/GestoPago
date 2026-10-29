package com.utng.pagosservicios.proveedor;

/** Respuesta del proveedor al preguntarle por una operacion ya enviada. */
public record ResultadoConsulta(Estado estado, String codigo, String folio, String autorizacion,
                                DatosCanje canje) {

    public enum Estado {
        APROBADA,
        RECHAZADA,
        /** El proveedor afirma que no aplico la operacion. */
        NO_ENCONTRADA,
        /** La consulta misma fallo o la respuesta no es concluyente. */
        INDETERMINADA,
        /** El proveedor no ofrece consulta de estatus. */
        NO_SOPORTADA
    }

    public static ResultadoConsulta aprobada(String codigo, String folio, String autorizacion) {
        return aprobada(codigo, folio, autorizacion, null);
    }

    public static ResultadoConsulta aprobada(String codigo, String folio, String autorizacion, DatosCanje canje) {
        return new ResultadoConsulta(Estado.APROBADA, codigo, folio, autorizacion, canje);
    }

    public static ResultadoConsulta rechazada(String codigo) {
        return new ResultadoConsulta(Estado.RECHAZADA, codigo, null, null, null);
    }

    public static ResultadoConsulta noEncontrada(String codigo) {
        return new ResultadoConsulta(Estado.NO_ENCONTRADA, codigo, null, null, null);
    }

    public static ResultadoConsulta indeterminada(String detalle) {
        return new ResultadoConsulta(Estado.INDETERMINADA, detalle, null, null, null);
    }

    public static ResultadoConsulta noSoportada() {
        return new ResultadoConsulta(Estado.NO_SOPORTADA, null, null, null, null);
    }
}
