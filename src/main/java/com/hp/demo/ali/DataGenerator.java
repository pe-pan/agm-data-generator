package com.hp.demo.ali;

import com.hp.demo.ali.agm.KanbanStatusHandler;
import com.hp.demo.ali.agm.KanbanStatusInitializer;
import com.hp.demo.ali.agm.PlanBacklogItemHandler;
import com.hp.demo.ali.agm.DefectHandler;
import com.hp.demo.ali.agm.EntityHandler;
import com.hp.demo.ali.agm.ProjectTaskHandler;
import com.hp.demo.ali.agm.ReleaseHandler;
import com.hp.demo.ali.agm.RequirementHandler;
import com.hp.demo.ali.agm.SheetHandler;
import com.hp.demo.ali.agm.SheetHandlerRegistry;
import com.hp.demo.ali.agm.SprintListInitializer;
import com.hp.demo.ali.excel.AgmEntityIterator;
import com.hp.demo.ali.excel.ExcelEntity;
import com.hp.demo.ali.rest.AgmClient;
import com.hp.demo.ali.entity.User;
import com.hp.demo.ali.excel.EntityIterator;
import com.hp.demo.ali.excel.ExcelReader;
import com.hp.demo.ali.rest.AgmRestService;
import com.hp.demo.ali.rest.ContentType;
import com.hp.demo.ali.rest.DevBridgeDownloader;
import com.hp.demo.ali.rest.IllegalRestStateException;
import com.hp.demo.ali.rest.RestClient;
import com.hp.demo.ali.tools.ResourceTools;
import com.hp.demo.ali.tools.XmlFile;
import com.jayway.jsonpath.JsonPath;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import org.apache.ant.compress.taskdefs.Unzip;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.log4j.Logger;
import org.apache.poi.ss.usermodel.Sheet;
import org.hp.almjclient.connection.ServiceResourceAdapter;
import org.hp.almjclient.exceptions.ALMRestException;
import org.hp.almjclient.exceptions.RestClientException;
import org.hp.almjclient.model.marshallers.Entity;
import org.w3c.dom.Element;

import javax.xml.bind.JAXBException;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * Created by panuska on 10/12/12.
 */
public class DataGenerator {

    private static Logger log = Logger.getLogger(DataGenerator.class.getName());

    private static Settings settings;
    private static AgmClient agmClient = AgmClient.getAgmClient();

    private static SheetHandlerRegistry registry;
    private static ProxyConfigurator proxyConfigurator;

