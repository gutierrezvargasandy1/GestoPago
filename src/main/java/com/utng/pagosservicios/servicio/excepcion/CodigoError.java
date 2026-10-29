package com.utng.pagosservicios.servicio.excepcion;

/**
 * Catalogo de errores de la API. No conoce HTTP: la traduccion a codigo de
 * estado la hace la capa web, asi las reglas de negocio no dependen del
 * protocolo por el que se exponen.
 */
public enum CodigoError {

    SOLICITUD_INVALIDA("Solicitud inválida"),
    CLAVE_IDEMPOTENCIA_INVALIDA("Clave de idempotencia inválida"),
    NO_AUTENTICADO("No autenticado"),
    CREDENCIALES_INVALIDAS("Credenciales inválidas"),
    ACCESO_DENEGADO("Acceso denegado"),
    CLIENTE_NO_HABILITADO("Cliente no habilitado"),
    CUENTA_NO_AUTORIZADA("Cuenta no autorizada"),
    RECURSO_NO_ENCONTRADO("Recurso no encontrado"),
    PAGO_NO_ENCONTRADO("Pago no encontrado"),
    METODO_NO_PERMITIDO("Método no permitido"),
    TIPO_CONTENIDO_NO_SOPORTADO("Tipo de contenido no soportado"),
    CLAVE_IDEMPOTENCIA_REUTILIZADA("Clave de idempotencia reutilizada"),
    PAGO_EN_PROCESO("Pago en proceso"),
    OPERACION_CONCURRENTE("Operación concurrente"),
    CUENTA_INACTIVA("Cuenta inactiva"),
    SALDO_INSUFICIENTE("Saldo insuficiente"),
    LIMITE_DIARIO_EXCEDIDO("Límite diario excedido"),
    PRODUCTO_NO_DISPONIBLE("Producto no disponible"),
    MONTO_NO_PERMITIDO("Monto no permitido"),
    REFERENCIA_INVALIDA("Referencia inválida"),
    PAGO_RECHAZADO("Pago rechazado por el proveedor"),
    PAGO_DUPLICADO("Pago duplicado en el proveedor"),
    PROVEEDOR_NO_DISPONIBLE("Proveedor no disponible"),
        USUARIO_YA_REGISTRADO("Usuario ya registrado"),
    ERROR_INTERNO("Error interno");

    private final String titulo;

    CodigoError(String titulo) {
        this.titulo = titulo;
    }

    public String getTitulo() {
        return titulo;
    }
}
