package com.hp.demo.ali;

import com.hp.demo.ali.agm.KanbanStatusInitializer;
import com.hp.demo.ali.agm.PlanBacklogItemHandler;
import com.hp.demo.ali.agm.BuildServerHandler;
import com.hp.demo.ali.agm.DefectHandler;
import com.hp.demo.ali.agm.EntityHandler;
import com.hp.demo.ali.agm.ProjectTaskHandler;
import com.hp.demo.ali.agm.ReleaseHandler;
import com.hp.demo.ali.agm.RequirementHandler;
import com.hp.demo.ali.agm.SheetHandler;
import com.hp.demo.ali.agm.SheetHandlerRegistry;
import com.hp.demo.ali.agm.SprintListInitializer;
import com.hp.demo.ali.excel.AgmEntityIterator;
import com.hp.demo.ali.rest.AgmClient;
import com.hp.demo.ali.entity.User;
import com.hp.demo.ali.excel.EntityIterator;
import com.hp.demo.ali.excel.ExcelReader;
import com.hp.demo.ali.rest.AgmRestService;
import com.hp.demo.ali.rest.ContentType;
import com.hp.demo.ali.rest.DevBridgeDownloader;
import com.hp.demo.ali.rest.IllegalRestStateException;
import com.hp.demo.ali.rest.RestClient;
import com.hp.demo.ali.tools.EntityTools;
import com.hp.demo.ali.tools.XmlFile;
import com.jayway.jsonpath.JsonPath;
import org.apache.ant.compress.taskdefs.Unzip;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.apache.poi.ss.usermodel.Sheet;
import org.hp.almjclient.connection.ServiceResourceAdapter;
import org.hp.almjclient.exceptions.ALMRestException;
import org.hp.almjclient.exceptions.EntityNotFoundException;
import org.hp.almjclient.exceptions.RestClientException;
import org.hp.almjclient.model.marshallers.Entity;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import javax.xml.bind.JAXBException;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by panuska on 10/12/12.
 */
public class DataGenerator {

    private static Logger log = Logger.getLogger(DataGenerator.class.getName());

    private static Settings settings;
    private static AgmClient agmClient = AgmClient.getAgmClient();

    private static SheetHandlerRegistry registry;

    private static BuildGenerator buildGenerator;

    private static void printUsage() {
        System.out.println("AgM data generator "+DataGenerator.class.getPackage().getImplementationVersion());
        System.out.println("For more information and release notes, go to");
        System.out.println("       https://connections.houston.hp.com/docs/DOC-58222");
        System.out.println("Usage: java -jar agm-data-generator.jar [excel-configuration-file.xlsx] [--generate-[u][p][h][b]] [URL] [--solution-name=solution_name] [--force-delete] admin_user_name admin_password");
        System.out.println("       excel-configuration-file.xlsx");
        System.out.println("         - data to generate the project from");
        System.out.println("         - built-in file used if this parameter is not specified");
        System.out.println("         - provide as the first parameter!");
        System.out.println("       --generate-");
        System.out.println("         u - adds (non-portal) users to the portal and project");
        System.out.println("         p - generate project data (entities)");
        System.out.println("         h - generate history within past sprints ");
        System.out.println("         b - generate builds + commits (ALI data)");
        System.out.println("           - access to Hudson / SVN is necessary");
        System.out.println("         - all the above is generated if no option is specified");
        System.out.println("       --solution-name=");
        System.out.println("         - name of the solution (handy when having more solutions)");
        System.out.println("         - first solution is used when nothing specified");
        System.out.println("       --account-name=");
        System.out.println("         - name of the account (handy when having more accounts)");
        System.out.println("         - the logged-in account is used when nothing specified");
        System.out.println("       --force-delete");
        System.out.println("         - do not ask for permission to delete previous data");
        System.out.println("       URL");
        System.out.println("         - URL where to login");
        System.out.println("         - https://gateway.saas.hp.com/msg/ if no URL is specified");
        System.out.println("       admin_user_name and admin_password are the only mandatory options");
        System.out.println("         - always provide as the very last parameters");
        System.out.println();
    }