    private static String getBuildTime() {
        String buildTime = null;
        try {
            JarFile jarFile = new JarFile(DataGenerator.class.getProtectionDomain().getCodeSource().getLocation().getFile());
            Manifest manifest = jarFile.getManifest();
            Attributes attr = manifest.getMainAttributes();
            buildTime = attr.getValue("Build-Time");
        } catch (IOException e) {
            log.debug("Exception when reading build time from manifest file!", e);
        }
        return buildTime;
    }
    private static void printUsage() {
        System.out.println(
                " /============================================================================\\"+System.lineSeparator()+
                " | AgM data generator "+DataGenerator.class.getPackage().getImplementationVersion()+" (build time: "+getBuildTime()+")                     |"+System.lineSeparator()+
                " |============================================================================|"+System.lineSeparator()+
                " | For more information and release notes, go to                              |"+System.lineSeparator()+
                " |   https://connections.houston.hp.com/docs/DOC-58222                        |"+System.lineSeparator()+
                " |============================================================================|"+System.lineSeparator()+
                " | Usage:                                                                     |"+System.lineSeparator()+
                " |   java -jar agm-data-generator.jar [excel-configuration-file.xlsx]         |"+System.lineSeparator()+
                " |   [--generate-[u][p][h][b]] [URL] [--solution-name=solution_name]          |"+System.lineSeparator()+
                " |   [--account-name=account_name] [--tenant-url=tenant_url] [--delete-all]   |"+System.lineSeparator()+
                " |   [--force-delete] admin_name admin_password                               |"+System.lineSeparator()+
                " |----------------------------------------------------------------------------|"+System.lineSeparator()+
                " |     excel-configuration-file.xlsx                                          |"+System.lineSeparator()+
                " |       - data to generate the project from                                  |"+System.lineSeparator()+
                " |       - built-in file used if this parameter is not specified              |"+System.lineSeparator()+
                " |       - provide as the very first parameter!                               |"+System.lineSeparator()+
                " |     --generate-                                                            |"+System.lineSeparator()+
                " |       u - adds (non-portal) users to the portal and project                |"+System.lineSeparator()+
                " |       p - generate project data (entities)                                 |"+System.lineSeparator()+
                " |       h - generate history within past sprints                             |"+System.lineSeparator()+
                " |       b - generate builds / commits (ALI data)                             |"+System.lineSeparator()+
                " |         - access to Hudson / SVN is necessary                              |"+System.lineSeparator()+
                " |       - all the above is generated if no option is specified               |"+System.lineSeparator()+
                " |       - nothing is generated if '--generate-' is specified                 |"+System.lineSeparator()+
                " |     --solution-name=                                                       |"+System.lineSeparator()+
                " |       - name of the solution (handy when having more solutions)            |"+System.lineSeparator()+
                " |       - first found solution is used when nothing specified                |"+System.lineSeparator()+
                " |     --account-name=                                                        |"+System.lineSeparator()+
                " |       - name of the account (handy when having more accounts)              |"+System.lineSeparator()+
                " |       - the logged-in account is used when nothing specified               |"+System.lineSeparator()+
                " |     --tenant-url=                                                          |"+System.lineSeparator()+
                " |       - URL where the tenant is accessible                                 |"+System.lineSeparator()+
                " |       - suppresses solution-name and account-name options (if provided)    |"+System.lineSeparator()+
                " |       - users cannot be added when providing this option                   |"+System.lineSeparator()+
                " |     --delete-all                                                           |"+System.lineSeparator()+
                " |       - deletes all data from the tenant regardless previous runs          |"+System.lineSeparator()+
                " |       - does not pay attention to the job log content                      |"+System.lineSeparator()+
                " |     --force-delete                                                         |"+System.lineSeparator()+
                " |       - do not ask for permission to delete previous data                  |"+System.lineSeparator()+
                " |       - only deletes data when used along with '--generate-' option        |"+System.lineSeparator()+
                " |     URL                                                                    |"+System.lineSeparator()+
                " |       - URL where to login                                                 |"+System.lineSeparator()+
                " |       - https://gateway.saas.hp.com/msg/ if no URL is specified            |"+System.lineSeparator()+
                " |     admin_name and admin_password are the only mandatory options           |"+System.lineSeparator()+
                " |       - always provide as the very last parameters                         |"+System.lineSeparator()+
                " |============================================================================|"+System.lineSeparator()+
                " | Example of how to provide parameters:                                      |"+System.lineSeparator()+
                " |   data.xlsx \"--solution-name=Horizon_Demo\" \"--account-name=kenshoo\"        |"+System.lineSeparator()+
                " |   --generate-phb --force-delete petr.panuska@hp.com my_horizon_password    |"+System.lineSeparator()+
                " \\============================================================================/"+System.lineSeparator()+System.lineSeparator());
    }

