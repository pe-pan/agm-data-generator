package com.hp.demo.ali.rest;

import com.hp.demo.ali.Settings;
import com.hp.demo.ali.entity.User;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.log4j.Logger;
import org.hp.almjclient.connection.ServiceResourceAdapter;
import org.hp.almjclient.exceptions.ALMRestException;
import org.hp.almjclient.exceptions.RestClientException;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by panuska on 2/6/13.
 */
public class AgmClient {
    private static Logger log = Logger.getLogger(AgmClient.class.getName());

    private static AgmClient agmClient;
    public static AgmClient getAgmClient() {
        if (agmClient == null) {
            agmClient = new AgmClient();
        }
        return agmClient;
    }

    private PortalClient client;

    private AgmClient() {
        client = new PortalClient();
    }

    /**
     * @param loginUrl where to login.
     * @param admin user credentials.
     */
    public void login(String loginUrl, User admin) {
        client.login(loginUrl, admin);
        String tenantUrl = Settings.getSettings().getTenantUrl();
        if (tenantUrl == null) {
            String accountName = Settings.getSettings().getAccountName();
            if (accountName != null) {
                client.switchAccount(accountName);
            }
            String solutionName = Settings.getSettings().getSolutionName();
            tenantUrl = client.getTenantUrl(solutionName);
        }
        client.parseTenantProperties(tenantUrl);
  }

    public void prepareAddingUsers() {
        client.prepareAddingUsers();
    }

    public void addPortalUser(User user) {
        client.addUser(user);
    }

    public void addTenantUser(User user) {
        try {
             JSONObject userJson = new JSONObject();
             userJson.put("firstName", StringEscapeUtils.escapeHtml(user.getFirstName()));
             userJson.put("lastName", StringEscapeUtils.escapeHtml(user.getLastName()));
             userJson.put("email", StringEscapeUtils.escapeHtml(user.getLogin()));
             userJson.put("loginName", StringEscapeUtils.escapeHtml(user.getLogin()));
             userJson.put("phone", StringEscapeUtils.escapeHtml(user.getPhone()));
             userJson.put("timezone", client.getTimezone());
             JSONArray users = new JSONArray();
             users.add(userJson);
             JSONObject usersJson = new JSONObject();
             usersJson.put("users", users);
             ServiceResourceAdapter adapter = AgmRestService.getAdapter();
             Map<String, String> headers = new HashMap<>(1);
             headers.put("INTERNAL_DATA", "20120922");
             adapter.addSessionCookie("AGM_STATE="+"20120922");
             adapter.putWithHeaders(String.class, Settings.getSettings().getRestUrl()+"/rest/api/portal/users", usersJson.toString(), headers, ServiceResourceAdapter.ContentType.JSON);
             log.info("User added to the tenant");
         } catch (ALMRestException e) {
             log.error("Cannot add user to project: "+user.getFirstName()+" "+user.getLastName());
             String responseHtml = e.getResponse().getEntity(String.class);
             String reason = responseHtml.substring(responseHtml.indexOf("<h1>")+"<h1>".length(), responseHtml.indexOf("</h1>")); //todo parse the HTML better way
             log.error(reason);
         } catch (RestClientException e) {
             log.error("Cannot add user to project: "+user.getFirstName()+" "+user.getLastName());
         }
    }

    public FileDownloader downloadDevBridge() {
        RestClient devBridgeDownloaderClient = new RestClient();
        FileDownloader downloader = new FileDownloader(devBridgeDownloaderClient );
        Settings settings = Settings.getSettings();
        User admin = User.getUser(settings.getAdmin());          //todo this is a code copy of PortalClient.login -> refactor
        String[][] data = {
                { "username", admin.getLogin() },
                { "password", admin.getPassword() }
        };
        devBridgeDownloaderClient.doPost(settings.getLoginUrl(), data);
        data = new String[][] { {"server-url", settings.getRestUrl() } };
        devBridgeDownloaderClient.doGet(settings.getRestUrl()+"/rest/domains/" + settings.getDomain() + "/projects/" + settings.getProject() + "/scm/dev-bridge/bundle", data, downloader);  // /scm/dev-bridge - downloads only war file!
        return downloader;
    }
}
