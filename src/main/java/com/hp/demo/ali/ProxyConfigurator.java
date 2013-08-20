package com.hp.demo.ali;

import org.apache.log4j.Logger;
import org.w3c.dom.Element;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Properties;

/**
 * Created by panuska on 19.8.13.
 */
public class ProxyConfigurator {
    private static Logger log = Logger.getLogger(ProxyConfigurator.class.getName());

    private Properties proxyProperties;

    public static final String HTTP_PROXY_HOST_KEY = "http.proxyHost";
    public static final String HTTPS_PROXY_HOST_KEY = "https.proxyHost";
    public static final String HTTP_PROXY_PORT_KEY = "http.proxyPort";
    public static final String HTTPS_PROXY_PORT_KEY = "https.proxyPort";
    public static final String NO_PROXY_HOSTS_KEY = "http.nonProxyHosts";

    public static final String PROXY_PROPERTIES_FILE_NAME = "proxy.properties";

    public void init(String url) {
        proxyProperties = getProxyFileConfiguration();
        if (proxyProperties == null) {
            System.setProperty("java.net.useSystemProxies", "true");
            proxyProperties = getSystemProxyConfiguration(url);
        } else {
            System.setProperty("java.net.useSystemProxies", "false");
            for (String key : proxyProperties.stringPropertyNames()) {
                log.debug("Setting system property "+key+"="+proxyProperties.getProperty(key));
                System.setProperty(key, proxyProperties.getProperty(key));
            }
        }
    }

    private Properties getProxyFileConfiguration() {
        File file = new File(PROXY_PROPERTIES_FILE_NAME);
        if (file.exists()) {
            Properties properties = new Properties();
            log.debug("Reading proxy properties from " + file.getAbsolutePath());
            try {
                properties.load(new FileInputStream(file));
            } catch (IOException e) {
                log.error("Cannot read proxy properties from "+file.getAbsolutePath());
                return null;
            }
            return properties;
        } else {
            log.debug("No proxy properties file found at " + file.getAbsolutePath());
            return null;
        }
    }

    private Properties getSystemProxyConfiguration(String url) {
        log.debug("Getting system proxy");
        String httpUrl;
        String httpsUrl;
        if (url.startsWith("https")) {
            httpsUrl = url;
            httpUrl = "http" + url.substring("https".length());
        } else {
            httpsUrl = "https" + url.substring("http".length());
            httpUrl = url;
        }
        Properties properties = new Properties();
        InetSocketAddress address = getSystemProxy(httpUrl);
        if (address != null) {
            properties.put(HTTP_PROXY_HOST_KEY, address.getHostString());
            properties.put(HTTP_PROXY_PORT_KEY, ""+address.getPort());
            log.debug("HTTP proxy: " + address.getHostString() + ":" + address.getPort());
        } else {
            log.debug("No HTTP proxy");
        }

        address = getSystemProxy(httpsUrl);
        if (address != null) {
            properties.put(HTTPS_PROXY_HOST_KEY, address.getHostString());
            properties.put(HTTPS_PROXY_PORT_KEY, ""+address.getPort());
            log.debug("HTTPS proxy: " + address.getHostString() + ":" + address.getPort());
        } else {
            log.debug("No HTTPS proxy");
        }
        return properties;
    }

    private InetSocketAddress getSystemProxy(String url) {
        List<Proxy> proxyList;
        try {
            proxyList = ProxySelector.getDefault().select(new URI(url));
        }
        catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
        if (proxyList != null && proxyList.size() > 0) {
            Proxy proxy = proxyList.get(0);
            if (proxyList.size() > 1) {
                log.warn("There is more " + proxy.type() + " proxies available. Use "+PROXY_PROPERTIES_FILE_NAME+" to set the right one.");
            }
            InetSocketAddress address = (InetSocketAddress) proxy.address();
            return address;
        }
        return null;
    }

    public CharSequence getDevBridgeProxyConfiguration() {
        if  (proxyProperties != null && proxyProperties.size() > 0) {
            String httpProxyPort = proxyProperties.getProperty(HTTP_PROXY_PORT_KEY);
            String httpProxyHost = proxyProperties.getProperty(HTTP_PROXY_HOST_KEY);
            String httpsProxyPort = proxyProperties.getProperty(HTTPS_PROXY_PORT_KEY);
            String httpsProxyHost = proxyProperties.getProperty(HTTPS_PROXY_HOST_KEY);
            String noProxyHosts = proxyProperties.getProperty(NO_PROXY_HOSTS_KEY);
            StringBuilder content = new StringBuilder(System.lineSeparator());
            if (httpProxyHost != null) {
                content.append("httpProxy=").append(httpProxyHost);
            }
            if (httpProxyPort != null) {
                content.append(":").append(httpProxyPort);
            }
            content.append(System.lineSeparator());
            if (httpsProxyHost != null) {
                content.append("httpsProxy=").append(httpsProxyHost);
            }
            if (httpsProxyPort != null) {
                content.append(":").append(httpsProxyPort);
            }
            content.append(System.lineSeparator());
            if (noProxyHosts != null) {
                content.append("noProxyHosts=").append(noProxyHosts).append(System.lineSeparator());
            }
            return content;
        }
        return "";
    }

    /**
     * Returns null when no proxy properties specified (even when an instance is provided)
     * @param element
     * @return
     */
    public Element getSvnAgentProxyConfiguration(Element element) {
        if (proxyProperties != null && proxyProperties.size() > 0) {
            String httpProxyPort = proxyProperties.getProperty(HTTP_PROXY_PORT_KEY);
            String httpProxyHost = proxyProperties.getProperty(HTTP_PROXY_HOST_KEY);
            if (httpProxyHost != null) {
                element.setAttribute("host", httpProxyHost);
                if (httpProxyPort != null) {
                    element.setAttribute("port", httpProxyPort);
                }
                return element;
            }
        }
        return null;
    }

    public String getHudsonProxyConfiguration() {
        if (proxyProperties != null && proxyProperties.size() > 0) {
            String httpProxyPort = proxyProperties.getProperty(HTTP_PROXY_PORT_KEY);
            String httpProxyHost = proxyProperties.getProperty(HTTP_PROXY_HOST_KEY);
            if (httpProxyHost != null) {
                StringBuilder content = new StringBuilder();
                content.append("<?xml version='1.0' encoding='UTF-8'?>").append(System.lineSeparator()).
                        append("<proxy>").append(System.lineSeparator()).
                        append("  <name>").append(httpProxyHost).append("/<name>").append(System.lineSeparator());
                if (httpProxyPort != null) {
                    content.append("  <port>").append(httpProxyPort).append("</port>").append(System.lineSeparator());
                }
                content.append("</proxy>").append(System.lineSeparator());
                return content.toString();
            }
        }
        return "";
    }
}