    public static void main(String[] args) throws JAXBException, IOException {
        if (args.length < 2 || args.length > 9) {    // todo 9 is the current number of possible arguments (make it more robust)
            printUsage();
            System.exit(-1);
        }
        log.debug("Options: "+Arrays.toString(args));
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        long startTime = System.currentTimeMillis();
        log.info("AgM data generator " + DataGenerator.class.getPackage().getImplementationVersion()+" (build time: "+getBuildTime()+")");
        log.info("Starting at: "+sdf.format(new Date(startTime)));
        Migrator.migrate();
        try {
            int argIndex;
            ExcelReader reader;
            if (args.length > 2 && args[0].endsWith(".xlsx")) {
                reader = new ExcelReader(new FileInputStream(args[0]));
                log.info("Using configuration "+args[0]);
                argIndex = 1;
            } else {
                reader = new ExcelReader(DataGenerator.class.getResourceAsStream("/data.xlsx")); // use built-in excel file
                log.info("Using built-in configuration...");
                argIndex = 0;
            }
            readUsers(reader);
            Settings.initSettings(reader);
            settings = Settings.getSettings();
            for (int i = 0; i < 7; i++) {   // todo 7 is the current number of optional arguments (user credentials are not optional); make it more robust
                if (args.length > 2+argIndex) {
                    if (args[argIndex].startsWith("--generate-")) {
                        settings.setAddUsers(false);
                        settings.setGenerateProject(false);
                        settings.setGenerateHistory(false);
                        settings.setGenerateBuilds(false);
                        for (int j = "--generate-".length(); j < args[argIndex].length(); j++) {
                            switch (args[argIndex].charAt(j)) {
                                case 'u' :
                                    settings.setAddUsers(true);
                                    break;
                                case 'p' :
                                    settings.setGenerateProject(true);
                                    break;
                                case 'h' :
                                    settings.setGenerateHistory(true);
                                    break;
                                case 'b' :
                                    settings.setGenerateBuilds(true);
                                    break;
                                default:
                                    System.out.println("Unknown parameter when using option generate: "+args[argIndex].charAt(j));
                                    printUsage();
                                    System.exit(-1);
                            }
                        }
                        log.info(settings.isAddUsers() ? "Users will be added to the project..." : "No users will be added to the project...");
                        log.info(settings.isGenerateProject() ? "Entities will be generated..." : "No entities will be generated...");
                        log.info(settings.isGenerateHistory() ? "History will be generated..." : "No history wil be generated...");
                        log.info(settings.isGenerateBuilds() ? "Builds and commits will be generated..." : "No builds/commits will be generated...");
                    } else if (args[argIndex].startsWith("http")) {
                        settings.setLoginUrl(args[argIndex]);
                    } else if (args[argIndex].startsWith("--account-name=")) {
                        settings.setAccountName(args[argIndex].substring("--account-name=".length()));
                        log.info("Account name being used: "+settings.getAccountName());
                    } else if (args[argIndex].startsWith("--solution-name=")) {
                        settings.setSolutionName(args[argIndex].substring("--solution-name=".length()));
                        log.info("Solution being populated: "+settings.getSolutionName());
                    } else if (args[argIndex].startsWith("--tenant-url=")) {
                        settings.setTenantUrl(args[argIndex].substring("--tenant-url=".length()));
                        log.info("Tenant URL: "+settings.getTenantUrl());
                    } else if (args[argIndex].startsWith("--force-delete")) {
                        settings.setForceDelete(true);
                    } else if (args[argIndex].startsWith("--delete-all")) {
                        settings.setDeleteAll(true);
                    } else {
                        System.out.println("Unclear argument "+args[argIndex]);
                        printUsage();
                        System.exit(-1);
                    }
                    argIndex++;
                }
            }
            if (settings.isAddUsers() && settings.getTenantUrl() != null) {
                log.error("Invalid combination: Adding users while providing Tenant URL is not supported.");
                log.error("Use --generate-phb option when providing --tenant-url option.");
                log.error("The tool does not have to work properly!");
            }

            proxyConfigurator = new ProxyConfigurator();
            proxyConfigurator.init();

            User admin = User.getUser(settings.getAdmin());
            admin.setLogin(args[argIndex++]);
            admin.setPassword(args[argIndex]);

            resolveTenantUrl();
            AgmRestService.initRestService();

            DevBridgeDownloader downloader = null;
            if (settings.isGenerateProject() || settings.isGenerateBuilds()) {
                downloader = agmClient.downloadDevBridge();
            }
            JobLogger jobLogger = new JobLogger(reader);
            jobLogger.deleteExistingData();

            if (settings.isAddUsers()) {
                addUsers();
            }
            if (settings.isGenerateProject() || settings.isGenerateHistory()) {
                configureProject();
            }
            if (settings.isGenerateProject() || settings.isGenerateBuilds()) {
                configureSvnAgent();
                configureAliDevBridge();
                stopDevBridge();
            }
            if (settings.isGenerateProject()) {
                generateProject(reader);
            }
            if (settings.isGenerateHistory()) {
                HistoryGenerator historyGenerator = new HistoryGenerator();
                historyGenerator.generate();
            }
            if (settings.isGenerateBuilds()) {
                BuildGenerator buildGenerator = new BuildGenerator(reader);
                buildGenerator.deleteJob();
                buildGenerator.configureHudson(proxyConfigurator);
                buildGenerator.generate();
                buildGenerator.createJob();
            }
            if (settings.isGenerateProject() || settings.isGenerateBuilds()) {
                downloader.waitTillDownloaded();
                replaceDevBridgeBits(downloader);
                configureDevBridgeBits();
                startDevBridge();

                synchronizeAliDevBridge();
            }
        } catch (RuntimeException e) {
            log.debug("Exception thrown:", e);
            throw e;
        } finally {
            long endTime = System.currentTimeMillis();
            log.info("Finished at: "+sdf.format(new Date(endTime)));
            long total = endTime - startTime;

            log.info(String.format("The generator ran for: %02d:%02d.%03d", total / 60000, (total%60000)/1000, total%100));
        }
    }

