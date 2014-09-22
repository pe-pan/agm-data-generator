package com.hp.demo.ali.rest;

import com.hp.demo.ali.excel.ExcelEntity;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;

import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

/**
 * Created by panuska on 10/26/12.
 */
public class RestClient {

    private static Logger log = Logger.getLogger(RestClient.class.getName());

    private HashMap<String, String> cookies = new HashMap<>();
    private String oldLocation = "";

    private void addCookieList(List<String> cookieList) {
        if (cookieList == null) {
            return;
        }
        log.debug("Adding cookies:");
        for (String cookie : cookieList) {
            log.debug(cookie);
            String key = cookie.substring(0, cookie.indexOf('='));
            String value = cookie.substring(key.length()+1, cookie.indexOf(";", key.length()));
            cookies.put(key, value);
        }
        log.debug("New cookies: "+cookies.toString());
    }

    public void addCookieValue(String cookie, String value) {
        cookies.put(cookie, value);
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

    public String getCookie(String cookieName) {
        return cookies.get(cookieName);
    }

    public static class HttpResponse {
        private final String response;
        private final int responseCode;
        private final String xFid;
        private final String location;

        public HttpResponse(String response, int responseCode, String xFid, String location) {
            this.response = response;
            this.responseCode = responseCode;
            this.xFid = xFid;
            this.location = location;
        }

        public String getResponse() {
            return response;
        }

        public int getResponseCode() {
            return responseCode;
        }

        public String getXFid() {         //todo resolve this hack
            return xFid;
        }

        public String getLocation() {     //todo resolve this hack
            return location;
        }
    }

    public synchronized HttpResponse doRequest(String urlAddress, String formData, Method method, ContentType contentType) {
        return doRequest(urlAddress, formData, method, contentType, null);
    }

    public synchronized HttpResponse doRequest(String urlAddress, ExcelEntity entity, Method method, ContentType contentType) {
        return doRequest(urlAddress, entity.toUrlParameters(), method, contentType, null);
    }

    /**
     * Posts given data to the given address and collects (re-send) cookies.
     * Also handles redirects; only first time it does POST, then it does GET.
     *
     * @param urlAddress where the request is being sent
     * @param formData if null, GET method is used; POST otherwise
     * @param method which method will be used
     * @param handler if null, the method is synchronous and waits for response. If not null, the method is asynchronous
     *                and returns immediately. The response should can be processed in this handler.
     * @return response of the request
     */
    public synchronized HttpResponse doRequest(String urlAddress, String formData, Method method, ContentType contentType, AsyncHandler handler) {
        HttpURLConnection conn = null;
        try {
            boolean redirect = false;
            do {
                if (method == Method.GET && formData != null) {
                    urlAddress = urlAddress + "?"+formData;
                    formData = null;
                }
                log.debug("At: "+urlAddress);
                URL url = new URL(urlAddress);
                conn = (HttpURLConnection) url.openConnection();
                conn.setDoOutput(formData != null);
                conn.setDoInput(true);
                conn.setAllowUserInteraction(false);
                conn.setInstanceFollowRedirects(false);
                conn.setReadTimeout(60*1000);
                conn.setConnectTimeout(60*1000);
                String methodName = redirect ? "GET" : method.toString();
                log.debug("Doing "+methodName);
                conn.setRequestMethod(methodName);
                switch (contentType) {
                    case JSON_JSON: {
                        log.debug("JSON_JSON documents");
                        conn.setRequestProperty("Content-type", "application/json;type=collection");
                        conn.setRequestProperty("Accept", "application/json");
                        break;
                    }
                    case FORM_JSON: {      //todo because of generating requirements using apmuiservices
                        log.debug("FORM_JSON documents");
                        conn.setRequestProperty("Content-type", "application/x-www-form-urlencoded");
                        conn.setRequestProperty("Accept", "application/json");
                        break;
                    }
                    case XML_XML: {
                        log.debug("XML_XML documents");
                        conn.setRequestProperty("Content-type", "application/xml; charset=UTF-8");
                        conn.setRequestProperty("Accept", "application/xml");
                        break;
                    }
                }
                if (headerName != null) {
                    conn.setRequestProperty(headerName, headerValue);
                    log.debug("Setting "+headerName+": "+headerValue);
                    headerName = null;
                }
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.22 (KHTML, like Gecko) Chrome/25.0.1364.97 Safari/537.22");
                log.debug("Setting Referer into: "+oldLocation);
                conn.setRequestProperty("Referer", oldLocation);      // todo an evil hack; this is because of downloading DevBridge... so they know the URL where DevBridge will be pointing at

                String cookieList = getCookieList();
                log.debug("Sending cookies: "+cookieList);
                conn.setRequestProperty("Cookie", cookieList);

                // write the data
                if (!redirect && formData != null) {
                    log.debug("Data size: " + formData.length());
                    conn.setRequestProperty("Content-Length", Integer.toString(formData.length()));
                    log.debug("Posting: " + formData);
                    IOUtils.write(formData, conn.getOutputStream());
                    conn.getOutputStream().flush();
                    conn.getOutputStream().close();
                }
                log.debug("Code: "+conn.getResponseCode()+"; Message: "+conn.getResponseMessage());

                String location = conn.getHeaderField("Location");
                if (location != null) {
                    log.debug("Location: "+location);
                    oldLocation = location;
                }

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

            if (handler == null) {
                log.debug("Receiving:");
                String response = IOUtils.toString(conn.getInputStream());
                conn.getInputStream().close();
                log.debug(response);

                String xFid = conn.getHeaderField("X-FID");                       // todo an evil hack
                return  new HttpResponse(response, conn.getResponseCode(), xFid, oldLocation);
            } else {
                log.debug("Handling asynchronously, starting a new thread");
                Thread thread = new Thread(handler, handler.getClass().getSimpleName());
                handler.setConnection(conn);
                thread.start();
                return new HttpResponse(null, conn.getResponseCode(), null, null);
            }
        } catch (IOException e) {
            log.debug("Exception caught", e);
            String errorStream = null;
            int responseCode = 0;
            try {
                if (conn != null && conn.getErrorStream() != null) {
                    responseCode = conn.getResponseCode();
                    log.debug("Response Code: "+ responseCode);
                    errorStream = IOUtils.toString(conn.getErrorStream());
                    log.debug("Error stream: "+ errorStream);
                }
            } catch (IOException e1) {
                log.debug("Cannot convert error stream to string");
            }
            throw new IllegalRestStateException(responseCode, errorStream, e);
        } finally {
            if (conn != null && handler == null) {  // close the connection only if received the data synchronously
                conn.disconnect();
            }
        }
    }

    private String headerName = null;
    private String headerValue = null;
    public void setCustomHeader(String headerName, String headerValue) {
        this.headerName = headerName;
        this.headerValue = headerValue;
    }

    private String serializeParameters(String [][]data) {
        if (data == null) return null;
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
                throw new IllegalStateException(e);
            }
        }
        return returnValue.substring(1);                // remove the starting '&' character
    }

