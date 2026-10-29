package com.utng.pagosservicios.servicio;

import com.utng.pagosservicios.config.PagosProperties;
import com.utng.pagosservicios.proveedor.CatalogoObtenido;
import com.utng.pagosservicios.proveedor.CatalogoProveedor;
import com.utng.pagosservicios.repositorio.RegistroSincronizacionJdbc;
import com.utng.pagosservicios.util.DescripcionError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Mantiene el catalogo local al dia con getProductList, respetando las reglas
 * del proveedor: consultarlo una vez al dia, nunca mas de 3 veces en 24 h, y no
 * usarlo como fuente en linea (los clientes leen siempre la copia local).
 *
 * <p>Pasos: revisar limites, registrar el intento (antes de llamar, para que
 * cuente aunque el proceso muera), llamar al proveedor FUERA de toda
 * transaccion, aplicar el resultado en una transaccion y registrar el fin.
 */
@Service
public class SincronizacionCatalogoService {

    private static final Logger log = LoggerFactory.getLogger(SincronizacionCatalogoService.class);
    private static final Duration VENTANA_LIMITE = Duration.ofHours(24);

    public enum Resultado { EXITOSA, FALLIDA, OMITIDA_RECIENTE, OMITIDA_LIMITE_DIARIO, OMITIDA_EN_CURSO }

    private final CatalogoProveedor proveedor;
    private final CatalogoActualizador actualizador;
    private final RegistroSincronizacionJdbc registros;
    private final PagosProperties.Catalogo configuracion;
    private final Clock reloj;
    private final ReentrantLock enCurso = new ReentrantLock();

    public SincronizacionCatalogoService(CatalogoProveedor proveedor, CatalogoActualizador actualizador,
                                         RegistroSincronizacionJdbc registros, PagosProperties propiedades,
                                         Clock reloj) {
        this.proveedor = proveedor;
        this.actualizador = actualizador;
        this.registros = registros;
        this.configuracion = propiedades.catalogo();
        this.reloj = reloj;
    }

    public Resultado sincronizarSiCorresponde() {
        if (!enCurso.tryLock()) {
            return Resultado.OMITIDA_EN_CURSO;
        }
        try {
            return sincronizar();
        } finally {
            enCurso.unlock();
        }
    }

    private Resultado sincronizar() {
        Instant ahora = reloj.instant();
        Optional<Instant> ultima = registros.ultimaExitosa();
        if (ultima.isPresent() && ahora.isBefore(ultima.get().plus(configuracion.frecuenciaMinima()))) {
            log.debug("Catalogo sincronizado el {}: aun no toca", ultima.get());
            return Resultado.OMITIDA_RECIENTE;
        }
        int intentos = registros.intentosDesde(ahora.minus(VENTANA_LIMITE));
        if (intentos >= configuracion.maxLlamadasPorDia()) {
            log.warn("Se alcanzo el limite de {} consultas diarias al catalogo del proveedor; se reintentara despues",
                    configuracion.maxLlamadasPorDia());
            return Resultado.OMITIDA_LIMITE_DIARIO;
        }

        long registro = registros.registrarInicio(ahora);
        CatalogoObtenido catalogo = proveedor.obtenerCatalogo();
        if (!catalogo.exitoso()) {
            log.warn("No se obtuvo el catalogo del proveedor ({}); se conserva el catalogo local", catalogo.detalle());
            registros.registrarFin(registro, reloj.instant(), false, 0, catalogo.detalle());
            return Resultado.FALLIDA;
        }
        try {
            int activos = actualizador.aplicar(catalogo.productos(), reloj.instant());
            registros.registrarFin(registro, reloj.instant(), true, activos, null);
            log.info("Catalogo sincronizado con el proveedor: {} productos activos", activos);
            return Resultado.EXITOSA;
        } catch (RuntimeException e) {
            log.error("No se pudo aplicar el catalogo del proveedor: {}", DescripcionError.de(e));
            registros.registrarFin(registro, reloj.instant(), false, 0, "ERROR_AL_APLICAR");
            return Resultado.FALLIDA;
        }
    }
}
