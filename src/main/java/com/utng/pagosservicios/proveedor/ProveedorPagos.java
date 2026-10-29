package com.utng.pagosservicios.proveedor;

/**
 * Puerto hacia el proveedor de pagos. Los servicios dependen de esta interfaz,
 * no de Gestopago (inversion de dependencias): agregar otro proveedor es
 * escribir otra implementacion sin tocar la logica de negocio, y en pruebas se
 * sustituye por un doble.
 *
 * <p>Contrato: las implementaciones no lanzan excepciones por fallos esperados
 * (red, timeouts, errores HTTP); los traducen a un desenlace explicito.
 */
public interface ProveedorPagos {

    /** false cuando se sabe de antemano que una llamada no llegaria (circuito abierto). */
    boolean disponible();

    ResultadoEnvio enviar(OrdenPago orden);

    /**
     * Pregunta que paso con una orden ya enviada. Recibe la orden completa
     * porque hay proveedores (PuntoRed con confirmTx) que identifican la
     * operacion por sus datos y no solo por un folio.
     */
    ResultadoConsulta consultar(OrdenPago orden);
}
