package com.utng.pagosservicios.dominio.pago;

/** Por que una transaccion termino reversada o quedo pendiente. */
public enum MotivoResolucion {

    RECHAZADA_POR_PROVEEDOR("El proveedor rechazó la operación."),
    DUPLICADA_EN_PROVEEDOR("El proveedor detectó un pago idéntico reciente y no aplicó este. "
            + "Si necesita repetirlo, espere 15 minutos."),
    PROVEEDOR_NO_DISPONIBLE("El proveedor no procesó la operación."),
    NUNCA_ENVIADA("La operación no llegó a enviarse al proveedor."),
    NO_ENCONTRADA_EN_PROVEEDOR("El proveedor confirmó que no aplicó la operación."),
    RESULTADO_INDETERMINADO("No se obtuvo una respuesta concluyente del proveedor."),
    CONSULTA_NO_SOPORTADA("El proveedor no ofrece consulta de estatus; requiere revisión manual."),
    CONSULTA_VENCIDA("Pasó el plazo en que el proveedor permite confirmar la operación; requiere revisión manual."),
    CONSULTA_IMPOSIBLE("No se pudieron reunir los datos para consultar al proveedor; requiere revisión manual."),
    INTENTOS_AGOTADOS("Se agotaron los intentos automáticos de conciliación.");

    private final String descripcion;

    MotivoResolucion(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
