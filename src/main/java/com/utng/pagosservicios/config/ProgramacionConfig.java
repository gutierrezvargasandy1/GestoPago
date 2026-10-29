package com.utng.pagosservicios.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Habilita las tareas programadas. Cada tarea decide por su cuenta si se
 * registra (ConciliacionProgramada y CatalogoProgramado son condicionales), asi
 * que apagar una no apaga la otra.
 */
@Configuration
@EnableScheduling
public class ProgramacionConfig {
}
