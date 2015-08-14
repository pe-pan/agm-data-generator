package com.hp.demo.ali.tools;

import org.apache.commons.io.IOUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;

/**
 * Created by panuska on 5/14/13.
 */
public class XmlFile {

    private Document document;
    private static DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    private static XPathFactory xpf = XPathFactory.newInstance();
    private static XPath xpath = xpf.newXPath();
    private static TransformerFactory tf = TransformerFactory.newInstance();

    public XmlFile(File file) {
        try {
            document = dbf.newDocumentBuilder().parse(file);
        } catch (SAXException | IOException | ParserConfigurationException e) {
            throw new IllegalStateException(e);
        }
    }

    public XmlFile(String string) {
        try {
            document = dbf.newDocumentBuilder().parse(IOUtils.toInputStream(string));
        } catch (SAXException | IOException | ParserConfigurationException e) {
            throw new IllegalStateException(e);
        }
    }

    public Element createElement(String nodeTag) {
        return document.createElement(nodeTag);
    }

    public Node setNode(String xpathString, String nodeTag, Element child) {
        try {
            XPathExpression expression = xpath.compile(xpathString+"/"+nodeTag);
            Node node = (Node) expression.evaluate(document, XPathConstants.NODE);
            if (node != null) {
                node.getParentNode().removeChild(node);
            }
            if (child != null) {
                expression = xpath.compile(xpathString);
                Node parent = (Node) expression.evaluate(document, XPathConstants.NODE);
                parent.appendChild(child);
            }
            return node;
        } catch (XPathExpressionException e) {
            throw new IllegalStateException("Exception when compiling xpath " + xpathString + " in document " + document.getDocumentURI(), e);
        }
    }

    public String setNodeValue(String xpathString, String value) {
        try {
            XPathExpression expression = xpath.compile(xpathString);
            Node node = (Node) expression.evaluate(document, XPathConstants.NODE);
            if (node == null) {
                throw new IllegalStateException("In XML file "+document.getDocumentURI()+" cannot be found node on the path "+xpathString);
            }
            String oldValue = node.getTextContent();
            node.setTextContent(value);
            return oldValue;
        } catch (XPathExpressionException e) {
            throw new IllegalStateException("Exception when compiling xpath " + xpathString + " in document " + document.getDocumentURI(), e);
        }
    }

    public String setNodeValue(String xpathString, long value) {
        return setNodeValue(xpathString, Long.toString(value));
    }

    public Document getDocument() {
        return document;
    }

    public void save(File file) {
        try {
            Transformer t = tf.newTransformer();
            t.transform(new DOMSource(document), new StreamResult(file));
        } catch (TransformerException e) {
            throw new IllegalStateException(e);
        }
    }

    public String saveToString() {
        try {
            Transformer t = tf.newTransformer();
            StringWriter sw = new StringWriter();
            t.transform(new DOMSource(document), new StreamResult(sw));
            return sw.toString();
        } catch (TransformerException e) {
            throw new IllegalStateException(e);
        }
    }
}