    private static void generateProject(ExcelReader reader) {
        log.info("Generating project data...");
        registry = new SheetHandlerRegistry();
        SheetHandler generalHandler = new EntityHandler();
        List<Sheet> entitySheets = reader.getAllEntitySheets();
        for (Sheet sheet : entitySheets) {
            String entityName = sheet.getSheetName();
            registry.registerHandler(entityName, generalHandler);
        }
        // these specialized handlers will overwrite the handlers above for these specific entities
        registry.registerHandler("release", new ReleaseHandler());
        registry.registerHandler("requirement", new RequirementHandler());
        registry.registerHandler("defect", new DefectHandler());
        registry.registerHandler("release-backlog-item", new PlanBacklogItemHandler());
        registry.registerHandler("team-member", new SprintListInitializer()); //once team members are known, the sprints should get initialized
        registry.registerHandler("project-task", new ProjectTaskHandler());
        registry.registerHandler("team", new KanbanStatusInitializer());      // once teams are known, the kanban statuses should be also known
        registry.registerHandler("kanban-status", new KanbanStatusHandler());

        for (Sheet sheet : entitySheets) {
            String entityName = sheet.getSheetName();
            generateEntity(reader, entityName);
        }
        AgmEntityIterator.logReferences();
    }

    private static Sheet readUsers(ExcelReader reader) {
        log.info("Reading list of users...");
        Sheet users = reader.getSheet("Users");
        EntityIterator<ExcelEntity> iterator = new EntityIterator<>(users);
        while (iterator.hasNext()) {
            ExcelEntity entity = iterator.next();
            String id = entity.getFieldValue("id");
            String login = entity.getFieldValue("login");
            String password = entity.getFieldValue("password");
            String firstName = entity.getFieldValue("first name");
            String lastName = entity.getFieldValue("last name");
            String phone = entity.getFieldValue("phone");
            boolean portalUser = "yes".equals(entity.getFieldValue("portal user"));
            User user = new User(id, login, password, firstName, lastName, phone, portalUser);
            User.addUser(user);
        }
        return users;
    }

    private static void generateEntity(ExcelReader reader, String sheetName) {
        Sheet sheet = reader.getSheet(sheetName);
        AgmEntityIterator<Entity> iterator = new AgmEntityIterator<>(sheet);

        SheetHandler handler = registry.getHandler(sheetName);
        handler.init(sheetName);
        while (iterator.hasNext()) {
            Entity entity = iterator.next();
            handler.row(entity);
        }
        handler.terminate();
    }

    public static void resolveTenantUrl() {
        log.info("Resolving Tenant ID, domain and project name...");
        User admin = User.getUser(settings.getAdmin());

        String loginUrl;
        try {
            loginUrl = agmClient.resolveLoginUrl(settings.getLoginUrl());
        } catch (IllegalStateException e) {
            log.debug(e);
            log.error("Incorrect credentials or URL: " + admin.getLogin() + " / " + admin.getPassword());
            log.error("At: " + settings.getLoginUrl());
            System.exit(-1);
            throw e;         //will never be executed
        }
        settings.setLoginUrl(loginUrl);
        String[] tenantProperties;
        try {
            tenantProperties = agmClient.login(loginUrl, admin);
        } catch (IllegalStateException e) {
            log.debug(e);
            log.error("Incorrect credentials or URL: " + admin.getLogin() + " / " + admin.getPassword());
            log.error("At: " + settings.getLoginUrl());
            System.exit(-1);
            throw e;        //will never be executed
        } catch (IllegalArgumentException e) {
            log.debug(e);
            log.error(e.getMessage());
            System.exit(-1);
            throw e;        //will never be executed
        }
        settings.setDomain(tenantProperties[1]);
        settings.setProject(tenantProperties[2]);
        settings.setTenantId(tenantProperties[3]);
        settings.setRestUrl(tenantProperties[4]);
        settings.setPortalUrl(tenantProperties[5]);
        settings.setInstanceId(tenantProperties[6]);
    }

