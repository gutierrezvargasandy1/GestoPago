package com.utng.pagosservicios.seguridad;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "seguridad")
public record SeguridadProperties(@NotNull @Valid Jwt jwt, Sesion sesion, Cors cors) {

    public SeguridadProperties {
        sesion = sesion == null ? new Sesion(null, null) : sesion;
        cors = cors == null ? new Cors(null) : cors;
    }

    /**
     * @param vigencia vida del desafio del segundo factor (entre la contrasena y el codigo)
     */
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

    /**
     * @param duracionMaxima vida maxima de una sesion (y de su token de acceso)
     * @param inactividad    la sesion se cierra sola si no hay peticiones en este tiempo
     */
    public record Sesion(Duration duracionMaxima, Duration inactividad) {
        public Sesion {
            duracionMaxima = duracionMaxima == null ? Duration.ofHours(8) : duracionMaxima;
            inactividad = inactividad == null ? Duration.ofMinutes(30) : inactividad;
        }
    }

    /** Origenes (front web) que pueden llamar a la API desde el navegador. */
    public record Cors(List<String> origenes) {
        public Cors {
            origenes = origenes == null || origenes.isEmpty()
                    ? List.of("http://localhost:[*]", "http://127.0.0.1:[*]")
                    : List.copyOf(origenes);
        }
    }
}
