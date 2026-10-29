package com.utng.pagosservicios;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PagosServiciosApplication {

    public static void main(String[] args) {
        SpringApplication.run(PagosServiciosApplication.class, args);
    }
}