    public static void synchronizeAliDevBridge() {
        log.debug("Synchronizing builds and source code changes");

        User admin = User.getUser(settings.getAdmin());
        String [][] data = {
                { "j_username", admin.getLogin() },
                { "j_password", admin.getPassword() },
                { "a", "" }
        };
        RestClient devBridgeClient = new RestClient();
        RestClient.HttpResponse response = devBridgeClient.doPost(settings.getAliDevBridgeUrl() + "/j_spring_security_check", data);

        data = new String[][] { { "fid", response.getXFid() } };
        devBridgeClient.doPost(settings.getAliDevBridgeUrl() + "/rest/task/start/BuildSyncTask", data);
        log.info("Build synchronization started!");
        devBridgeClient.doPost(settings.getAliDevBridgeUrl() + "/rest/task/start/SourceSyncTask", data);
        log.info("Source code synchronization started!");
    }

    public static void configureAliDevBridge() {
        log.info("Configuring ALI Dev Bridge...");

        ServiceResourceAdapter adapter = AgmRestService.getAdapter();
        try {
            Map<String, String> headers = new HashMap<>(1);
            headers.put("INTERNAL_DATA", "20101021");
            adapter.addSessionCookie("STATE="+"20101021");
            adapter.postWithHeaders(String.class, AgmRestService.getCollectionBaseUrl() + "scm/dev-bridge/deployment-url", "bridge_url=" + settings.getAliDevBridgeUrl(), headers, ServiceResourceAdapter.ContentType.NONE);
        } catch (RestClientException | ALMRestException e) {
            throw new IllegalStateException(e);
        }
        //todo check response code
    }

