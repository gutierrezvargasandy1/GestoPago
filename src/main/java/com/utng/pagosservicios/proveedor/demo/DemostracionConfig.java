package com.utng.pagosservicios.proveedor.demo;

import com.utng.pagosservicios.proveedor.ProveedorPagos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;

/**
 * Activa el proveedor de demostracion con pagos.demostracion.activa=true (perfil
 * "demo"). @Primary: PagoService y la conciliacion lo usan en lugar de GestoPago,
 * sin tocar su configuracion.
 */
@Configuration
@ConditionalOnProperty(prefix = "pagos.demostracion", name = "activa", havingValue = "true")
public class DemostracionConfig {

    private static final Logger log = LoggerFactory.getLogger(DemostracionConfig.class);

    @Bean
    @Primary
    public ProveedorPagos proveedorDemostracion() {
        log.warn("MODO DEMOSTRACION ACTIVO: las compras NO se envian a GestoPago; su resultado lo genera el sistema");
        return new ProveedorDemostracion(Duration.ofMillis(900));
    }
}
