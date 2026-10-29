package com.utng.pagosservicios.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class AplicacionConfig {

    /** Reloj inyectable: la logica que depende del tiempo se puede probar con un reloj fijo. */
    @Bean
    public Clock reloj() {
        return Clock.systemUTC();
    }
}