    public HttpResponse doRequest(String urlAddress, String[][] formData, Method method, ContentType contentType) {
        return doRequest(urlAddress, serializeParameters(formData), method, contentType);
    }

    public HttpResponse doGet(String url) {
        return doRequest(url, (String) null, Method.GET, ContentType.NONE);
    }

    public HttpResponse doGet(String url, AsyncHandler handler) {
        return doRequest(url, null, Method.GET, ContentType.NONE, handler);
    }

    public HttpResponse doGet(String url, String[][] data, AsyncHandler handler) {
        return doRequest(url, serializeParameters(data), Method.GET, ContentType.NONE, handler);
    }

    public HttpResponse doPost(String url, String data) {
        return doRequest(url, data, Method.POST, ContentType.XML_XML);
    }

    public HttpResponse doPost(String url, String[][] data) {
        return doRequest(url, serializeParameters(data), Method.POST, ContentType.NONE);
    }

    public HttpResponse doPost(String url, String[][] data, AsyncHandler handler) {
        return doRequest(url, serializeParameters(data), Method.POST, ContentType.NONE, handler);
    }

    public HttpResponse doPost(String url, String data, ContentType type) {
        return doRequest(url, data, Method.POST, type);
    }

    public HttpResponse doPut(String url, String data, ContentType type) {
        return doRequest(url, data, Method.PUT, type);
    }

    public HttpResponse doPut(String url, String data) {
        return doRequest(url, data, Method.PUT, ContentType.JSON_JSON);
    }

    public HttpResponse doPut(String url, String[][] data) {
        return doRequest(url, serializeParameters(data), Method.PUT, ContentType.JSON_JSON);
    }

    public HttpResponse doDelete(String url) {
        return doRequest(url, (String) null, Method.DELETE, ContentType.NONE);
    }

}
