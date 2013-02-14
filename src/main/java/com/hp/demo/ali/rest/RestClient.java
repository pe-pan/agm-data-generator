package com.hp.demo.ali.rest;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;

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

    public synchronized HttpResponse doRequest(String urlAddress, String formData, Method method, ContentType contentType) {
        return doRequest(urlAddress, formData, method, contentType, null);
    }

    /**
     * Posts given data to the given address and collects (re-send) cookies.
     * Also handles redirects; only first time it does POST, then it does GET.
     *
     * @param urlAddress where the request is being sent
     * @param formData if null, GET method is used; POST otherwise
     * @param method which method will be used
     * @param handler
     * @return response of the request
     */
    public synchronized HttpResponse doRequest(String urlAddress, String formData, Method method, ContentType contentType, AsyncHandler handler) {
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
                String methodName = redirect ? "GET" : method.toString();
                log.debug("Doing "+methodName);
                conn.setRequestMethod(methodName);
                switch (contentType) {
                    case JSON : {
                        log.debug("JSON documents");
                        conn.setRequestProperty("Content-type", "application/json;type=collection");
                        conn.setRequestProperty("Accept", "application/json");
                        break;
                    }
                    case XML : {
                        log.debug("XML documents");
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
                if (urlAddress.endsWith("/qcbin/rest/api/portal/users")) { //todo an evil hack; set INTERNAL_DATA header when setting ALI DEV Bridge URL
                    String state = cookies.get("LWSSO_COOKIE_KEY");
                    cookies.put("AGM_STATE", state);
                    log.debug("Setting INTERNAL_DATA header: "+ state);
                    conn.setRequestProperty("INTERNAL_DATA", state);
                }
                log.debug("Setting Referer into: "+oldLocation);
                conn.setRequestProperty("Referer", oldLocation);

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

                return  new HttpResponse(response, conn.getResponseCode());
            } else {
                log.debug("Handling asynchronously, starting a new thread");
                Thread thread = new Thread(handler, handler.getClass().getSimpleName());
                handler.setConnection(conn);
                thread.start();
                return new HttpResponse(null, conn.getResponseCode());
            }
        } catch (IOException e) {
            log.debug("Exception caught", e);
            try {
                if (conn != null) {
                    log.debug("Error stream: "+ IOUtils.toString(conn.getErrorStream()));
                }
            } catch (IOException e1) {
                log.debug("Cannot convert error stream to string");
            }
            throw new IllegalStateException(e);
        } finally {
            if (conn != null && handler == null) {  // close the connection only if received the data synchronously
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

    public HttpResponse doPost(String url, String[][] data, AsyncHandler handler) {
        return doRequest(url, serializeParameters(data), Method.POST, ContentType.NONE, handler);
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

}
