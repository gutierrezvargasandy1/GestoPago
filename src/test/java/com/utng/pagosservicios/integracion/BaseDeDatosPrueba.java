package com.utng.pagosservicios.integracion;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * PostgreSQL real para las pruebas de integracion. Por defecto levanta un
 * contenedor con Testcontainers (requiere Docker). Sin Docker, se puede apuntar
 * a una base local vacia con TEST_DB_URL, TEST_DB_USERNAME y TEST_DB_PASSWORD.
 *
 * <p>No se usa H2: el bloqueo SELECT ... FOR UPDATE, los CHECK y el
 * comportamiento del UNIQUE ante concurrencia son justo lo que se quiere probar.
 */
final class BaseDeDatosPrueba {

    private static PostgreSQLContainer contenedor;

    private BaseDeDatosPrueba() {
    }

    static String urlExterna() {
        String url = System.getenv("TEST_DB_URL");
        return url == null || url.isBlank() ? null : url;
    }

    static synchronized void registrar(DynamicPropertyRegistry registro) {
        String url = urlExterna();
        if (url != null) {
            registro.add("spring.datasource.url", () -> url);
            registro.add("spring.datasource.username", () -> variable("TEST_DB_USERNAME", "postgres"));
            registro.add("spring.datasource.password", () -> variable("TEST_DB_PASSWORD", "postgres"));
            return;
        }
        if (contenedor == null) {
            contenedor = new PostgreSQLContainer("postgres:17-alpine");
            contenedor.start();
        }
        PostgreSQLContainer activo = contenedor;
        registro.add("spring.datasource.url", activo::getJdbcUrl);
        registro.add("spring.datasource.username", activo::getUsername);
        registro.add("spring.datasource.password", activo::getPassword);
    }

    private static String variable(String nombre, String porDefecto) {
        String valor = System.getenv(nombre);
        return valor == null || valor.isBlank() ? porDefecto : valor;
    }
}
