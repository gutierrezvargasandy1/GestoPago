package com.utng.pagosservicios.seguridad;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TotpTest {

    /** Secreto "12345678901234567890" del RFC 6238 en Base32. */
    private static final String SECRETO_RFC = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    @Test
    void coincideConLosVectoresDelRfc6238() {
        assertThat(Totp.codigo(SECRETO_RFC, Instant.ofEpochSecond(59))).isEqualTo("287082");
        assertThat(Totp.codigo(SECRETO_RFC, Instant.ofEpochSecond(1111111109))).isEqualTo("081804");
        assertThat(Totp.codigo(SECRETO_RFC, Instant.ofEpochSecond(1234567890))).isEqualTo("005924");
        assertThat(Totp.codigo(SECRETO_RFC, Instant.ofEpochSecond(2000000000))).isEqualTo("279037");
    }

    @Test
    void unCodigoYaUsadoNoSeAceptaOtraVez() {
        String secreto = Totp.generarSecreto();
        Instant ahora = Instant.now();
        String codigo = Totp.codigo(secreto, ahora);

        long paso = Totp.verificar(secreto, codigo, ahora, null).orElseThrow();

        assertThat(Totp.verificar(secreto, codigo, ahora, paso)).isEmpty();
    }

    @Test
    void rechazaCodigosMalFormadosOViejos() {
        String secreto = Totp.generarSecreto();
        Instant ahora = Instant.now();
        assertThat(Totp.verificar(secreto, "12345", ahora, null)).isEmpty();
        assertThat(Totp.verificar(secreto, "abcdef", ahora, null)).isEmpty();
        assertThat(Totp.verificar(secreto, Totp.codigo(secreto, ahora.minusSeconds(300)), ahora, null)).isEmpty();
    }
}
