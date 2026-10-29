package com.utng.pagosservicios.proveedor.gestopago;

import com.utng.pagosservicios.util.Enmascarar;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Respuesta XML de sendTx y confirmTx:
 *
 * <pre>
 * &lt;RESPONSE&gt;
 *   &lt;ID_TX&gt;...&lt;/ID_TX&gt;  &lt;NUM_AUTORIZACION&gt;...&lt;/NUM_AUTORIZACION&gt;
 *   &lt;SALDO&gt;, &lt;COMISION&gt;, &lt;FECHA&gt;, &lt;MONTO&gt;, &lt;REFERENCIA&gt;
 *   &lt;PIN&gt; y &lt;legend&gt;   (tarjetas de regalo)
 *   &lt;MENSAJE&gt;&lt;CODIGO&gt;01&lt;/CODIGO&gt;&lt;TEXTO&gt;...&lt;/TEXTO&gt;&lt;/MENSAJE&gt;
 * &lt;/RESPONSE&gt;
 * </pre>
 *
 * Sin un CODIGO la respuesta se considera ilegible: no hay forma de saber que
 * paso y la operacion se concilia.
 */
record RespuestaPuntoRed(String codigo, String texto, String idTransaccion, String autorizacion,
                         String pin, String leyenda) {

    private static final Pattern PIN_EN_XML =
            Pattern.compile("(?is)(<PIN\\b[^>]*>)(.*?)(</PIN\\s*>)");
    private static final Pattern REFERENCIA_EN_XML =
            Pattern.compile("(?is)(<(REFERENCIA|TELEFONO)\\b[^>]*>)(.*?)(</\\2\\s*>)");

    static Optional<RespuestaPuntoRed> leer(byte[] cuerpo) {
        return DocumentoXml.leer(cuerpo).flatMap(RespuestaPuntoRed::de);
    }

    private static Optional<RespuestaPuntoRed> de(DocumentoXml documento) {
        Optional<String> codigo = documento.texto("MENSAJE", "CODIGO").or(() -> documento.texto("CODIGO"));
        if (codigo.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new RespuestaPuntoRed(
                codigo.get(),
                documento.texto("MENSAJE", "TEXTO").orElse(null),
                documento.texto("ID_TX").orElse(null),
                documento.texto("NUM_AUTORIZACION").filter(RespuestaPuntoRed::autorizacionReal).orElse(null),
                documento.texto("PIN").orElse(null),
                documento.texto("legend").orElse(null)));
    }

    /** El proveedor manda -1 cuando no hubo autorizacion. */
    private static boolean autorizacionReal(String valor) {
        return !"-1".equals(valor);
    }

    /**
     * Copia del cuerpo apta para la bitacora: el PIN se oculta (es lo que el
     * cliente compro) y la referencia o telefono quedan con sus ultimos 4
     * caracteres (la completa ya vive en transaccion_pago).
     */
    static String paraBitacora(String xml) {
        if (xml == null) {
            return null;
        }
        String sinPin = PIN_EN_XML.matcher(xml).replaceAll("$1****$3");
        return REFERENCIA_EN_XML.matcher(sinPin).replaceAll(coincidencia -> Matcher.quoteReplacement(
                coincidencia.group(1) + Enmascarar.ultimos(coincidencia.group(3).strip()) + coincidencia.group(4)));
    }

    @Override
    public String toString() {
        return "RespuestaPuntoRed[codigo=" + codigo + ", texto=" + texto + ", idTransaccion=" + idTransaccion
                + ", autorizacion=" + autorizacion + ", pin=" + (pin == null ? "(sin PIN)" : "****") + "]";
    }
}
