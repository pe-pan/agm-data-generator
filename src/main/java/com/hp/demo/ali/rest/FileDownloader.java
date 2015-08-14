package com.hp.demo.ali.rest;

import com.hp.demo.ali.Migrator;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;

import java.io.*;
import java.net.HttpURLConnection;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by panuska on 2/13/13.
 */
public class FileDownloader implements AsyncHandler {

    private static Logger log = Logger.getLogger(FileDownloader.class.getName());
    private HttpURLConnection conn;

    private final RestClient client; // to synchronize closing the connection
    private boolean downloaded = false;
    private File file = null;
    private final Date timeLimit;
    private final String assumedFileName;

    public static final SimpleDateFormat buildTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    public FileDownloader(RestClient client, String assumedFileName) {
        this.client = client;
        this.timeLimit = null;
        this.assumedFileName = assumedFileName;
    }

    public FileDownloader(RestClient client, Date timeLimit, String assumedFileName) {
        this.client = client;
        this.timeLimit = timeLimit;
        this.assumedFileName = assumedFileName;
    }

    @Override
    public void setConnection(HttpURLConnection conn) {
        this.conn = conn;
    }

    @Override
    public void run() {
        try {
            Date lastModified = conn.getLastModified() == 0 ? null : new Date(conn.getLastModified());
            if (timeLimit != null) {
                if (lastModified != null) {
                    log.debug("Time limit: "+timeLimit.getTime()+"; lastModified: "+lastModified.getTime());
                    if (lastModified.compareTo(timeLimit) <= 0) {
                        log.debug("The remote file is not newer than needed; we need newer than "+buildTimeFormat.format(timeLimit)+" but the remote file is from "+buildTimeFormat.format(lastModified));
                        return;
                    }
                }
                log.info("There might be a newer file available...");
            }
            String header = conn.getHeaderField("Content-Disposition");
            if (header != null) {
                file = new File(Migrator.TMP_DIR, header.substring(header.indexOf("; filename=")+"; filename=".length()));
            } else {
                file = new File(Migrator.TMP_DIR, FilenameUtils.getName(conn.getURL().toString()));
            }
            log.info("Downloading "+ file +"...");
            int size;
            try {
                FileOutputStream outFile = new FileOutputStream(file);
                size = IOUtils.copy(conn.getInputStream(), outFile);
                outFile.flush();
                outFile.close();
                if (lastModified != null) {
                    if (!file.setLastModified(lastModified.getTime())) {
                        log.debug("Not possible to set the last modified date of file "+file.getAbsolutePath());
                    }
                }
            } catch (IOException e) {
                log.error("File "+assumedFileName+" cannot be downloaded", e);
                return;
            } finally {
                synchronized (client) {
                    conn.disconnect();
                }
            }
            log.info("File "+ file +" of "+size+" bytes successfully downloaded");
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
                    log.info("Waiting to finish downloading "+assumedFileName+"...");
                    wait();
                } catch (InterruptedException e) {
                    log.debug("Interrupted when waiting for download "+assumedFileName, e);
                    return downloaded;
                }
            }
        }
        return downloaded;
    }

    public File getFile() {
        return file;
    }
}
