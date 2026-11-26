package com.utng.pagosservicios.web.dto;

import com.utng.pagosservicios.servicio.PerfilService.Perfil;

import java.time.Instant;
import java.util.List;

/**
 * Perfil del cliente autenticado. usuario y correo son el mismo dato (las cuentas
 * creadas antes del registro con correo conservan su usuario anterior).
 * modoDemostracion avisa a las apps que las compras no van a GestoPago.
 */
public record PerfilResponse(String usuario, String correo, String nombre, boolean dosPasosActivo,
                             boolean pagosHabilitados, String terminosVersion, Instant terminosAceptadosEn,
                             boolean modoDemostracion, SesionInfo sesion, List<CuentaInfo> cuentas) {

    public record SesionInfo(String plataforma, Instant iniciadaEn, Instant expiraEn, long minutosInactividad) {
    }

    public record CuentaInfo(String numero, String saldo, String estado, String limiteDiario) {
    }

    public static PerfilResponse de(Perfil perfil) {
        return new PerfilResponse(
                perfil.cliente().getUsuario(),
                perfil.cliente().getUsuario(),
                perfil.cliente().getNombre(),
                perfil.cliente().isTotpActivo(),
                perfil.cliente().puedePagar(),
                perfil.cliente().getTerminosVersion(),
                perfil.cliente().getTerminosAceptadosEn(),
                perfil.modoDemostracion(),
                perfil.sesion().map(s -> new SesionInfo(s.plataforma().name(), s.creadaEn(), s.expiraEn(),
                        perfil.minutosInactividad())).orElse(null),
                perfil.cuentas().stream().map(c -> new CuentaInfo(c.getNumero(), c.getSaldo().toPlainString(),
                        c.getEstado().name(), c.getLimiteDiario().toPlainString())).toList());
    }
}
