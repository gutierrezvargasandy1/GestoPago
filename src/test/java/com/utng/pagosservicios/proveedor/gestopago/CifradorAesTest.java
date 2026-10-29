package com.utng.pagosservicios.proveedor.gestopago;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CifradorAesTest {

    /** Llave 00..1f e IV 00..0f: vector conocido, calculado aparte con openssl. */
    private static final String LLAVE = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";
    private static final String IV = "AAECAwQFBgcICQoLDA0ODw==";

    private final CifradorAes cifrador = new CifradorAes(LLAVE, IV);

    /**
     * Mismo resultado que
     * {@code printf '%s' '<json>' | openssl enc -aes-256-cbc -K 0001..1f -iv 0001..0f -base64 -A}:
     * es la prueba de que el proveedor (que usa el mismo algoritmo) podra descifrarlo.
     */
    @Test
    void coincideConElVectorCalculadoConOpenssl() {
        assertThat(cifrador.cifrar("{\"idProducto\":2003,\"upc\":\"123456789012345\"}"))
                .isEqualTo("bMSBczYvtXg/9FpJNpjLVUA8/KPE5PGo0FkiEhtHmWZKQMJSTh8kfe8FGJRYR9FE");
    }

    @Test
    void cifraElTextoComoUtf8() {
        assertThat(cifrador.cifrar("Operación ñ")).isEqualTo("XLD2GIYhIXwjP7KUXl2jsQ==");
    }

    @Test
    void idaYVuelta() {
        String json = "{\"telefono\":\"5512345678\",\"montoPago\":250.50}";
        assertThat(cifrador.descifrar(cifrador.cifrar(json))).isEqualTo(json);
    }

    @Test
    void rechazaUnaLlaveQueNoEsAes256SinMostrarla() {
        assertThatThrownBy(() -> new CifradorAes("AAECAwQFBgcICQoLDA0ODw==", IV))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32 bytes")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("AAECAwQFBgcICQoLDA0ODw=="));
    }

    @Test
    void rechazaUnIvDeLongitudIncorrecta() {
        assertThatThrownBy(() -> new CifradorAes(LLAVE, LLAVE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("16 bytes");
    }

    @Test
    void rechazaBase64InvalidoYValoresVacios() {
        assertThatThrownBy(() -> new CifradorAes("esto no es base64!", IV))
                .isInstanceOf(IllegalArgumentException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("esto no es base64!"));
        assertThatThrownBy(() -> new CifradorAes(" ", IV))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GESTOPAGO_AES_KEY");
    }
}
