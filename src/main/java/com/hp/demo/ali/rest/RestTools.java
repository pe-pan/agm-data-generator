package com.hp.demo.ali.rest;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.tidy.Tidy;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Created by panuska on 2/12/13.
 */
public class RestTools {
    public static String getProtocolHost(String stringUrl) {
        URL url;
        try {
            url = new URL(stringUrl);
        } catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }
        return url.getProtocol()+"://"+url.getHost();
    }

    public static String extractString(String html, String xpathString) {
        //todo this method should not be here
        Tidy tidy = new Tidy();
        tidy.setShowErrors(0);        //todo redirect Tidy logging to log4j; (see http://ideas-and-code.blogspot.cz/2009/10/jtidy-errors-to-log4j.html)
        tidy.setShowWarnings(false);
        tidy.setQuiet(true);
        Document document = tidy.parseDOM(new StringReader(html), null);
        XPath xpath = XPathFactory.newInstance().newXPath();
        Node a = null;
        try {
            a = (Node) xpath.compile(xpathString).evaluate(document, XPathConstants.NODE);
        } catch (XPathExpressionException e) {
            throw new IllegalStateException("Cannot parse this xpath:"+xpathString, e);
        }
        try {
            return a.getNodeValue();
        } catch (NullPointerException e) {
            throw new IllegalStateException("Entity not found!\nEntity: "+xpathString+"\nDocument: "+html);
        }
    }
}