    public static void main(String[] args) throws JAXBException, IOException {
        if (args.length < 2 || args.length > 7) {    // todo 7 is the current number of possible arguments (make it more robust)
            printUsage();
            System.exit(-1);
        }
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        long startTime = System.currentTimeMillis();
        log.info("AgM data generator " + DataGenerator.class.getPackage().getImplementationVersion());
        log.info("Starting at: "+sdf.format(new Date(startTime)));
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
            Settings.initSettings(reader.getSheet("Settings"));
            settings = Settings.getSettings();
            for (int i = 0; i < 5; i++) {   // todo 5 is the current number of optional arguments (configuration file, user credentials are not optional; make it more robust)
                if (args.length > 2+argIndex) {
                    if (args[argIndex].startsWith("--generate-")) {
                        settings.setAddUsers(false);
                        settings.setGenerateProject(false);
                        settings.setGenerateHistory(false);
                        settings.setGenerateBuilds(false);
                        settings.setMeldRepository(false);
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
                                    settings.setMeldRepository(true);
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
                    } else if (args[argIndex].startsWith("--force-delete")) {
                        settings.setForceDelete(true);
                    } else {
                        System.out.println("Unclear argument "+args[argIndex]);
                        printUsage();
                        System.exit(-1);
                    }
                    argIndex++;
                }
            }
            User admin = User.getUser(settings.getAdmin());
            admin.setLogin(args[argIndex++]);
            admin.setPassword(args[argIndex]);

            resolveTenantUrl();
            AgmRestService.initRestService();

            DevBridgeDownloader downloader = null;
            if (settings.isGenerateProject() && settings.isGenerateBuilds()) {
                downloader = agmClient.downloadDevBridge();
            }
            jobLog = new File("job-"+settings.getTenantId()+"-"+settings.getDomain()+"-"+settings.getProject()+".log");
            if (jobLog.exists()) {
                log.info("Log from previous run found ("+jobLog.getName()+"), previously created data are going to be deleted...");
                if (!settings.isForceDelete()) {
                    log.info("Type 'yes' and press <ENTER> if you wish to continue...");
                    BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
                    String input = in.readLine();
                    if (!input.equals("yes")) {
                        System.exit(-1);
                    }
                } else {
                    log.info("Delete forced; deleting now!");
                }
                openLog();
                String previousEntity = "";
                for (String line = readLogLine(); line != null; line = readLogLine()) {
                    int columnIndex = line.indexOf(':');
                    String entityName = line.substring(0, columnIndex);
                    String agmId = line.substring(columnIndex+2);
                    log.debug("Deleting "+entityName+" with ID: "+agmId);
                    if (!entityName.equals(previousEntity)) {
                        log.info("Deleting entity: "+entityName);
                        previousEntity = entityName;
                    }
                    AgmRestService service = AgmRestService.getCRUDService();
                    try {
                        service.delete(entityName, Integer.parseInt(agmId));
                    } catch (EntityNotFoundException e) {
                        log.error("Cannot delete "+entityName+" with ID: "+agmId);
                    }
                }
            } else {
                log.info("No log ("+jobLog.getName()+") from previous run found; first run against this tenant?");
            }
            if (settings.isAddUsers()) {
                addUsers();
            }
            if (settings.isGenerateProject()) {
                generateProject(reader);
            }
            if (settings.isGenerateHistory()) {
                HistoryGenerator historyGenerator = new HistoryGenerator();
                historyGenerator.generate();
            }
            if (settings.isGenerateBuilds()) {
                List<Long>skippedRevisions = readSkippedRevisions(reader.getSheet("Skip-Revisions"));
                buildGenerator = new BuildGenerator(settings);
                buildGenerator.deleteJob();
                buildGenerator.generate(reader.getSheet("Builds"), skippedRevisions);
                buildGenerator.createJob();
            }
            if (settings.isGenerateProject() && settings.isGenerateBuilds()) {
                configureSvnAgent();
                configureAliDevBridge();

                stopDevBridge();
                downloader.waitTillDownloaded();
                replaceDevBridgeBits(downloader);
                configureDevBridgeBits();
                startDevBridge();

                synchronizeAliDevBridge();
            }
        } catch (RestClientException | ALMRestException e) {
            log.debug(e);
            throw new IllegalStateException(e);
        } catch (RuntimeException e) {
            log.debug(e);
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
        registry.registerHandler("build-server", new BuildServerHandler());
        registry.registerHandler("team-member", new SprintListInitializer()); //once team members are known, the sprints should get initialized
        registry.registerHandler("project-task", new ProjectTaskHandler());
        registry.registerHandler("team", new KanbanStatusInitializer());      // once teams are known, the kanban statuses should be also known

        for (Sheet sheet : entitySheets) {
            String entityName = sheet.getSheetName();
            generateEntity(reader, entityName);
        }
        AgmEntityIterator.logReferences();
    }

