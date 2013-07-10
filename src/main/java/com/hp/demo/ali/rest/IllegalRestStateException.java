package com.hp.demo.ali.rest;

/**
 * Created by panuska on 9.7.13.
 */
public class IllegalRestStateException extends IllegalStateException {
    private String errorStream;
    private int responseCode;

    public IllegalRestStateException(int responseCode, String errorStream, Throwable cause) {
        super(cause);
        this.responseCode = responseCode;
        this.errorStream = errorStream;
    }

    public String getErrorStream() {
        return errorStream;
    }

    public int getResponseCode() {
        return responseCode;
    }
}
