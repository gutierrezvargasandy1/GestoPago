package com.utng.pagosservicios.proveedor.gestopago;

/** Respuestas XML con el formato de PuntoRed, para simular al proveedor en las pruebas. */
public final class XmlPuntoRed {

    private XmlPuntoRed() {
    }

    /** sendTx o confirmTx aplicada (01). */
    public static String exito(String idTx, String autorizacion) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <RESPONSE>
                  <ID_TX>%s</ID_TX>
                  <NUM_AUTORIZACION>%s</NUM_AUTORIZACION>
                  <SALDO>9900.00</SALDO><SALDO_F>$9,900.00</SALDO_F>
                  <COMISION>0.00</COMISION><COMISION_F>$0.00</COMISION_F>
                  <FECHA>27/10/2026 12:00:00</FECHA>
                  <MONTO>100.00</MONTO>
                  <REFERENCIA>5512345678</REFERENCIA>
                  <MENSAJE><CODIGO>01</CODIGO><TEXTO>Operacion realizada con exito</TEXTO></MENSAJE>
                </RESPONSE>
                """.formatted(idTx, autorizacion);
    }

    /** Tarjeta de regalo: ademas del folio trae el PIN y la leyenda de canje. */
    public static String exitoConPin(String idTx, String pin, String leyenda) {
        return """
                <RESPONSE>
                  <ID_TX>%s</ID_TX><NUM_AUTORIZACION>PIN01</NUM_AUTORIZACION>
                  <PIN>%s</PIN><legend>%s</legend>
                  <MENSAJE><CODIGO>01</CODIGO><TEXTO>Operacion realizada con exito</TEXTO></MENSAJE>
                </RESPONSE>
                """.formatted(idTx, pin, leyenda);
    }

    /** Cualquier otro desenlace: 02 saldo insuficiente, 06 duplicada, 82 sin respuesta... */
    public static String codigo(String codigo, String texto) {
        return """
                <RESPONSE>
                  <ID_TX>0</ID_TX><NUM_AUTORIZACION>-1</NUM_AUTORIZACION>
                  <MENSAJE><CODIGO>%s</CODIGO><TEXTO>%s</TEXTO></MENSAJE>
                </RESPONSE>
                """.formatted(codigo, texto);
    }

    public static String catalogo(String... productos) {
        return "<RESPONSE><MENSAJE><CODIGO>01</CODIGO><TEXTO>OK</TEXTO></MENSAJE><PRODUCTOS>"
                + String.join("", productos) + "</PRODUCTOS></RESPONSE>";
    }

    public static String producto(int idServicio, String servicio, int idProducto, String producto,
                                  String precio, int tipoFront) {
        return ("<producto servicio=\"%s\" producto=\"%s\" idServicio=\"%d\" idProducto=\"%d\" "
                + "idCatTipoServicio=\"%d\" tipoFront=\"%d\" hasDigitoVerificador=\"false\" precio=\"%s\" "
                + "showAyuda=\"0\" tipoReferencia=\"a\"><legend>Instrucciones de %s</legend></producto>")
                .formatted(servicio, producto, idServicio, idProducto, tipoFront, tipoFront, precio, producto);
    }
}
