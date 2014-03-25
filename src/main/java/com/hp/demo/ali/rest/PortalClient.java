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
    private String lwssoCookieKey;
    private String accountName;
    private String timezone = "Europe/Prague";  //todo if account name not provided on cmd-line, the added users will be in this zone by default

    private static final String PRODUCT_NAME = "Agile Manager";

    public PortalClient() {
        client = new RestClient();
    }

    public void login(String loginUrl, User admin) {
        final String[][] data = {
                { "username", admin.getLogin() },
                { "password", admin.getPassword() }
        };
        RestClient.HttpResponse response;
        response = client.doPost(loginUrl, data);
        lwssoCookieKey = client.getCookie("LWSSO_COOKIE_KEY");
        log.debug("Logged in to: " + loginUrl);
        portalUrl = RestTools.getProtocolHost(response.getLocation());
        Settings.getSettings().setPortalUrl(portalUrl);
    }

    public void switchAccount(String accountName) {
        assert accountName != null;
        assert portalUrl != null;
        assert lwssoCookieKey != null;
        client.setCustomHeader("X-XSRF-TOKEN", lwssoCookieKey);
        RestClient.HttpResponse response = client.doGet(portalUrl+"/myaccount/service/settings/activeuser");

        List accounts = JsonPath.read(response.getResponse(), "$.data[0].accountsAndServices[?(@.accountDisplayName == '"+accountName+"')]");
        if (accounts.size() == 0) {
            List<String> accountNames = JsonPath.read(response.getResponse(), "$.data[0].accountsAndServices[*].accountDisplayName");
            String possibleAccountNames;
            if (accountNames == null || accountNames.size() == 0) {
                possibleAccountNames = "\nThere is no account at all.";
            } else {
                possibleAccountNames = "\nThe possible account names are: "+accountNames;
            }
            throw new IllegalArgumentException("The provided account name does not exist: "+accountName+possibleAccountNames);
        }
        this.accountName = accountName;
        JSONObject account = (JSONObject) accounts.get(0);
        log.debug("Account: "+account);

        client.setCustomHeader("X-XSRF-TOKEN", lwssoCookieKey);
        client.doPut(portalUrl + "/myaccount/service/account/switchAccount", account.toString(), ContentType.JSON_JSON);
        log.info("Populate data for this account: " + accountName);
    }

    /**
     * Evaluates the tenant URL of the provided solution under the selected account. If there is more solutions with the
     * very same name, returns URL of the very first solution (of this name).
     * @param solutionName if null, returns URL of the very fist found solution.
     * @return tenant URL
     */
    public String getTenantUrl(String solutionName) {
        client.setCustomHeader("X-XSRF-TOKEN", lwssoCookieKey);
        RestClient.HttpResponse response = client.doGet(portalUrl+"/myaccount/service/customer/CustomerProductsAndSignupRequests");
        List solutions = JsonPath.read(response.getResponse(), "$.data[*].data[?(@.productDisplayLabel == '"+PRODUCT_NAME+"')]");
        if (solutions.size() == 0) {
            throw new IllegalArgumentException("There are no '"+PRODUCT_NAME+"' solutions under the given account: "+(accountName == null ? "default " : accountName));
        }
        String tenantUrl;
        if (solutionName != null) {
            List<String> agmUrls = JsonPath.read(response.getResponse(), "$.data[*].data[?(@.productDisplayLabel == '"+PRODUCT_NAME+"')][?(@.displayLabel == '"+solutionName+"')].launch.url");
            if (agmUrls.size() == 0) {
                List<String> solutionNames = JsonPath.read(response.getResponse(), "$.data[*].data[?(@.productDisplayLabel == '"+PRODUCT_NAME+"')].displayLabel");
                String possibleSolutionNames;
                if (solutionNames == null || solutionNames.size() == 0) {
                    possibleSolutionNames = "\nThere is no solution at all.";
                } else {
                    possibleSolutionNames = "\nThe possible solution names are: "+solutionNames;
                }
                throw new IllegalArgumentException("The provided solution name does not exist: "+solutionName+possibleSolutionNames);
            }
            tenantUrl = agmUrls.get(0); //todo if there is more solutions having the same display label, it will take the very first one
        } else {
            tenantUrl = JsonPath.read(response.getResponse(), "$.data[*].data[?(@.productDisplayLabel == '"+PRODUCT_NAME+"')][0].launch.url");
            log.info("Solution being populated: "+JsonPath.read(response.getResponse(), "$.data[*].data[?(@.productDisplayLabel == '"+PRODUCT_NAME+"')][0].displayLabel"));
        }
        String instanceId;
        if (solutionName != null) {
            List<Integer> instanceIds = JsonPath.read(response.getResponse(), "$.data[*].data[?(@.productDisplayLabel == '"+PRODUCT_NAME+"')][?(@.displayLabel == '"+solutionName+"')].instanceId");
            instanceId = instanceIds.get(0).toString();
        } else {
            Integer instanceIdInteger = JsonPath.read(response.getResponse(), "$.data[*].data[?(@.productDisplayLabel == '"+PRODUCT_NAME+"')][0].instanceId");
            instanceId = instanceIdInteger.toString();
        }
        Settings.getSettings().setInstanceId(instanceId); // todo this is the reason why --tenant-url and --generate-u cannot be combined -> test if it can be combined and if so, remove the warnings
        return tenantUrl;
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

    private int owningAccountId;
    private String owningAccountName;
    private int owningAccountSaasId;

    public void prepareAddingUsers() {
        client.setCustomHeader("X-XSRF-TOKEN", lwssoCookieKey);
        RestClient.HttpResponse response = client.doGet(portalUrl+"/myaccount/service/settings/activeuser");
        owningAccountId = JsonPath.read(response.getResponse(), "$.data[0].owningAccountId");
        owningAccountName = JsonPath.read(response.getResponse(), "$.data[0].owningAccountName").toString();
        owningAccountSaasId = JsonPath.read(response.getResponse(), "$.data[0].owningAccountSaasId");
        timezone = JsonPath.read(response.getResponse(), "$.data[0].timezone");

        log.debug("Owning Account ID: "+owningAccountId);
        log.debug("Owning Account Name: "+owningAccountName);
        log.debug("Owning Account SaaS ID: "+owningAccountSaasId );
    }

    public String getTimezone() {
        return timezone;
    }

    public void addUser(User user) {
        JSONObject userJson = new JSONObject();
        userJson.put("firstName", StringEscapeUtils.escapeHtml(user.getFirstName()));
        userJson.put("lastName", StringEscapeUtils.escapeHtml(user.getLastName()));
        userJson.put("email", StringEscapeUtils.escapeHtml(user.getLogin()));
        userJson.put("loginName", StringEscapeUtils.escapeHtml(user.getLogin()));
        userJson.put("phone", StringEscapeUtils.escapeHtml(user.getPhone()));
        userJson.put("timeZone", timezone);
        userJson.put("roleId", "CUSTOMER_PORTAL_BASIC");
        JSONArray instanceIds = new JSONArray();
        instanceIds.add(Settings.getSettings().getInstanceId() + "#true");
        userJson.put("allowedServices", instanceIds);
        userJson.put("owningAccountId", owningAccountId);
        userJson.put("owningAccountName", owningAccountName);
        userJson.put("owningAccountSaasId", owningAccountSaasId);
        log.info("Adding user: " + user.getFirstName() + " " + user.getLastName() + ", " + user.getLogin());
        try {
            client.setCustomHeader("X-XSRF-TOKEN", lwssoCookieKey);
            RestClient.HttpResponse response = client.doPost(portalUrl+"/myaccount/service/users", userJson.toString(), ContentType.JSON_JSON);
            String message = JsonPath.read(response.getResponse(), "$.pageMessage").toString();
            log.info(message);
        } catch (IllegalRestStateException e) {
            int responseCode = e.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_PRECON_FAILED) {
                // it was not added as it already exists
                String errorMessage = JsonPath.read(e.getErrorStream(), "$.fields.email");
                log.info(errorMessage);
            } else {
                log.error(e.getErrorStream());
            }
        }
    }
}
