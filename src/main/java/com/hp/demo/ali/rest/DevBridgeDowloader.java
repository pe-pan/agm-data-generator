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
public class DevBridgeDowloader implements AsyncHandler {

    private static Logger log = Logger.getLogger(DevBridgeDowloader.class.getName());
    private HttpURLConnection conn;

    private final Settings settings;
    private final RestClient client; // to synchronize closing the connection

    public DevBridgeDowloader(Settings settings, RestClient client) {
        this.settings = settings;
        this.client = client;
    }

    @Override
    public void setConnection(HttpURLConnection conn) {
        this.conn = conn;
    }

    @Override
    public void run() {
        String header = conn.getHeaderField("Content-Disposition");
        String fileName = header.substring(header.indexOf("; filename=")+"; filename=".length());
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
        log.debug("File "+fileName+" of "+size+" bytes downloaded, moving into: "+settings.getJettyWarFolder());
        File dest = new File(settings.getJettyWarFolder()+File.separator+fileName);
        try {
            dest.delete();
            FileUtils.moveToDirectory(new File(fileName), new File(settings.getJettyWarFolder()), false);
            log.info("File "+fileName+" successfully downloaded and moved into "+settings.getJettyWarFolder());
        } catch (IOException e) {
            log.error("File "+fileName+" cannot be moved: "+e.getMessage());
        }
    }
}
