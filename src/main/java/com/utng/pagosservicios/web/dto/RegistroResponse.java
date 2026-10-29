package com.utng.pagosservicios.web.dto;

import com.utng.pagosservicios.servicio.RegistroService.ClienteRegistrado;

/** Lo que se devuelve al registrarse: nunca el hash de la contrasena. */
public record RegistroResponse(String usuario, String nombre, String numeroCuenta, String saldo) {

    public static RegistroResponse de(ClienteRegistrado registrado) {
        return new RegistroResponse(registrado.cliente().getUsuario(), registrado.cliente().getNombre(),
                registrado.cuenta().getNumero(), registrado.cuenta().getSaldo().toPlainString());
    }
}