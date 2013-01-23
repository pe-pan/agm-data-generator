package com.hp.demo.ali.rest;

import com.hp.demo.ali.entity.Entity;
import com.hp.demo.ali.entity.Field;
import org.apache.log4j.Logger;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.List;

/**
 * Created by panuska on 10/26/12.
 */
public class RestHelper {

    static String lwssoCookieKey;
    private static Logger log = Logger.getLogger(RestHelper.class.getName());

    public static void Login(String username, String password, String qcAddress) {
        try {

            qcAddress = qcAddress + "?j_username=" + username + "&j_password=" + password;
            log.debug("Login to: " + qcAddress);

            URL url = new URL(qcAddress);
            URLConnection conn = url.openConnection();
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setAllowUserInteraction(false);
//             conn.setRequestProperty("Authorization", "Basic "
//                     + Base64.encode((username + ":" + password).getBytes()));

            // Get the response
            BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String line;
            while ((line = rd.readLine()) != null) {
                log.debug(line);
            }
            rd.close();
            log.debug("Logged in");

            lwssoCookieKey = conn.getHeaderField("Set-Cookie");
            log.debug("Cookie value:" + lwssoCookieKey);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void LoginSaaS(String username, String password, String qcAddress) {
        HttpURLConnection conn = null;
        try {

//             qcAddress = qcAddress+"?j_username="+username+"&j_password="+password;
            URL url = new URL(qcAddress);
            conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setAllowUserInteraction(false);
            conn.setRequestMethod("POST");

            // write the credentials
            String urlParameters =
                    "username=" + URLEncoder.encode(username, "UTF-8") +
                    "&password=" + URLEncoder.encode(password, "UTF-8");
            DataOutputStream wr = new DataOutputStream(conn.getOutputStream());
            wr.writeBytes(urlParameters);
            wr.flush();
            wr.close();
            // Get the response

            BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String line;
            while ((line = rd.readLine()) != null) {
                log.debug(line);
            }
            rd.close();
            log.debug("Logged in");

            lwssoCookieKey = conn.getHeaderField("Set-Cookie");
            log.debug("Cookie value:" + lwssoCookieKey);
            return;

        } catch (IOException e) {
            throw new IllegalStateException(e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    public static Entity postEntity(Entity entity, String restAddress) {
        try {
            final JAXBContext context = JAXBContext.newInstance(Entity.class);
            final Marshaller marshaller = context.createMarshaller();

            final StringWriter stringWriter = new StringWriter();

            marshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);
            // Marshal the javaObject and write the XML to the stringWriter
            marshaller.marshal(entity, stringWriter);

            log.debug(stringWriter.toString());
            String xmlEntity = post(stringWriter.toString(), restAddress, entity);

            ByteArrayInputStream input = new ByteArrayInputStream(xmlEntity.getBytes());

            Unmarshaller u = context.createUnmarshaller();
            return (Entity) u.unmarshal(input);

        } catch (JAXBException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String moveEntity (Entity entity, String qcAddress) {
        HttpURLConnection conn = null;
        try {
            // write the parameters
            StringBuilder urlParameters = new StringBuilder(qcAddress).append('?');

            List<Field> fields = entity.getFields().getField();
            for(Field field : fields) {
                urlParameters.append(field.getName()).append('=').append(/*URLEncoder.encode(*/field.getValue().getValue()/*, "UTF-8")*/).append('&');
            }
            urlParameters.deleteCharAt(urlParameters.length()-1);
            log.debug("Posting: " + urlParameters.toString());

            URL url = new URL(urlParameters.toString());
            conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(false);
            conn.setDoInput(true);
            conn.setRequestMethod("POST");
            conn.setAllowUserInteraction(false);
            conn.setRequestProperty("Cookie", lwssoCookieKey.substring(0, lwssoCookieKey.length() - ";Path=/".length()) + "; TENANT_ID_COOKIE=0");
            conn.setRequestProperty("Content-type", "application/xml; charset=UTF-8");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:7.0.1) Gecko/20100101 Firefox/7.0.1");
            conn.setRequestProperty("Accept", "application/json");

//            DataOutputStream wr = new DataOutputStream(conn.getOutputStream());
//            wr.writeBytes(urlParameters.toString());
//            wr.flush();
//            wr.close();
            // Get the response

            BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String line;
            StringBuilder returnValue = new StringBuilder();
            while ((line = rd.readLine()) != null) {
                log.debug(line);
                returnValue.append(line);
            }
            rd.close();
            int startIndex = "[\"release-backlog-item_".length();
            int lastIndex = returnValue.indexOf("\"", startIndex);

            String xmlEntity = returnValue.substring(startIndex, lastIndex);
            log.debug("Receive:");
            log.debug(xmlEntity);
            return xmlEntity;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    public static String post(String xmlToPost, String restAddress, Entity entity) {
        try {

            // Send data
            URL url = new URL(restAddress);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            if ("release-backlog-item".equals(entity.getType())) {
                //todo an evil hack
                log.debug("Putting at: " + restAddress);
                conn.setRequestMethod("PUT");
            } else {
                log.debug("Posting at: " + restAddress);
                conn.setRequestMethod("POST");
            }
            conn.setRequestProperty("Cookie", lwssoCookieKey.substring(0, lwssoCookieKey.length() - ";Path=/".length()) + "; TENANT_ID_COOKIE=0");
            conn.setRequestProperty("Content-type", "application/xml; charset=UTF-8");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:7.0.1) Gecko/20100101 Firefox/7.0.1");
            conn.setRequestProperty("Accept", "application/xml");
            OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
            wr.write(xmlToPost);
            wr.flush();
            wr.close();
            // Get the response
            BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String line;
            StringBuilder xmlEntity = new StringBuilder();
            while ((line = rd.readLine()) != null) {
                xmlEntity.append(line);
            }
            rd.close();
            conn.disconnect();
            log.debug("Receive:");
            log.debug(xmlEntity);
            return xmlEntity.substring("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>".length());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

}
