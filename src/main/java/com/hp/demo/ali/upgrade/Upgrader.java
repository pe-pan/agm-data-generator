package com.hp.demo.ali.upgrade;

import com.hp.demo.ali.Settings;
import com.hp.demo.ali.rest.FileDownloader;
import com.hp.demo.ali.rest.IllegalRestStateException;
import com.hp.demo.ali.rest.RestClient;

import org.apache.log4j.Logger;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * Created by panuska on 21.2.14.
 */
public class Upgrader {
    private static Logger log = Logger.getLogger(Upgrader.class.getName());

    public static final String AGM_JAR_FILE = "agm-data-generator.jar";

    /**
     * Returns true if there is a newer version available and this version was downloaded successfully. False otherwise.
     */
    public static boolean upgrade() {
        String url = Settings.getSettings().getUpdateUrl();
        if (url == null || url.length() == 0) {
            log.debug("No URL to download an upgrade from.");
            return false;
        }
        if (!bypassSSLVerification(url)) {
            return false;
        }
        try {
            if (!url.endsWith("/")) url = url + "/";
            log.debug("Upgrade URL: " + url);
            RestClient client = new RestClient();

            Date timeLimit = getLastModified();
            FileDownloader agmJarFileDownloader = new FileDownloader(client, timeLimit, Upgrader.AGM_JAR_FILE);
            client.doGet(url + AGM_JAR_FILE, agmJarFileDownloader);
            if (!agmJarFileDownloader.waitTillDownloaded()) {
                log.error("Upgrade cannot be downloaded. Check logs for more information.");
                return false;
            }
            if (agmJarFileDownloader.getFile() == null) {
                log.info("No update available.");
                return false;
            }

            String thisBuildTime = getBuildTime();
            String downloadedBuildTime = getBuildTime(agmJarFileDownloader.getFile().getAbsolutePath());
            log.info("Build time of the local file is " + thisBuildTime + "; build time of the downloaded file is " + downloadedBuildTime);
            if (downloadedBuildTime.compareTo(thisBuildTime) <= 0) {
                log.info("Nothing to be upgraded!!");
                return false;
            }
            log.info("We can upgrade.");
            return true;
        } catch (IllegalRestStateException e) {
            log.error("Cannot upgrade" + (e.getErrorStream() != null ? "; Error: " + e.getErrorStream() : ""), e);
            return false;
        }
    }

    /**
     * Opens the manifest file of the given JAR file and returns the Build-Time attribute.
     * Returns null when an exception is thrown while reading the Build-Time attribute.
     */
    public static String getBuildTime(String jarFileName) {
        try {
            JarFile jarFile = new JarFile(jarFileName);
            Manifest manifest = jarFile.getManifest();
            Attributes attr = manifest.getMainAttributes();
            return attr.getValue("Build-Time");
        } catch (IOException e) {
            log.debug("Exception when reading build time from manifest file!", e);
            return null;
        }
    }

    /**
     * Opens the manifest file of the ADG JAR file and returns the Build-Time attribute.
     * Returns null when an exception is thrown while reading the Build-Time attribute.
     */
    public static String getBuildTime() {
        return getBuildTime(getThisJarFileName());
    }

    public static Date getLastModified() {
        return new Date(new File(getThisJarFileName()).lastModified());
    }

    private static String getThisJarFileName() {
        return Upgrader.class.getProtectionDomain().getCodeSource().getLocation().getFile();
    }

    private static boolean bypassSSLVerification(final String url) {
        // to avoid this exception: SSLProtocolException: handshake alert: unrecognized_name
        System.setProperty("jsse.enableSNIExtension", "false"); //FMI see http://stackoverflow.com/questions/7615645/ssl-handshake-alert-unrecognized-name-error-since-upgrade-to-java-1-7-0

        try {
            SSLContext ssl = SSLContext.getInstance("TLSv1");
            ssl.init(null, new TrustManager[]{new X509TrustManager() {
                public void checkClientTrusted(
                        X509Certificate[] cert, String s)
                        throws CertificateException {
                }

                public void checkServerTrusted(
                        X509Certificate[] cert, String s)
                        throws CertificateException {
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }
            }}, null);
            HttpsURLConnection.setDefaultSSLSocketFactory(ssl.getSocketFactory());
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new IllegalStateException(e);
        }

        try {
            final URL upgradeUrl = new URL(url);
            HostnameVerifier allHostsValid = new HostnameVerifier() {
                public boolean verify(String hostname, SSLSession session) {
                    return url != null && upgradeUrl.getHost().equals(hostname);
                }
            };
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
        } catch (MalformedURLException e) {
            log.error("Not an URL "+url, e);
            return false;
        }
        return true;
    }
}
