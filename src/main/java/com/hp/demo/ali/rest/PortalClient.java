package com.hp.demo.ali.rest;

import com.hp.demo.ali.Settings;
import com.hp.demo.ali.entity.User;
import com.jayway.jsonpath.JsonPath;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.log4j.Logger;

import java.net.HttpURLConnection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by panuska on 19.3.14.
 */
public class PortalClient  {
    private static Logger log = Logger.getLogger(PortalClient.class.getName());

    private RestClient client;

    private String portalUrl;
    private String timezone = "Europe/Prague";  //todo if account name not provided on cmd-line, the added users will be in this zone by default

    private static final String PRODUCT_NAME = "Agile Manager";

    public PortalClient() {
        client = new RestClient();
    }

    public void authenticate(String portalUrl, User admin) {
        final String data = "{\"loginName\":\""+admin.getLogin()+"\",\"password\":\""+admin.getPassword()+"\"}";
        RestClient.HttpResponse response = client.doPost(portalUrl + "/openservice/v1/lwsso/getToken", data, ContentType.JSON_JSON);
        String ssoToken = response.getResponse();
        client.addCookieValue("LWSSO_COOKIE_KEY", ssoToken);
    }

    public String getTenantUrl(String portalUrl, User admin) {
        RestClient.HttpResponse response = client.doGet(portalUrl+"/service/v2/obtaining/getSolutionInstances?loginName="+admin.getLogin());
        String accountName = Settings.getSettings().getAccountName();
        String accountFilter = accountName == null ? "[*]" : "[?(@.displayName == '"+accountName+"')]";

        String solutionName= Settings.getSettings().getSolutionName();
        String solutionFilter = solutionName == null ? "[*]" : "[?(@.instanceName == '"+solutionName+"')]";

        String filter = "$.accounts"+accountFilter+".solutions[?(@.solutionName == '"+PRODUCT_NAME+"')]"+".instances"+solutionFilter+".loginUrl";
        List<String> loginUrls = JsonPath.read(response.getResponse(), filter);

        if (loginUrls.size() == 0) {
            if (accountName == null && solutionName == null) { // no these arguments given
                throw new IllegalArgumentException("No "+PRODUCT_NAME+" solutions found under any of your accounts!");
            } else {
                List<String> accountNames = JsonPath.read(response.getResponse(), "$.accounts[*].displayName");
                List<String> solutionNames = JsonPath.read(response.getResponse(), "$.accounts[*].solutions[?(@.solutionName == '"+PRODUCT_NAME+"')]"+".instances.instanceName");
                throw new IllegalArgumentException(
                        "Under the given account ("+(accountName == null ? "not given" : accountName)+") "+
                        "and solution ("+(solutionName == null ? "not given" : solutionName)+"), "+
                        "there are no "+PRODUCT_NAME+" solutions found.\n" +
                        "Possible account names are: "+accountNames+"\n"+
                        "Possible solution names are: "+solutionNames);
            }
        } else if (loginUrls.size() > 1) {
            if (accountName == null || solutionName == null) {
                List<String> accountNames = JsonPath.read(response.getResponse(), "$.accounts[*].displayName");
                List<String> solutionNames = JsonPath.read(response.getResponse(), "$.accounts[*].solutions[?(@.solutionName == '"+PRODUCT_NAME+"')]"+".instances.instanceName");
                throw new IllegalArgumentException("Too many "+PRODUCT_NAME+" solutions found ("+loginUrls.size()+").\n"+
                        "Try to limit the results by providing --accountName and/or --solutionName parameters.\n"+
                        "Possible account names are: "+accountNames+"\n"+
                        "Possible solution names are: "+solutionNames);
            } else {
                throw new IllegalArgumentException(
                        "Under the given account (" + accountName + ") and solution (" + solutionName + "), " +
                        "there is more " + PRODUCT_NAME + " solutions found ("+loginUrls.size()+").\n"+
                        "Not able to determine which solution you want to populate.\n"+
                        "Try to rename one of the solutions to make the names unique.\n"+
                        "Alternatively, use --tenantUrl parameter to indicate the right solution.");
            }
        } else {
            this.portalUrl = portalUrl;
            return loginUrls.get(0);
        }
    }

    public void parseTenantProperties(String tenantUrl) {
        RestClient.HttpResponse response = client.doGet(tenantUrl);
        Pattern p = Pattern.compile("^https?://([^/]+)/agm/webui/alm/([^/]+)/([^/]+)/apm/[^/]+/\\?TENANTID=(.+)$");
        Matcher m = p.matcher(response.getLocation());
        m.matches();
        String host = m.group(1);   // host
        String domain = m.group(2);   // domain
        String project = m.group(3);   // project
        String tenantId = m.group(4);   // tenant ID
        String restUrl = "https://" + host + "/agm";

        Settings settings = Settings.getSettings();
        settings.setDomain(domain);
        settings.setProject(project);
        settings.setTenantId(tenantId);
        settings.setRestUrl(restUrl);
    }

    public String getTimezone() {
        return timezone;
    }

    public void addUser(User user) {
        try {
            try {
                RestClient.HttpResponse response;
                response = client.doGet(portalUrl+"/service/v1/Users/loginName/"+user.getLogin()+"?attributes=tenants");
                String userId = JsonPath.read(response.getResponse(), "$.id"); //we expect there is always only one user of that name (login)

                JSONArray tenants = JsonPath.read(response.getResponse(), "$..tenants[?(@.value == '"+Settings.getSettings().getTenantId()+"')]");
                if (tenants.size() == 0) {
                    log.info("User exists but is not part of this solution; adding "+user.getLogin());

                    JSONObject userJson = new JSONObject();
                    userJson.put("tenantToAdd", Settings.getSettings().getTenantId());
                    client.doPut(portalUrl+"/service/v1/Users/patch/"+userId, userJson.toJSONString(), ContentType.JSON_JSON);
                    log.info("User "+user.getLogin()+" added to the solution");
                } else {
                    log.info("User "+user.getLogin()+" already a member of the solution");
                }
            } catch (IllegalRestStateException e) {
                if (e.getErrorStream().equals("No user was found")) {
                    log.info("No user "+user.getLogin()+" exists in the portal; let's create one.");

                    JSONObject userJson = new JSONObject();
                    userJson.put("userName", StringEscapeUtils.escapeHtml(user.getLogin()));

                    JSONObject userName = new JSONObject();
                    userName.put("familyName", StringEscapeUtils.escapeHtml(user.getLastName()));
                    userName.put("givenName", StringEscapeUtils.escapeHtml(user.getFirstName()));
                    userJson.put("name", userName);

                    JSONArray tenantsArray = new JSONArray();
                    JSONObject tenant = new JSONObject();
                    tenant.put("value", Settings.getSettings().getTenantId());
                    tenantsArray.add(tenant);
                    userJson.put("tenants", tenantsArray);

            //        userJson.put("timezone", timezone);
                    JSONArray phonesArray = new JSONArray();
                    JSONObject phone = new JSONObject();
                    phone.put("value", user.getPhone());
                    phonesArray.add(phone);
                    userJson.put("phoneNumbers", phonesArray);

                    client.doPost(portalUrl+"/service/v1/Users/create", userJson.toJSONString(), ContentType.JSON_JSON);

                    log.info("User created and added to the solution");
                } else throw e;
            }
        } catch (IllegalRestStateException e) {
            log.error("Adding/updating user "+user.getLogin()+" has failed; response code: "+e.getResponseCode(), e);

            if (e.getErrorStream() != null) {
                log.error(e.getErrorStream());
            }
        }
    }
}