    public static void addUsers() {
        RestClient portalClient = new RestClient();
        User admin = User.getUser(settings.getAdmin());

        String[][] data = {
                { "username", admin.getLogin() },
                { "password", admin.getPassword() }
        };
        portalClient.doPost(settings.getLoginUrl(), data);
        RestClient.HttpResponse response = portalClient.doGet(settings.getPortalUrl()+"/portal2/service/settings/general?");
        String token = JsonPath.read(response.getResponse(), "$.cSRFTokenVal");
        response = portalClient.doGet(settings.getPortalUrl()+"/portal2/service/users/session?");
        String timezone = JsonPath.read(response.getResponse(), "$.timezone");
        String owningAccountId = JsonPath.read(response.getResponse(), "$.owningAccountId").toString();
        String owningAccountName = JsonPath.read(response.getResponse(), "$.owningAccountName");
        String owningAccountSaasId = JsonPath.read(response.getResponse(), "$.owningAccountSaasId").toString();

        String accountName = settings.getAccountName();
        if (accountName != null) {           //todo refactor => merge with AgmClient.login method (where account is also being set)
            List<net.minidev.json.JSONObject> accounts = JsonPath.read(response.getResponse(), "$.accountsAndServices[?(@.accountDisplayName == '"+accountName+"')]");
            if (accounts.size() == 0) {
                List<String> accountNames = JsonPath.read(response.getResponse(), "$.accountsAndServices[*].accountDisplayName");
                String possibleAccountNames;
                if (accountNames == null || accountNames.size() == 0) {
                    possibleAccountNames = "\nThere is no account at all.";
                } else {
                    possibleAccountNames = "\nThe possible account names are: "+accountNames;
                }
                throw new IllegalArgumentException("The provided account name does not exist: "+accountName+possibleAccountNames);
            }

            String nextAccountId = accounts.get(0).get("accountId").toString();
            portalClient.setCustomHeader("csrf.token", token);
            JSONObject accountId = new JSONObject();
            accountId.put("id", owningAccountId);
            accountId.put("nextAccountId", nextAccountId);
            portalClient.doPut(settings.getPortalUrl()+"/portal2/service/accounts/updateCurrentAccount/"+owningAccountId, accountId.toString(), ContentType.JSON_JSON);
        }
        for (User user : User.getUsers()) {
            if (user.isPortalUser()) {
                JSONObject userJson = new JSONObject();
                userJson.put("firstName", StringEscapeUtils.escapeHtml(user.getFirstName()));
                userJson.put("lastName", StringEscapeUtils.escapeHtml(user.getLastName()));
                userJson.put("email", StringEscapeUtils.escapeHtml(user.getLogin()));
                userJson.put("loginName", StringEscapeUtils.escapeHtml(user.getLogin()));
                userJson.put("phone", StringEscapeUtils.escapeHtml(user.getPhone()));
                userJson.put("timezone", timezone);
                JSONArray roles = new JSONArray();
                roles.add("CUSTOMER_PORTAL_BASIC");
                userJson.put("roles", roles);
                userJson.put("acceptNotification", false);
                userJson.put("status", true);
                userJson.put("id", null);
                JSONArray instanceIds = new JSONArray();
                instanceIds.add(settings.getInstanceId()+"#true");
                userJson.put("allowedServices", instanceIds);
                userJson.put("userImpersonation", 0);
                userJson.put("owningAccountId", owningAccountId);
                userJson.put("owningAccountName", owningAccountName);
                userJson.put("owningAccountSaasId", owningAccountSaasId);
                log.info("Adding user: " + user.getFirstName() + " " + user.getLastName() + ", " + user.getLogin());
                try {
                    portalClient.setCustomHeader("csrf.token", token);
                    response = portalClient.doPost(settings.getPortalUrl()+"/portal2/service/users", userJson.toString(), ContentType.JSON_JSON);
                    log.info("New user added");
                    token = JsonPath.read(response.getResponse(), "$.csrftoken");
                } catch (IllegalRestStateException e) {
                    int responseCode = e.getResponseCode();
                    String errorMessage = JsonPath.read(e.getErrorStream(), "$.errorMessage");
                    if (responseCode == HttpURLConnection.HTTP_CONFLICT) {
                        // perhaps, it was not added as it already exists -> trying to attach it as an existing user account
                        log.info(errorMessage);
                        token = JsonPath.read(e.getErrorStream(), "$.csrftoken");
                        try {
                            log.debug("Trying to attach an existing user account: "+ user.getFirstName() + " " + user.getLastName() + ", " + user.getLogin());
                            userJson = new JSONObject();
                            userJson.put("loginName", StringEscapeUtils.escapeHtml(user.getLogin()));
                            userJson.put("allowedServices", instanceIds);
                            userJson.put("roles", "CUSTOMER_PORTAL_BASIC");

                            portalClient.setCustomHeader("csrf.token", token);
                            response = portalClient.doPut(settings.getPortalUrl()+"/portal2/service/users/attach/"+ URLEncoder.encode(user.getLogin()), userJson.toString(), ContentType.JSON_JSON);
                            log.info("Existing user attached");
                            token = JsonPath.read(response.getResponse(), "$.csrftoken");
                        } catch (IllegalRestStateException e2) {
                            log.error("Cannot add user to portal: "+user.getFirstName()+" "+user.getLastName());
                            log.error(e2.getErrorStream());
                            token = JsonPath.read(e.getErrorStream(), "$.csrftoken");
                        }
                    } else {
                        log.error(errorMessage);
                    }
                }
                try {
                    userJson = new JSONObject();
                    userJson.put("firstName", StringEscapeUtils.escapeHtml(user.getFirstName()));
                    userJson.put("lastName", StringEscapeUtils.escapeHtml(user.getLastName()));
                    userJson.put("email", StringEscapeUtils.escapeHtml(user.getLogin()));
                    userJson.put("loginName", StringEscapeUtils.escapeHtml(user.getLogin()));
                    userJson.put("phone", StringEscapeUtils.escapeHtml(user.getPhone()));
                    userJson.put("timezone", timezone);
                    JSONArray users = new JSONArray();
                    users.add(userJson);
                    JSONObject usersJson = new JSONObject();
                    usersJson.put("users", users);
                    ServiceResourceAdapter adapter = AgmRestService.getAdapter();
                    Map<String, String> headers = new HashMap<>(1);
                    headers.put("INTERNAL_DATA", "20120922");
                    adapter.addSessionCookie("AGM_STATE="+"20120922");
                    adapter.putWithHeaders(String.class, settings.getRestUrl()+"/rest/api/portal/users", usersJson.toString(), headers, ServiceResourceAdapter.ContentType.JSON);
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
        }
    }

    public static final String DEV_BRIDGE_ZIP_ROOT = "\\DevBridge\\";

    public static void stopDevBridge() {
        log.info("Stopping ALI Dev Bridge...");
        String devBridgeScript = settings.getAliDevBridgeFolder()+DEV_BRIDGE_ZIP_ROOT+"bin\\DevBridge.bat";   // todo this is Windows only!
        Process devBridge;
        try {
            devBridge = Runtime.getRuntime().exec(devBridgeScript+" stop");
            devBridge.waitFor();
            log.debug("Service stopped");
            if (devBridge.exitValue() != 0) {
                log.debug(IOUtils.toString(devBridge.getErrorStream()));
                log.debug("Cannot stop ALI Dev Bridge service; code " + devBridge.exitValue());
            }
            devBridge = Runtime.getRuntime().exec(devBridgeScript+" remove");
            devBridge.waitFor();
            log.debug("Service removed");
            if (devBridge.exitValue() != 0) {
                log.debug(IOUtils.toString(devBridge.getErrorStream()));
                log.debug("Cannot remove ALI Dev Bridge service; code " + devBridge.exitValue());
            }
        } catch (IOException e) {
            log.debug("Cannot stop or remove ALI Dev Bridge service", e);
        } catch (InterruptedException e) {
            log.error("Process ALI Dev Bridge interrupted", e);
        }

    }

    public static void replaceDevBridgeBits(DevBridgeDownloader downloader) {
        log.debug("Deleting old ALI Dev Bridge folder: "+settings.getAliDevBridgeFolder());
        try {
            FileUtils.deleteDirectory(new File(settings.getAliDevBridgeFolder()));
        } catch (IOException e) {
            log.debug("File " + settings.getAliDevBridgeFolder() + " cannot be deleted ", e);
        }
        log.debug("Unpacking downloaded ALI Dev Bridge "+downloader.getFile()+" into "+settings.getAliDevBridgeFolder());
        Unzip u = new Unzip();
        u.setSrc(downloader.getFile());
        u.setDest(new File(settings.getAliDevBridgeFolder()));
        u.execute();
    }

    public static void startDevBridge() {
        log.info("Starting ALI Dev Bridge...");
        String devBridgeScript = settings.getAliDevBridgeFolder()+DEV_BRIDGE_ZIP_ROOT+"bin\\DevBridge.bat";
        Process devBridge;
        try {
            devBridge = Runtime.getRuntime().exec(devBridgeScript+" install");
            devBridge.waitFor();
            log.debug("Service installed");
            log.debug(IOUtils.toString(devBridge.getInputStream()));
            if (devBridge.exitValue() != 0) {
                log.error(IOUtils.toString(devBridge.getErrorStream()));
                log.error("Cannot install ALI Dev Bridge service; code "+devBridge.exitValue());
                throw new IllegalStateException("Cannot install ALI Dev Bridge service; code "+devBridge.exitValue());
            }
            devBridge = Runtime.getRuntime().exec(devBridgeScript+" start");
            devBridge.waitFor();
            log.debug("Service started");
            log.debug(IOUtils.toString(devBridge.getInputStream()));
            if (devBridge.exitValue() != 0) {
                log.error(IOUtils.toString(devBridge.getErrorStream()));
                log.error("Cannot start ALI Dev Bridge service; code "+devBridge.exitValue());
                throw new IllegalStateException("Cannot start ALI Dev Bridge service; code "+devBridge.exitValue());
            }
            RestClient devBridgeClient = new RestClient();
            int attempts = 15;
            while (attempts > 0) {
                try {
                    Thread.sleep(2000);
                    devBridgeClient.doGet(settings.getAliDevBridgeUrl());
                    log.info("ALI Dev Bridge started!");
                    attempts = 0;
                } catch (IllegalStateException e) {
                    log.info("ALI Dev Bridge has not started yet; attempts to try: "+attempts);
                    attempts--;
                }
            }
        } catch (IOException e) {
            log.error("Cannot install or start ALI Dev Bridge service", e);
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            log.error("Process ALI Dev Bridge interrupted", e);
            throw new IllegalStateException(e);
        }
    }

    public static void configureDevBridgeBits() {  //todo rename; too similar to configureAliDevBridge method name
        log.info("Configuring ALI Dev Bridge bits...");
        try {
            final String fileName = Migrator.WRAPPER_CUSTOM_CONF_FILE;
            File file = new File(Migrator.CONF_DIR, fileName);
            InputStream in = ResourceTools.getCustomResource(file);
            OutputStream out = new FileOutputStream(settings.getAliDevBridgeFolder()+DEV_BRIDGE_ZIP_ROOT+"wrapper"+File.separator+fileName, true);
            IOUtils.copy(in, out);
            in.close();
            out.close();

            Collection<File> descriptors = FileUtils.listFiles(new File(settings.getAliDevBridgeFolder()+DEV_BRIDGE_ZIP_ROOT+"deploy"), new String[] {"xml"}, false);
            assert descriptors.size() == 1;
            File tenantDescriptor = descriptors.iterator().next();
            String tenantDescriptorName = tenantDescriptor.getName().substring(0, tenantDescriptor.getName().length() - 4);
            CharSequence content = proxyConfigurator.getDevBridgeProxyConfiguration();
            FileUtils.write(new File(settings.getAliDevBridgeFolder()+DEV_BRIDGE_ZIP_ROOT+"tenants\\"+tenantDescriptorName+"\\conf\\connection.properties"),
                    content);
        } catch (IOException e) {
            log.error("Cannot configure installed ALI Dev Bridge bits", e);
            throw new IllegalStateException(e);
        }
    }

    public static void configureSvnAgent() {
        log.info("Configuring SVN agent...");
        File agentConfigFile = new File(settings.getSvnAgentFolder()+"\\config\\agent.xml");
        XmlFile file = new XmlFile(agentConfigFile);
        Element httpProxy = proxyConfigurator.getSvnAgentProxyConfiguration(file.createElement("HttpProxy"));  //todo refactor so the element instance is being created inside of the method getSvnAgentProxyConfiguration
        file.setNode("/AgentConfig", "HttpProxy", httpProxy);
        file.setNodeValue("/AgentConfig/AGM/@url", settings.getRestUrl());
        file.setNodeValue("/AgentConfig/Projects/Project/@domain", settings.getDomain());
        file.setNodeValue("/AgentConfig/Projects/Project/@project", settings.getProject());
        file.setNodeValue("/AgentConfig/Projects/Project/@username", User.getUser(settings.getAdmin()).getLogin());
        file.setNodeValue("/AgentConfig/Projects/Project/@password", User.getUser(settings.getAdmin()).getPassword());
        file.save(agentConfigFile);
    }

    public static void configureProject() {
        log.info("Configuring project...");
        String [][] params = new String[][] {
                { "APM_EXTENSION", "project", "projectlevelkey", "SETTINGS", "SEND_MAIL_ON_ASSIGNMENT_CHANGE" },
                { "APM_EXTENSION", "project", "projectlevelkey", "SETTINGS", "SEND_MAIL_ON_STATUS_CHANGE" },
                { "APM_EXTENSION", "project", "projectlevelkey", "SETTINGS", "SEND_MAIL_ON_EXCEED_CYCLE_TIME" },
                { "APM_EXTENSION", "project", "projectlevelkey", "PLANNING", "AUTO_CREATE_TASK_FOR_DEFECT" }
        };

        StringBuilder builder = new StringBuilder();
        builder.append("<ConfigurationResourceParameters>");
        for (String[] paramSet : params) {
            builder.append("<ConfigurationResourceParameter><configurationKey><extensionName>").append(paramSet[0]).append("</extensionName>").
                    append("<scopeType>").append(paramSet[1]).append("</scopeType>").
                    append("<scopeKey>").append(paramSet[2]).append("</scopeKey>").
                    append("<componentKey>").append(paramSet[3]).append("</componentKey>").
                    append("<propertyKey>").append(paramSet[4]).append("</propertyKey>").
                    append("</configurationKey>" +
                            "<configurationValue>" +
                            "<value>false</value>" +
                            "<className>java.lang.Boolean</className>" +
                            "</configurationValue>" +
                            "</ConfigurationResourceParameter>");
        }
        builder.append("</ConfigurationResourceParameters>");
        try {
            AgmRestService.getAdapter().post(String.class, AgmRestService.getCollectionBaseUrl()+"/customization/configurationservice/setvalues", builder.toString(), ServiceResourceAdapter.ContentType.XML);
        } catch (RestClientException | ALMRestException e) {
            throw new IllegalStateException(e);
        }
    }

}