    private static Sheet readUsers(ExcelReader reader) {
        log.info("Reading list of users...");
        Sheet users = reader.getSheet("Users");
        EntityIterator<com.hp.demo.ali.entity.Entity> iterator = new EntityIterator<>(users);
        while (iterator.hasNext()) {
            com.hp.demo.ali.entity.Entity userEntity = iterator.next();
            String id = EntityTools.getFieldValue(userEntity, "id");
            String login = EntityTools.getFieldValue(userEntity, "login");
            String password = EntityTools.getFieldValue(userEntity, "password");
            String firstName = EntityTools.getFieldValue(userEntity, "first name");
            String lastName = EntityTools.getFieldValue(userEntity, "last name");
            boolean portalUser = "yes".equals(EntityTools.getFieldValue(userEntity, "portal user"));
            User user = new User(id, login, password, firstName, lastName, portalUser);
            User.addUser(user);
        }
        return users;
    }

    static List<Long> readSkippedRevisions(Sheet sheet) {
        EntityIterator<com.hp.demo.ali.entity.Entity> iterator = new EntityIterator<>(sheet);
        List<Long> revisions = new LinkedList<>();
        while (iterator.hasNext()) {
            com.hp.demo.ali.entity.Entity entity =  iterator.next();
            long revision = EntityTools.getFieldLongValue(entity, "revisions to skip");
            revisions.add(revision);
        }
        return revisions;
    }

    static File jobLog = null;

