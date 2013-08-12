package com.hp.demo.ali.rest;

import com.hp.demo.ali.Settings;
import com.hp.demo.ali.entity.User;
import com.jayway.jsonpath.JsonPath;
import org.apache.log4j.Logger;
import org.json.simple.JSONObject;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private RestClient client;
    private String restUrl;

    private AgmClient() {
        client = new RestClient();
    }

    public String resolveLoginUrl(String agmUrl) {
        String loginUrl = agmUrl.replace("https://", "http://");
        RestClient.HttpResponse  response = client.doGet(loginUrl);
        loginUrl = response.getLocation().length() > 0 ? response.getLocation() : agmUrl;
        String loginContext = RestTools.extractString(response.getResponse(), "//form[@id='loginForm']/@action");
        return RestTools.getProtocolHost(loginUrl)+loginContext;
    }

    /**
     * Returns an array of strings:
     * [0] host
     * [1] domain
     * [2] project
     * [3] tenant ID
     * [4] REST API URL
     * [5] portal URL
     *
     * @param loginUrl
     * @param admin
     * @return
     */
    public String[] login(String loginUrl, User admin) {
        final String[][] data = {
                { "username", admin.getLogin() },
                { "password", admin.getPassword() }
        };
        RestClient.HttpResponse response;
        response = client.doPost(loginUrl, data);
        String portalUrl = RestTools.getProtocolHost(response.getLocation());
        log.debug("Logged in to: " + loginUrl);
        log.debug("Portal URL: "+portalUrl);
        String accountName = Settings.getSettings().getAccountName();
        if (accountName != null) {
            response = client.doGet(portalUrl+"/portal2/service/settings/general?");
            String token = JsonPath.read(response.getResponse(), "$.cSRFTokenVal");

            response = client.doGet(portalUrl+"/portal2/service/users/session?");
            String owningAccountId = JsonPath.read(response.getResponse(), "$.owningAccountId").toString();
            log.debug("Owning Account ID: "+owningAccountId );
            List accountIds = JsonPath.read(response.getResponse(), "$.accountsAndServices[?(@.accountName == '"+accountName+"')].accountId");
            if (accountIds.size() == 0) {
                throw new IllegalArgumentException("The provided account name does not exist: "+accountName);
            }
            String accountId = accountIds.get(0).toString();
            log.debug("Account ID: "+accountId);
            client.setCustomHeader("csrf.token", token);
            JSONObject accountData = new JSONObject();
            accountData.put("id", owningAccountId);
            accountData.put("nextAccountId", accountId);

            client.doPut(portalUrl+"/portal2/service/accounts/updateCurrentAccount/"+owningAccountId, accountData.toString(), ContentType.JSON_JSON);
            log.info("Populate data for this account: "+accountName);
        }
        String solutionName = Settings.getSettings().getSolutionName();
        response = client.doGet(RestTools.getProtocolHost(response.getLocation())+"/portal2/service/services/requestsAndServices");
        String agmUrl;
        List solutions = JsonPath.read(response.getResponse(), "$.data[?(@.solutionName == 'Agile Manager')].solutionInstances");
        if (solutions.size() == 0) {
            throw new IllegalArgumentException("There are no 'Agile Manager' solutions under the given account: "+(accountName == null ? "default " : accountName));
        }
        if (solutionName != null) {
            List<String> agmUrls = (List<String>)JsonPath.read(response.getResponse(), "$.data[?(@.solutionName == 'Agile Manager')].solutionInstances[?(@.displayName == '"+solutionName+"')].loginUrl");
            if (agmUrls.size() == 0) {
                throw new IllegalArgumentException("The provided solution name does not exist: "+solutionName);
            }
            agmUrl = agmUrls.get(0);
        } else {
            agmUrl = JsonPath.read(response.getResponse(), "$.data[?(@.solutionName == 'Agile Manager')].solutionInstances[0].loginUrl");
            log.info("Solution being populated: "+JsonPath.read(response.getResponse(), "$.data[?(@.solutionName == 'Agile Manager')].solutionInstances[0].displayName"));
        }
        String instanceId;
        if (solutionName != null) {
            List<Integer> instanceIds = JsonPath.read(response.getResponse(), "$.data[?(@.solutionName == 'Agile Manager')].solutionInstances[?(@.displayName == '"+solutionName+"')].instanceId");
            instanceId = instanceIds.get(0).toString();
        } else {
            Integer instanceIdInteger = JsonPath.read(response.getResponse(), "$.data[?(@.solutionName == 'Agile Manager')].solutionInstances[0].instanceId");
            instanceId = instanceIdInteger.toString();
        }

        response = client.doGet(agmUrl);

        Pattern p = Pattern.compile("^https?://([^/]+)/agm/webui/alm/([^/]+)/([^/]+)/apm/[^/]+/\\?TENANTID=(.+)$");
        Matcher m = p.matcher(response.getLocation());
        m.matches();
        String[] tenantProperties = new String[7];
        tenantProperties[0] = m.group(1);   // host
        tenantProperties[1] = m.group(2);   // domain
        tenantProperties[2] = m.group(3);   // project
        tenantProperties[3] = m.group(4);   // tenant ID

        tenantProperties[4] = "https://" + tenantProperties[0] + "/qcbin";
        tenantProperties[5] = portalUrl;    // portal URL
        tenantProperties[6] = instanceId;

        restUrl = tenantProperties[4] + "/rest/domains/" + tenantProperties[1] + "/projects/" + tenantProperties[2] + "/";
        return tenantProperties;
    }

    public DevBridgeDownloader downloadDevBridge() {
        DevBridgeDownloader downloader = new DevBridgeDownloader(client);
        client.doPost(restUrl+"scm/dev-bridge/bundle", null, downloader);  // /scm/dev-bridge - downloads only war file!
        return downloader;
    }
}
