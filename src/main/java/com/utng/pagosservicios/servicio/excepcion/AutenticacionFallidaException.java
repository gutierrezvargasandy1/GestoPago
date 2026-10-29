package com.utng.pagosservicios.servicio.excepcion;

/**
 * Credenciales incorrectas (401). El mensaje es el mismo para usuario
 * inexistente y contrasena incorrecta, para no revelar que usuarios existen.
 */
public class AutenticacionFallidaException extends PagoException {

    public AutenticacionFallidaException() {
        super(CodigoError.CREDENCIALES_INVALIDAS, "Usuario o contraseña incorrectos.");
    }
}
