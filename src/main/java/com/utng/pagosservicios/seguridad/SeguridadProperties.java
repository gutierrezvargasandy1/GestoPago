package com.utng.pagosservicios.seguridad;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "seguridad")
public record SeguridadProperties(@NotNull @Valid Jwt jwt) {

    public record Jwt(@NotBlank String secreto, @NotBlank String emisor, @NotNull Duration vigencia) {

        /** HS256 exige una clave de al menos 256 bits; una mas corta se puede atacar por fuerza bruta. */
        public Jwt {
            if (secreto != null && secreto.getBytes(StandardCharsets.UTF_8).length < 32) {
                throw new IllegalArgumentException(
                        "seguridad.jwt.secreto (JWT_SECRETO) debe tener al menos 32 bytes para HS256");
            }
        }

        @Override
        public String toString() {
            return "Jwt[secreto=****, emisor=" + emisor + ", vigencia=" + vigencia + "]";
        }
    }
}
