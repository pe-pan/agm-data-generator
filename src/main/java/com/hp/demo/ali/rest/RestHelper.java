package com.hp.demo.ali.rest;

import com.hp.demo.ali.entity.Entity;
import com.hp.demo.ali.entity.Field;
import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.tidy.Tidy;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

/**
 * Created by panuska on 10/26/12.
 */
public class RestHelper {

    private static Logger log = Logger.getLogger(RestHelper.class.getName());

    public static void Login(String username, String password, String qcAddress) {
        try {

            qcAddress = qcAddress + "?j_username=" + username + "&j_password=" + password;
            log.debug("Login to: " + qcAddress);

            URL url = new URL(qcAddress);
            URLConnection conn = url.openConnection();
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setAllowUserInteraction(false);

            // Get the response
            BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String line;
            while ((line = rd.readLine()) != null) {
                log.debug(line);
            }
            rd.close();
            log.debug("Logged in");

            addCookieList(url.getHost(), conn.getHeaderFields().get("Set-Cookie"));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void LoginSaaS(String username, String password, String qcAddress) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(qcAddress);
            conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setAllowUserInteraction(false);
            conn.setRequestMethod("POST");

            // write the credentials
            String urlParameters =
                    "username=" + URLEncoder.encode(username, "UTF-8") +
                    "&password=" + URLEncoder.encode(password, "UTF-8");
            DataOutputStream wr = new DataOutputStream(conn.getOutputStream());
            wr.writeBytes(urlParameters);
            wr.flush();
            wr.close();
            log.debug("Code: "+conn.getResponseCode()+"; Message: "+conn.getResponseMessage());
            // Get the response

            BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String line;
            while ((line = rd.readLine()) != null) {
                log.debug(line);
            }
            rd.close();
            log.debug("Logged in");

            List<String> cookieList = conn.getHeaderFields().get("Set-Cookie");
            addCookieList(url.getHost(), cookieList);

        } catch (IOException e) {
            throw new IllegalStateException(e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    public static Entity postEntity(Entity entity, String restAddress) {
        try {
            final JAXBContext context = JAXBContext.newInstance(Entity.class);
            final Marshaller marshaller = context.createMarshaller();

            final StringWriter stringWriter = new StringWriter();

            marshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);
            // Marshal the javaObject and write the XML to the stringWriter
            marshaller.marshal(entity, stringWriter);

            log.debug(stringWriter.toString());
            String xmlEntity = post(stringWriter.toString(), restAddress, entity);

            ByteArrayInputStream input = new ByteArrayInputStream(xmlEntity.getBytes());

            Unmarshaller u = context.createUnmarshaller();
            return (Entity) u.unmarshal(input);

        } catch (JAXBException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String moveEntity(Entity entity, String qcAddress) {
        HttpURLConnection conn = null;
        try {
            // write the parameters
            StringBuilder urlParameters = new StringBuilder(qcAddress).append('?');

            List<Field> fields = entity.getFields().getField();
            for (Field field : fields) {
                urlParameters.append(field.getName()).append('=').append(/*URLEncoder.encode(*/field.getValue().getValue()/*, "UTF-8")*/).append('&');
            }
            urlParameters.deleteCharAt(urlParameters.length() - 1);
            log.debug("Posting: " + urlParameters.toString());

            URL url = new URL(urlParameters.toString());
            conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(false);
            conn.setDoInput(true);
            conn.setRequestMethod("POST");
            conn.setAllowUserInteraction(false);
            conn.setRequestProperty("Content-type", "application/xml; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");

            conn.setRequestProperty("Cookie", getCookieList(url.getHost()));
            log.debug("Code: "+conn.getResponseCode()+"; Message: "+conn.getResponseMessage());
            // Get the response

            BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String line;
            StringBuilder returnValue = new StringBuilder();
            while ((line = rd.readLine()) != null) {
                log.debug(line);
                returnValue.append(line);
            }
            rd.close();
            int startIndex = returnValue.indexOf("release-backlog-item_")+"release-backlog-item_".length();
            int lastIndex = returnValue.indexOf("\"", startIndex);

            String xmlEntity = returnValue.substring(startIndex, lastIndex);
            log.debug("Receive:");
            log.debug(xmlEntity);
            return xmlEntity;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static HashMap<String, String> cookies = new HashMap<String, String>();

    //todo every host should have its own set of cookies
    private static void addCookieList(String host, List<String> cookieList) {
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

    private static String getCookieList(String url) {
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

    public static String post(String xmlToPost, String restAddress, Entity entity) {
        try {

            // Send data
            URL url = new URL(restAddress);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            if (restAddress.contains("release-backlog-item")) {
                //todo an evil hack
                log.debug("Putting at: " + restAddress);
                conn.setRequestMethod("PUT");
            } else {
                log.debug("Posting at: " + restAddress);
                conn.setRequestMethod("POST");
            }
            conn.setRequestProperty("Cookie", getCookieList(url.getHost()));
            conn.setRequestProperty("Content-type", "application/xml; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/xml");
            OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
            wr.write(xmlToPost);
            wr.flush();
            wr.close();
            log.debug("Code: "+conn.getResponseCode()+"; Message: "+conn.getResponseMessage());
            // Get the response
            BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String line;
            StringBuilder xmlEntity = new StringBuilder();
            while ((line = rd.readLine()) != null) {
                xmlEntity.append(line);
            }
            rd.close();
            conn.disconnect();
            log.debug("Receive:");
            log.debug(xmlEntity);
            return xmlEntity.substring("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>".length());
        } catch (Exception e) {
            throw new IllegalStateException("When posting: \n"+xmlToPost+"\n at address: "+restAddress, e);
        }
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

    public static HttpResponse postData(String urlAddress, HashMap<String, String> formData) {
        Set<String> keys = formData.keySet();
        StringBuilder urlParameters = new StringBuilder();
        for (String key : keys) {
            String value = formData.get(key);
            if (value == null) {
                value = "";
            }
            try {
                urlParameters.
                        append('&').                    // even the very first parameter starts with '&'
                        append(key).append('=').
                        append(URLEncoder.encode(value, "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                throw new IllegalStateException(e);
            }
        }
        return postData(urlAddress, urlParameters.substring(1), false);
    }

    /**
     * Posts given data to the given address and sets the given cookie.
     * Also handles redirects; only first time it does POST, then it does GET.
     *
     * @param urlAddress
     * @param formData if null, GET method is used; POST otherwise
     * @return
     */
    public static HttpResponse postData(String urlAddress, String formData, boolean isPut) {
        //todo refactor this class to remove code duplicates
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
                conn.setRequestMethod(redirect | formData == null ? "GET" : isPut ? "PUT" : "POST");
                if (isPut) {
                    conn.setRequestProperty("Content-type", "application/json;type=collection");
                    conn.setRequestProperty("Accept", "application/json");
                }
                if (urlAddress.endsWith("/scm/dev-bridge/deployment-url")) { //todo an evil hack; set INTERNAL_DATA header when setting ALI DEV Bridge URL
                    String state = cookies.get("LWSSO_COOKIE_KEY");
                    cookies.put("STATE", state);
                    log.debug("Setting INTERNAL_DATA header: "+ state);
                    conn.setRequestProperty("INTERNAL_DATA", state);
                }
                conn.setRequestProperty("Cookie", getCookieList(url.getHost()));

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
                addCookieList(url.getHost(), conn.getHeaderFields().get("Set-Cookie"));
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

    public static String extractString(String html, String xpathString) {
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
