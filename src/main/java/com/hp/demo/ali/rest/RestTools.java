package com.hp.demo.ali.rest;

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
}
