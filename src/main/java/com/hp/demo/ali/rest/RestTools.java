package com.hp.demo.ali.rest;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * Created by panuska on 2/12/13.
 */
public class RestTools {

    public static String encodeUrl(String url) {
        if (url == null) return null;
        try {
            return URLEncoder.encode(url, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