    public static void writeLogLine(String entityName, String entityId) {
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(jobLog, true));
            writer.write(entityName+": "+entityId+System.lineSeparator());
            writer.flush();
            writer.close();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<String> logItems = new LinkedList<>();
    private static void openLog() {
        log.debug("Reading all the job log...");
        try {
            BufferedReader logReader = new BufferedReader(new FileReader(jobLog));
            for (String line = logReader.readLine(); line != null; line = logReader.readLine()) {
                logItems.add(line);
            }
            logReader.close();
            log.debug("Renaming job log file...");
            String bakFileName = jobLog.getName()+".bak";
            File bakFile = new File(bakFileName);
            bakFile.delete();
            jobLog.renameTo(bakFile);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String readLogLine() {
        if (logItems.size() > 0) {
            log.debug("Processing job log line #"+logItems.size());
            return logItems.remove(logItems.size()-1);
        } else {
            log.debug("Job log processed.");
            return null;
        }
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
        settings.setHost(tenantProperties[0]);
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
        for (User user : User.getUsers()) {
            if (user.isPortalUser()) {
                JSONObject userJson = new JSONObject();
                userJson.put("firstName", user.getFirstName());
                userJson.put("lastName", user.getLastName());
                userJson.put("email", user.getLogin());
                userJson.put("loginName", user.getLogin());
                userJson.put("phone", "1");
                userJson.put("timezone", timezone);
                JSONArray roles = new JSONArray();
                roles.add("CUSTOMER_PORTAL_BASIC");
                userJson.put("roles", roles);
                userJson.put("acceptNotification", true);
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
                    token = JsonPath.read(response.getResponse(), "$.csrftoken");
                } catch (IllegalRestStateException e) {
                    int responseCode = e.getResponseCode();
                    String errorMessage = JsonPath.read(e.getErrorStream(), "$.errorMessage");
                    if (responseCode == 409) {
                        // perhaps, it was not added as it already exists -> trying to attach it as an existing user account
                        log.info(errorMessage);
                        token = JsonPath.read(e.getErrorStream(), "$.csrftoken");
                        try {
                            log.debug("Trying to attach an existing user account: "+ user.getFirstName() + " " + user.getLastName() + ", " + user.getLogin());
                            userJson = new JSONObject();
                            userJson.put("loginName", user.getLogin());
                            userJson.put("allowedServices", instanceIds);
                            roles = new JSONArray();
                            roles.add("CUSTOMER_PORTAL_BASIC");
                            userJson.put("roles", roles);

                            portalClient.setCustomHeader("csrf.token", token);
                            response = portalClient.doPut(settings.getPortalUrl()+"/portal2/service/users/attach/"+user.getLogin(), userJson.toString(), ContentType.JSON_JSON);
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
                    //todo serialize using JSON library
                    String formData = "{\"users\":[{\"loginName\":\""+user.getLogin()+
                            "\", \"firstName\":\""+user.getFirstName()+
                            "\", \"lastName\":\""+user.getLastName()+
                            "\", \"phone\":\"1\", \"email\":\""+user.getLogin()+
                            "\", \"timezone\":\"Europe/Prague\"}]}";
                    ServiceResourceAdapter adapter = AgmRestService.getAdapter();
                    Map<String, String> headers = new HashMap<>(1);
                    headers.put("INTERNAL_DATA", "20120922");
                    adapter.addSessionCookie("AGM_STATE="+"20120922");
                    adapter.putWithHeaders(String.class, settings.getRestUrl()+"/rest/api/portal/users", formData, headers, ServiceResourceAdapter.ContentType.JSON);
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
        String devBridgeScript = settings.getDevBridgeFolder()+DEV_BRIDGE_ZIP_ROOT+"bin\\DevBridge.bat";   // todo this is Windows only!
        Process devBridge = null;
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
            log.debug("Cannot install or start ALI Dev Bridge service");
        } catch (InterruptedException e) {
            log.error("Process ALI Dev Bridge interrupted", e);
        }

    }

    public static void replaceDevBridgeBits(DevBridgeDownloader downloader) {
        log.debug("Deleting old ALI Dev Bridge folder: "+settings.getDevBridgeFolder());
        try {
            FileUtils.deleteDirectory(new File(settings.getDevBridgeFolder()));
        } catch (IOException e) {
            log.debug("File " + settings.getDevBridgeFolder() + " cannot be deleted ", e);
        }
        log.debug("Unpacking downloaded ALI Dev Bridge "+downloader.getFileName()+" into "+settings.getDevBridgeFolder());
        Unzip u = new Unzip();
        u.setSrc(new File(downloader.getFileName()));
        u.setDest(new File(settings.getDevBridgeFolder()));
        u.execute();
    }

    public static void startDevBridge() {
        log.info("Starting ALI Dev Bridge...");
        String devBridgeScript = settings.getDevBridgeFolder()+DEV_BRIDGE_ZIP_ROOT+"bin\\DevBridge.bat";
        Process devBridge = null;
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
            log.error("Cannot install or start ALI Dev Bridge service");
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            log.error("Process ALI Dev Bridge interrupted", e);
            throw new IllegalStateException(e);
        }
    }

    public static void configureDevBridgeBits() {  //todo rename; too similar to configureAliDevBridge method name
        log.info("Configuring ALI Dev Bridge bits...");
        try {
            FileUtils.write(new File(settings.getDevBridgeFolder()+DEV_BRIDGE_ZIP_ROOT+"wrapper\\wrapper-custom.conf"),
                    "\n"+
                    "wrapper.java.additional.101=-Dali.bridge.port=8380\n"+                //todo remove this hard-coded options
                    "wrapper.java.additional.102=-Dali.bridge.ssl.port=8543\n"+
                    "wrapper.java.additional.104=-Dali.bridge.http.warning=false\n"+
                    "wrapper.java.command=c:\\Java\\jdk1.7.0_03\\bin\\java.exe\n", true);

            Collection<File> descriptors = FileUtils.listFiles(new File(settings.getDevBridgeFolder()+DEV_BRIDGE_ZIP_ROOT+"deploy"), new String[] {"xml"}, false);
            assert descriptors.size() == 1;
            File tenantDescriptor = descriptors.iterator().next();
            String tenantDescriptorName = tenantDescriptor.getName().substring(0, tenantDescriptor.getName().length()-4);
            FileUtils.write(new File(settings.getDevBridgeFolder()+DEV_BRIDGE_ZIP_ROOT+"tenants\\"+tenantDescriptorName+"\\conf\\connection.properties"),
                    "\n" +
                    "httpProxy=156.152.46.12:8088\n" +                                     //todo remove this hard-coded options
                    "httpsProxy=156.152.46.12:8088\n" +
                    "noProxyHosts=alm-server\n", true);
        } catch (IOException e) {
            log.error("Cannot configure installed ALI Dev Bridge bits", e);
            throw new IllegalStateException(e);
        }
    }

    public static void configureSvnAgent() {
        log.info("Configuring SVN agent...");
        File agentConfigFile = new File(settings.getSvnAgentFolder()+"\\config\\agent.xml");
        XmlFile file = new XmlFile(agentConfigFile);
        file.setNodeValue("/AgentConfig/AGM/@url", settings.getRestUrl());
        file.setNodeValue("/AgentConfig/Projects/Project/@domain", settings.getDomain());
        file.setNodeValue("/AgentConfig/Projects/Project/@project", settings.getProject());
        file.setNodeValue("/AgentConfig/Projects/Project/@username", User.getUser(settings.getAdmin()).getLogin());
        file.setNodeValue("/AgentConfig/Projects/Project/@password", User.getUser(settings.getAdmin()).getPassword());
        file.save(agentConfigFile);
    }
}
