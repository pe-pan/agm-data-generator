package com.hp.demo.ali.rest;

import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.tidy.Tidy;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

/**
 * Created by panuska on 10/26/12.
 */
public class RestClient {

    private static Logger log = Logger.getLogger(RestClient.class.getName());

    private HashMap<String, String> cookies = new HashMap<String, String>();

    private void addCookieList(List<String> cookieList) {
        if (cookieList == null) {
            return;
        }
        for (String cookie : cookieList) {
            String key = cookie.substring(0, cookie.indexOf('='));
            String value = cookie.substring(key.length()+1, cookie.indexOf(";", key.length()));
            cookies.put(key, value);
        }
        log.debug("Cookies: "+cookies.toString());
    }

    private String getCookieList() {
        if (cookies.size() == 0) {
            return "";
        }
        Set<String> keys = cookies.keySet();
        StringBuilder cookieList = new StringBuilder();
        for (String key : keys) {
            String value = cookies.get(key);
            cookieList.append(key).append('=').append(value).append(';');
        }
        return cookieList.substring(0, cookieList.length() - 1); // remove the last ';'
    }

    public static class HttpResponse {
        private final String response;
        private final int responseCode;

        public HttpResponse(String response, int responseCode) {
            this.response = response;
            this.responseCode = responseCode;
        }

        public String getResponse() {
            return response;
        }

        public int getResponseCode() {
            return responseCode;
        }
    }

    /**
     * Posts given data to the given address and collects (re-send) cookies.
     * Also handles redirects; only first time it does POST, then it does GET.
     *
     * @param urlAddress where the request is being sent
     * @param formData if null, GET method is used; POST otherwise
     * @param method which method will be used
     * @return response of the request
     */
    public HttpResponse doRequest(String urlAddress, String formData, Method method, ContentType contentType) {
        HttpURLConnection conn = null;
        try {
            boolean redirect = false;
            do {
                log.debug("At: "+urlAddress);
                URL url = new URL(urlAddress);
                conn = (HttpURLConnection) url.openConnection();
                conn.setDoOutput(formData != null);
                conn.setDoInput(true);
                conn.setAllowUserInteraction(false);
                conn.setInstanceFollowRedirects(false);
                conn.setRequestMethod(redirect ? "GET" : method.toString());
                switch (contentType) {
                    case JSON : {
                        conn.setRequestProperty("Content-type", "application/json;type=collection");
                        conn.setRequestProperty("Accept", "application/json");
                        break;
                    }
                    case XML : {
                        conn.setRequestProperty("Content-type", "application/xml; charset=UTF-8");
                        conn.setRequestProperty("Accept", "application/xml");
                        break;
                    }
                }
                if (urlAddress.endsWith("/scm/dev-bridge/deployment-url")) { //todo an evil hack; set INTERNAL_DATA header when setting ALI DEV Bridge URL
                    String state = cookies.get("LWSSO_COOKIE_KEY");
                    cookies.put("STATE", state);
                    log.debug("Setting INTERNAL_DATA header: "+ state);
                    conn.setRequestProperty("INTERNAL_DATA", state);
                }
                conn.setRequestProperty("Cookie", getCookieList());

                // write the data
                if (!redirect && formData != null) {
                    log.debug("Data size: " + formData.length());
                    conn.setRequestProperty("Content-Length", Integer.toString(formData.length()));
                    log.debug("Posting: " + formData);
                    DataOutputStream wr = new DataOutputStream(conn.getOutputStream());
                    wr.writeBytes(formData);
                    wr.flush();
                    wr.close();
                }
                log.debug("Code: "+conn.getResponseCode()+"; Message: "+conn.getResponseMessage());

                if (conn.getResponseCode() == 301 || conn.getResponseCode() == 302) {
                    urlAddress = conn.getHeaderField("Location");
                    log.debug("Redirect to: " + urlAddress);
                    redirect = true;
                    conn.disconnect();
                } else {
                    redirect = false;
                }
                addCookieList(conn.getHeaderFields().get("Set-Cookie"));
            } while (redirect);

            // Get the response

            log.debug("Receiving:");
            BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String line;
            StringBuilder response = new StringBuilder();
            while ((line = rd.readLine()) != null) {
                log.debug(line);
                response.append(line);
            }
            rd.close();

            return  new HttpResponse(response.toString(), conn.getResponseCode());
        } catch (IOException e) {
            log.debug("Exception caught", e);
            throw new IllegalStateException(e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private String serializeParameters(String [][]data) {
        StringBuilder returnValue = new StringBuilder();
        for (String[] parameter : data) {
            assert parameter.length == 2;
            String key = parameter[0];
            String value  = parameter[1];
            if (value == null) {
                value = "";
            }
            try {
                returnValue.
                        append('&').                    // even the very first parameter starts with '&'
                        append(key).append('=').
                        append(URLEncoder.encode(value, "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                throw new IllegalStateException(e);     // remove the starting '&' character
            }
        }
        return returnValue.substring(1);
    }

    public HttpResponse doRequest(String urlAddress, String[][] formData, Method method, ContentType contentType) {
        return doRequest(urlAddress, serializeParameters(formData), method, contentType);
    }

    public HttpResponse doGet(String url) {
        return doRequest(url, (String) null, Method.GET, ContentType.NONE);
    }

    public HttpResponse doPost(String url, String data) {
        return doRequest(url, data, Method.POST, ContentType.XML);
    }

    public HttpResponse doPost(String url, String[][] data) {
        return doRequest(url, serializeParameters(data), Method.POST, ContentType.NONE);
    }

    public HttpResponse doPut(String url, String data) {
        return doRequest(url, data, Method.PUT, ContentType.JSON);
    }

    public HttpResponse doPut(String url, String[][] data) {
        return doRequest(url, serializeParameters(data), Method.PUT, ContentType.JSON);
    }

    public HttpResponse doDelete(String url) {
        return doRequest(url, (String) null, Method.DELETE, ContentType.NONE);
    }

    public String extractString(String html, String xpathString) {
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
