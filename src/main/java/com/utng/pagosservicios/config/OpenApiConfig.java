package com.utng.pagosservicios.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Documentacion OpenAPI: agrega el boton "Authorize" para pegar el JWT de /api/v1/auth/token. */
@Configuration
public class OpenApiConfig {

    private static final String ESQUEMA_JWT = "bearerAuth";

    @Bean
    public OpenAPI documentacionApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("API de pagos de servicios")
                        .version("v1")
                        .description("Recargas y pagos de servicios con PuntoRed (GestoPago). "
                                + "1) POST /api/v1/auth/registro  2) POST /api/v1/auth/token  "
                                + "3) Authorize con el token  4) POST /api/v1/pagos con Idempotency-Key."))
                .components(new Components().addSecuritySchemes(ESQUEMA_JWT, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_JWT));
    }
}