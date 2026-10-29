package com.utng.pagosservicios.proveedor.gestopago;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RespuestaPuntoRedTest {

    private static byte[] utf8(String texto) {
        return texto.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void leeUnaRespuestaExitosa() {
        RespuestaPuntoRed respuesta = RespuestaPuntoRed.leer(utf8(XmlPuntoRed.exito("778899", "A1B2C3"))).orElseThrow();

        assertThat(respuesta.codigo()).isEqualTo("01");
        assertThat(respuesta.texto()).isEqualTo("Operacion realizada con exito");
        assertThat(respuesta.idTransaccion()).isEqualTo("778899");
        assertThat(respuesta.autorizacion()).isEqualTo("A1B2C3");
    }

    @Test
    void unaAutorizacionMenosUnoEsQueNoHuboAutorizacion() {
        RespuestaPuntoRed respuesta = RespuestaPuntoRed.leer(utf8(XmlPuntoRed.codigo("02", "Saldo insuficiente")))
                .orElseThrow();
        assertThat(respuesta.codigo()).isEqualTo("02");
        assertThat(respuesta.autorizacion()).isNull();
    }

    @Test
    void leePinYLeyendaDeUnaTarjetaDeRegalo() {
        RespuestaPuntoRed respuesta = RespuestaPuntoRed.leer(utf8(XmlPuntoRed.exitoConPin("1", "9999-0000", "Canje")))
                .orElseThrow();
        assertThat(respuesta.pin()).isEqualTo("9999-0000");
        assertThat(respuesta.leyenda()).isEqualTo("Canje");
        assertThat(respuesta.toString()).doesNotContain("9999-0000");
    }

    @Test
    void respetaLaCodificacionQueDeclaraElDocumento() {
        byte[] latin1 = ("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><RESPONSE><MENSAJE><CODIGO>01</CODIGO>"
                + "<TEXTO>Operación</TEXTO></MENSAJE></RESPONSE>").getBytes(StandardCharsets.ISO_8859_1);
        assertThat(RespuestaPuntoRed.leer(latin1).orElseThrow().texto()).isEqualTo("Operación");
    }

    @Test
    void toleraCambiosDeMayusculasYEspacios() {
        RespuestaPuntoRed respuesta = RespuestaPuntoRed.leer(utf8("<response><mensaje><codigo> 06 </codigo></mensaje></response>"))
                .orElseThrow();
        assertThat(respuesta.codigo()).isEqualTo("06");
    }

    @Test
    void loQueNoTraeCodigoEsIlegible() {
        assertThat(RespuestaPuntoRed.leer(utf8("<html>Mantenimiento</html>"))).isEmpty();
        assertThat(RespuestaPuntoRed.leer(utf8("{\"codigo\":\"01\"}"))).isEmpty();
        assertThat(RespuestaPuntoRed.leer(utf8("<RESPONSE><MENSAJE><CODIGO>01"))).isEmpty();
        assertThat(RespuestaPuntoRed.leer(new byte[0])).isEmpty();
    }

    /** XXE: una respuesta maliciosa no debe poder leer archivos del servidor. */
    @Test
    void rechazaEntidadesExternas(@TempDir Path carpeta) throws IOException {
        Path secreto = Files.writeString(carpeta.resolve("secreto.txt"), "CONTENIDO-SECRETO");
        String xxe = "<?xml version=\"1.0\"?><!DOCTYPE r [<!ENTITY x SYSTEM \"" + secreto.toUri() + "\">]>"
                + "<RESPONSE><MENSAJE><CODIGO>&x;</CODIGO></MENSAJE></RESPONSE>";
        assertThat(RespuestaPuntoRed.leer(utf8(xxe))).isEmpty();
    }

    @Test
    void rechazaLaExpansionDeEntidades() {
        String bomba = "<?xml version=\"1.0\"?><!DOCTYPE l [<!ENTITY a \"aaaaaaaaaa\">"
                + "<!ENTITY b \"&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;\">]>"
                + "<RESPONSE><MENSAJE><CODIGO>&b;</CODIGO></MENSAJE></RESPONSE>";
        assertThat(RespuestaPuntoRed.leer(utf8(bomba))).isEmpty();
    }

    @Test
    void laCopiaParaLaBitacoraOcultaPinYReferencia() {
        String xml = XmlPuntoRed.exito("1", "A").replace("<MONTO>", "<PIN>1234-5678</PIN><MONTO>");

        String bitacora = RespuestaPuntoRed.paraBitacora(xml);

        assertThat(bitacora).contains("<PIN>****</PIN>").doesNotContain("1234-5678");
        assertThat(bitacora).contains("<REFERENCIA>******5678</REFERENCIA>").doesNotContain("5512345678");
        assertThat(bitacora).contains("<ID_TX>1</ID_TX>");
    }
}
