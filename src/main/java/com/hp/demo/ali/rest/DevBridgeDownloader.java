package com.hp.demo.ali.rest;

import com.hp.demo.ali.Settings;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;

import java.io.*;
import java.net.HttpURLConnection;

/**
 * Created by panuska on 2/13/13.
 */
public class DevBridgeDownloader implements AsyncHandler {

    private static Logger log = Logger.getLogger(DevBridgeDownloader.class.getName());
    private HttpURLConnection conn;

    private final Settings settings;
    private final RestClient client; // to synchronize closing the connection
    private boolean downloaded = false;
    private String fileName = null;

    public DevBridgeDownloader(Settings settings, RestClient client) {
        this.settings = settings;
        this.client = client;
    }

    @Override
    public void setConnection(HttpURLConnection conn) {
        this.conn = conn;
    }

    @Override
    public void run() {
        try {
            String header = conn.getHeaderField("Content-Disposition");
            fileName = header.substring(header.indexOf("; filename=")+"; filename=".length());
            log.info("Downloading "+fileName+"...");
            int size;
            FileOutputStream outFile = null;
            try {
                outFile = new FileOutputStream(fileName);
                size = IOUtils.copy(conn.getInputStream(), outFile);
                outFile.flush();
                outFile.close();
            } catch (IOException e) {
                log.error("File cannot be downloaded", e);
                return;
            } finally {
                synchronized (client) {
                    conn.disconnect();
                }
            }
            log.info("File "+fileName+" of "+size+" bytes successfully downloaded");
        } finally {
            synchronized (this) {
                downloaded = true;
                notify();
            }
        }
    }

    public boolean waitTillDownloaded() {
        synchronized (this) {
            while (!downloaded) {
                try {
                    log.info("Waiting to download Dev Bridge...");
                    wait();
                } catch (InterruptedException e) {
                    log.debug("Interrupted when waiting for download", e);
                    return downloaded;
                }
            }
        }
        return downloaded;
    }

    public String getFileName() {
        return fileName;
    }
}
