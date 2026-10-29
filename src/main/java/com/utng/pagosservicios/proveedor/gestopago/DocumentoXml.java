package com.utng.pagosservicios.proveedor.gestopago;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Lectura segura de las respuestas XML de PuntoRed.
 *
 * <p>Una respuesta del proveedor es contenido externo: el parser se configura
 * contra XXE (entidades externas que leerian archivos del servidor o harian
 * peticiones a la red interna) y contra la expansion de entidades ("billion
 * laughs"). Cualquier DOCTYPE se rechaza: una respuesta legitima no lo necesita.
 *
 * <p>Los nombres de elemento se comparan sin distinguir mayusculas: un cambio de
 * "legend" a "LEGEND" no debe volver ilegible una respuesta y dejar el dinero
 * en conciliacion sin necesidad.
 */
final class DocumentoXml {

    private static final ErrorHandler SIN_SALIDA = new ErrorHandler() {
        @Override
        public void warning(SAXParseException e) {
            // se ignoran las advertencias
        }

        @Override
        public void error(SAXParseException e) throws SAXException {
            throw e;
        }

        @Override
        public void fatalError(SAXParseException e) throws SAXException {
            throw e;
        }
    };

    private final Element raiz;

    private DocumentoXml(Element raiz) {
        this.raiz = raiz;
    }

    /** Vacio si el contenido no es XML bien formado. Nunca lanza excepciones. */
    static Optional<DocumentoXml> leer(byte[] contenido) {
        if (contenido == null || contenido.length == 0) {
            return Optional.empty();
        }
        try {
            DocumentBuilder constructor = fabricaSegura().newDocumentBuilder();
            // Sin esto, el parser imprime "[Fatal Error]" en la salida estandar.
            constructor.setErrorHandler(SIN_SALIDA);
            Document documento = constructor.parse(new InputSource(new ByteArrayInputStream(contenido)));
            Element raiz = documento.getDocumentElement();
            return raiz == null ? Optional.empty() : Optional.of(new DocumentoXml(raiz));
        } catch (ParserConfigurationException | SAXException | IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    static DocumentBuilderFactory fabricaSegura() throws ParserConfigurationException {
        DocumentBuilderFactory fabrica = DocumentBuilderFactory.newInstance();
        fabrica.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        fabrica.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        fabrica.setFeature("http://xml.org/sax/features/external-general-entities", false);
        fabrica.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        fabrica.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        fabrica.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        fabrica.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        fabrica.setXIncludeAware(false);
        fabrica.setExpandEntityReferences(false);
        fabrica.setNamespaceAware(false);
        return fabrica;
    }

    String nombreRaiz() {
        return raiz.getTagName();
    }

    /** Texto (sin espacios a los lados) del elemento al final de la ruta, si existe y no esta vacio. */
    Optional<String> texto(String... ruta) {
        return elemento(ruta).flatMap(DocumentoXml::textoDe);
    }

    /** Todos los elementos que coinciden con el ultimo tramo de la ruta. */
    List<Element> elementos(String... ruta) {
        if (ruta.length == 0) {
            return List.of(raiz);
        }
        Element actual = raiz;
        for (int i = 0; i < ruta.length - 1; i++) {
            Optional<Element> siguiente = hijo(actual, ruta[i]);
            if (siguiente.isEmpty()) {
                return List.of();
            }
            actual = siguiente.get();
        }
        return hijos(actual, ruta[ruta.length - 1]);
    }

    /**
     * Valor de un dato del elemento: como atributo o, si no existe, como
     * elemento hijo. El proveedor documenta atributos; se acepta ambas formas.
     */
    static Optional<String> dato(Element elemento, String nombre) {
        if (elemento.hasAttribute(nombre)) {
            return limpio(elemento.getAttribute(nombre));
        }
        var atributos = elemento.getAttributes();
        for (int i = 0; i < atributos.getLength(); i++) {
            Node atributo = atributos.item(i);
            if (atributo.getNodeName().equalsIgnoreCase(nombre)) {
                return limpio(atributo.getNodeValue());
            }
        }
        return hijo(elemento, nombre).flatMap(DocumentoXml::textoDe);
    }

    private Optional<Element> elemento(String... ruta) {
        Element actual = raiz;
        for (String tramo : ruta) {
            Optional<Element> siguiente = hijo(actual, tramo);
            if (siguiente.isEmpty()) {
                return Optional.empty();
            }
            actual = siguiente.get();
        }
        return Optional.of(actual);
    }

    private static Optional<Element> hijo(Element padre, String nombre) {
        List<Element> encontrados = hijos(padre, nombre);
        return encontrados.isEmpty() ? Optional.empty() : Optional.of(encontrados.get(0));
    }

    private static List<Element> hijos(Element padre, String nombre) {
        List<Element> encontrados = new ArrayList<>();
        NodeList nodos = padre.getChildNodes();
        for (int i = 0; i < nodos.getLength(); i++) {
            Node nodo = nodos.item(i);
            if (nodo instanceof Element hijo && hijo.getTagName().equalsIgnoreCase(nombre)) {
                encontrados.add(hijo);
            }
        }
        return encontrados;
    }

    private static Optional<String> textoDe(Element elemento) {
        return limpio(elemento.getTextContent());
    }

    private static Optional<String> limpio(String valor) {
        if (valor == null) {
            return Optional.empty();
        }
        String recortado = valor.strip();
        return recortado.isEmpty() ? Optional.empty() : Optional.of(recortado);
    }
}
