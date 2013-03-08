package com.hp.demo.ali;

import com.hp.demo.ali.entity.*;
import com.hp.demo.ali.excel.EntityIterator;
import com.hp.demo.ali.excel.ExcelReader;
import com.hp.demo.ali.rest.*;
import com.hp.demo.ali.tools.EntityTools;
import org.apache.ant.compress.taskdefs.Unzip;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.apache.poi.ss.usermodel.Sheet;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by panuska on 10/12/12.
 */
public class DataGenerator {

    private static Logger log = Logger.getLogger(RestClient.class.getName());

    private static Settings settings;
    private static RestClient client = new RestClient();

    private static BuildGenerator buildGenerator;

    public static String buildServerName = "Hudson"; //todo should be set to null

    public static void main(String[] args) throws JAXBException, IOException {
        if (args.length != 1 && args.length != 3) {
            System.out.println("Usage: java -jar data-generator.jar excel-configuration-file.xlsx [admin_user_name admin_password]");
            System.out.println("       admin_user_name and admin_password are optional");
            System.out.println("       they overwrite the settings from excel configuration file");
            System.out.println();
            System.exit(-1);
        }
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        long startTime = new Date().getTime();
        log.info("Starting at: "+sdf.format(new Date(startTime)));
        try {
            ExcelReader reader = new ExcelReader(args[0]);
            readUsers(reader);
            settings = new Settings(reader.getSheet("Settings"));
            if (args.length == 3) {
                User admin = User.getUser(settings.getAdmin());
                admin.setLogin(args[1]);
                admin.setPassword(args[2]);
            }

            DevBridgeDownloader downloader = null;
            if (settings.isGenerateProject()) {
                if (settings.getRestUrl().length() == 0 || settings.getTenantId().length() == 0) {
                    resolveTenantUrl();
                }
                log.debug("REST URL: " + settings.getRestUrl());
                log.debug("Tenant ID:" + settings.getTenantId());


                jobLog = new File("job-"+settings.getTenantId()+"-"+settings.getDomain()+"-"+settings.getProject()+".log");
                if (jobLog.exists()) {
                    log.info("Log from previous run found ("+jobLog.getName()+"), previously created data are going to be deleted...\n"+
                    "Type 'yes' and press <ENTER> if you wish to continue..."
                    );
                    BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
                    String input = in.readLine();
                    if (!input.equals("yes")) {
                        System.exit(-1);
                    }
                    downloader = downloadDevBridge();
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
                        try {
                            client.doDelete(settings.getRestUrl() + entityName + "s/" + agmId);
                        } catch (IllegalStateException e) {
                            log.error("Cannot delete "+entityName+" with ID: "+agmId);
                        }
                    }
                } else {
                    log.info("No log ("+jobLog.getName()+") from previous run found; first run against this tenant?");
                    downloader = downloadDevBridge();
                }
                if (settings.isAddUsers()) { // todo move this if out of [ if (settings.isGenerateProject()) ] condition
                    addUsers();
                }

                generateProject(reader);
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
        } finally {
            long endTime = new Date().getTime();
            log.info("Finished at: "+sdf.format(new Date(endTime)));
            long total = endTime - startTime;

            log.info(String.format("The generator ran for: %02d:%02d.%03d", total / 60000, (total%60000)/1000, total%100));
        }
    }

    private static void generateProject(ExcelReader reader) {
        log.info("Generating project data...");
        User adminUser = User.getUser(settings.getAdmin());
        if ("on-premise".equals(settings.getEnvironment())) {
            client.doGet(settings.getLoginUrl() + "?j_username=" + adminUser.getLogin() + "&j_password=" + adminUser.getPassword());
        } else if ("SaaS".equals(settings.getEnvironment())) {
            final String[][] data = {
                    { "username", adminUser.getLogin() },
                    { "password", adminUser.getPassword() }
            };
            client.doPost(settings.getLoginUrl(), data);
        } else {
            throw new IllegalStateException("Unknown environment type: "+settings.getEnvironment());
        }
        log.debug("Logged in to: " + settings.getLoginUrl());

        List<Sheet> entitySheets = reader.getAllEntitySheets();
        for (Sheet sheet : entitySheets) {
            String entityName = sheet.getSheetName();
            if ("apmuiservice".equals(entityName)) { //todo remove the string constant
                log.info("Moving backlog items to the created release...");
                generateEntity(reader, entityName, settings.getRestUrl()+entityName+"s/assignmentservice/planning");
            } else {
                log.info("Generating entity: "+entityName);
                generateEntity(reader, entityName, settings.getRestUrl()+entityName+"s?TENANTID="+settings.getTenantId());
            }
        }
        log.debug(idTranslationTable.toString());

    }

    private static Sheet readUsers(ExcelReader reader) {
        log.info("Reading list of users...");
        Sheet users = reader.getSheet("Users");
        EntityIterator iterator = new EntityIterator(users);
        while (iterator.hasNext()) {
            Entity userEntity = iterator.next();
            String id = EntityTools.getFieldValue(userEntity, "id");
            String login = EntityTools.getFieldValue(userEntity, "login");
            String password = EntityTools.getFieldValue(userEntity, "password");
            String firstName = EntityTools.getFieldValue(userEntity, "first name");
            String lastName = EntityTools.getFieldValue(userEntity, "last name");
            User user = new User(id, login, password, firstName, lastName);
            User.addUser(user);
        }
        return users;
    }

    static List<Long> readSkippedRevisions(Sheet sheet) {
        EntityIterator iterator = new EntityIterator(sheet);
        List<Long> revisions = new LinkedList<Long>();
        while (iterator.hasNext()) {
            Entity entity =  iterator.next();
            long revision = EntityTools.getFieldLongValue(entity, "revisions to skip");
            revisions.add(revision);
        }
        return revisions;
    }

    private static Map<String, String> idTranslationTable = new HashMap<String, String>();
    static File jobLog = null;

    public static void writeLogLine(String entityName, String entityId) {
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(jobLog, true));
            writer.write(entityName+": "+entityId+"\n");
            writer.flush();
            writer.close();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<String> logItems = new LinkedList<String>();
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

    private static void generateEntity(ExcelReader reader, String sheetName, String agmAddress) {
        Sheet sheet = reader.getSheet(sheetName);
        EntityIterator iterator = new EntityIterator(sheet);
        List<String> referenceColumns = iterator.getReferenceColumns();

        int firstReqId = 0;
        int firstDefId = 0;

        /**
         * Translates the IDs from excel to the ones used in AGM.
         */
        while (iterator.hasNext()) {
            Entity excelEntity = iterator.next();
            // remember ID
            Field idField = EntityTools.removeIdField(excelEntity);
            String excelId = idField.getValue().getValue();
            String originalEntityId = null;
            if ("apmuiservice".equals(sheetName)) {
                originalEntityId = EntityTools.getFieldValue(excelEntity, "id");
            }

            // replace with references
            for (String referenceId : referenceColumns) {
                Field field = EntityTools.getField(excelEntity, referenceId);
                if (field != null) { // skip translation if no id set (e.g. for root requirements)
                    Value value = field.getValue();
                    String excelValue = value.getValue();
                    String newValue;
                    if (excelValue.startsWith("Users#")) { // references to users are handled differently
                        //todo make it configurable
                        newValue = User.getUser(excelValue.substring("Users#".length())).getLogin();
                    } else {
                        newValue = idTranslationTable.get(excelValue);
                    }
                    if (newValue == null) {
                        log.error("Cannot translate as the value not found in table: "+excelValue);
                        // leave the original value
                    } else {
                        value.setValue(newValue); // set new value
                    }
                }
            }
            String agmId;
            if ("release-backlog-item".equals(sheetName)) {
                StringBuilder data = new StringBuilder();
                List<Field> fields = excelEntity.getFields().getField();
                String backlogId = fields.get(0).getValue().getValue();
                String sprintId = fields.get(1).getValue().getValue();
                String teamId = fields.get(2).getValue().getValue();
                String owner = fields.get(3).getValue().getValue();
                data.
                        append("{\"entities\":[{\"Fields\":[{\"Name\":\"id\", \"values\":[{\"value\":\"").
                        append(backlogId).
                        append("\"}]},{\"Name\":\"sprint-id\", \"values\":[{\"value\":\"").
                        append(sprintId).
                        append("\"}]},{\"Name\":\"team-id\", \"values\":[{\"value\":\"").
                        append(teamId).
                        append("\"}]},{\"Name\":\"owner\", \"values\":[{\"value\":\"").
                        append(owner).
                        append("\"}]}], \"Type\":\"release-backlog-item\"}], \"TotalResults\":1}");
                client.doPut(settings.getRestUrl() + "release-backlog-items", data.toString());
            } else if ("apmuiservice".equals(sheetName)) {
                RestClient.HttpResponse response = client.doRequest(agmAddress + EntityTools.toUrlParameters(excelEntity), (String) null, Method.POST, ContentType.JSON);
                String returnValue = response.getResponse();
                int startIndex = returnValue.indexOf("release-backlog-item_")+"release-backlog-item_".length();
                int lastIndex = returnValue.indexOf("\"", startIndex);
                agmId = returnValue.substring(startIndex, lastIndex);
                String key= sheetName+"#"+originalEntityId;
                String value = agmId;
                log.debug("Storing: "+key+"="+value);
                idTranslationTable.put(key, value);
            } else {
                if (sheetName.equals("release")) {
                // todo an evil hack ; remove it -> handlers can resolve it
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                    Field startDateField = EntityTools.getField(excelEntity, "start-date");
                    Field endDateField = EntityTools.getField(excelEntity, "end-date");
                    Date startDate = new Date(settings.getFirstBuildDate().getTime() + (Long.parseLong(startDateField.getValue().getValue()) * 24*60*60*1000));
                    Date endDate = new Date(startDate.getTime() + (Long.parseLong(endDateField.getValue().getValue()) * 24*60*60*1000));

                    startDateField.getValue().setValue(sdf.format(startDate));
                    log.debug("Setting start of the release to: "+sdf.format(startDate));
                    endDateField.getValue().setValue(sdf.format(endDate));
                    log.debug("Setting end of the release to: "+sdf.format(endDate));
                }
                if (sheetName.equals("build-server")) {
                    //todo an evil hack; remove it -> handles can resolve it
                   buildServerName = EntityTools.getField(excelEntity, "name").getValue().getValue();
                }
                String data = EntityTools.toXml(excelEntity);
                if (sheetName.equals("release-backlog-item")) {
                    client.doPut(agmAddress, data);
                } else {
                    RestClient.HttpResponse response = client.doPost(agmAddress, data);
                    Entity agmEntity = EntityTools.fromXml(response.getResponse());
                    agmId = EntityTools.getFieldValue(agmEntity, "id");
                    // todo an evil hack ; remove it -> handlers can resolve it
                    if (sheetName.equals("requirement") && firstReqId == 0) {   // remember req IDs
                        firstReqId = Integer.parseInt(agmId);
                        settings.setFirstRequirementNumber(firstReqId);
                        log.info("First requirement ID: "+firstReqId);
                    }
                    if (sheetName.equals("defect") && firstDefId == 0) {        // remember def IDs
                        firstDefId = Integer.parseInt(agmId);
                        settings.setFirstDefectNumber(firstDefId);
                        log.info("First defect ID: "+firstDefId);
                    }
                    idTranslationTable.put(sheetName+"#"+excelId, agmId);
                    writeLogLine(sheetName, agmId);
                    if (sheetName.equals("release")) {
                        learnSprints(agmId);
                    }
                }
            }
        }
    }

    public static void resolveTenantUrl() {
        log.info("Resolving Tenant ID, domain and project name...");
        User admin = User.getUser(settings.getAdmin());

        String loginUrl = settings.getLoginUrl().replace("https://", "http://");
        RestClient.HttpResponse response;
        String loginContext = null;
        try {
            response = client.doGet(loginUrl);
            loginUrl = response.getLocation().length() > 0 ? response.getLocation() : settings.getLoginUrl();
            loginContext = RestTools.extractString(response.getResponse(), "//div[@id='wrapper']/div[@class='container'][1]/form[@id='loginForm']/@action");
        } catch (IllegalStateException e) {
            log.debug(e);
            log.error("Incorrect credentials or URL: " + admin.getLogin() + " / " + admin.getPassword());
            System.exit(-1);
        }
        final String[][] data = {
                { "username", admin.getLogin() },
                { "password", admin.getPassword() }
        };
        response = client.doPost(RestTools.getProtocolHost(loginUrl)+loginContext, data);
        String agmUrl = null;
        String portalUrl = null;
        try {
            agmUrl = RestTools.extractString(response.getResponse(), "//div[@id='wrapper']/div[@class='container'][1]/div/a[1]/@href");
            portalUrl = RestTools.extractString(response.getResponse(), "//div[@id='wrapper']/div[@class='container'][1]/div/a[2]/@href");
        } catch (IllegalStateException e) {
            log.debug(e);
            log.error("Incorrect credentials or URL: " + admin.getLogin() + " / " + admin.getPassword());
            System.exit(-1);
        }
        response = client.doGet(agmUrl);

        Pattern p = Pattern.compile("^https?://([^/]+)/agm/webui/alm/([^/]+)/([^/]+)/apm/[^/]+/\\?TENANTID=(.+)$");
        Matcher m = p.matcher(response.getLocation());
        m.matches();
        settings.setHost(m.group(1));
        settings.setDomain(m.group(2));
        settings.setProject(m.group(3));
        settings.setTenantId(m.group(4));

        settings.setRestUrl("https://" + settings.getHost() + "/qcbin/rest/domains/" + settings.getDomain() + "/projects/" + settings.getProject() + "/");
        settings.setPortalUrl(RestTools.getProtocolHost(portalUrl));
        log.debug("Portal URL: " + portalUrl);
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
        final String[][] data = { { "bridge_url", settings.getAliDevBridgeUrl() } };

        RestClient.HttpResponse response = client.doRequest(settings.getRestUrl() + "scm/dev-bridge/deployment-url", data, Method.POST, ContentType.NONE);
        //todo check response code
    }

    public static void learnSprints(String releaseId) {
        log.info("Learning created sprints...");
        RestClient.HttpResponse response = client.doGet(settings.getRestUrl() + "release-cycles?query={parent-id[" + releaseId + "]}&order-by={start-date[ASC]}&page-size=2000&start-index=1");
        String xmlEntities = response.getResponse().substring("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>".length());
        Entities sprintEntities;
        try {
            final JAXBContext context = JAXBContext.newInstance(Entities.class);
            ByteArrayInputStream input = new ByteArrayInputStream(xmlEntities.getBytes());
            Unmarshaller u = context.createUnmarshaller();
            sprintEntities = (Entities) u.unmarshal(input);
        } catch (JAXBException e) {
            throw new IllegalStateException(e);
        }
        List<Entity> sprintList = sprintEntities.getEntity();
        int i = 1;
        for (Entity sprint : sprintList) {
            String id = EntityTools.getFieldValue(sprint, "id");
            idTranslationTable.put("sprint#"+i++, id);
            log.debug("Learning sprint id: "+id);
        }
    }

    public static void addUsers() {
        RestClient portalClient = new RestClient();
        User admin = User.getUser(settings.getAdmin());

        String[][] data = {
                { "username", admin.getLogin() },
                { "password", admin.getPassword() }
        };
        portalClient.doPost(settings.getLoginUrl(), data);
        portalClient.doGet(settings.getPortalUrl()+"/portal/pages/Dashboard?TENANTID=0");
        RestClient.HttpResponse response = portalClient.doGet(settings.getPortalUrl()+"/portal/pages/ListUsers2");
        String token = RestTools.extractString(response.getResponse(), "//div[@id='newUserDialog']/form[@id='createUserForm']/input[@name='struts.token']/@value");

        for (User user : User.getUsers()) {
            data = new String[][] {
                    { "firstName", user.getFirstName() },
                    { "lastName", user.getLastName() },
                    { "email", user.getLogin() },
                    { "phone", "1" },
                    { "portalUser", "off" },
                    { "timezone", "Europe/Prague" },
                    { "roles", "" },
                    { "struts.token.name", "struts.token" },
                    { "struts.token", token }
            };
            log.info("Adding user: " + user.getFirstName() + " " + user.getLastName() + ", " + user.getLogin());
            try {
                response = portalClient.doPost(settings.getPortalUrl()+"/portal/pages/CreateUser2", data);
                int i = response.getResponse().indexOf("token");
                i = response.getResponse().indexOf(':', i);
                i = response.getResponse().indexOf('"', i)+1;
                int lastI = response.getResponse().indexOf('"', i);
                token = response.getResponse().substring(i, lastI);
            } catch (IllegalStateException e) {
                // perhaps, it was not added as it already exists -> trying to attach it as an existing user account
                // todo handle it basing on the error stream value
                try {
                    log.debug("Trying to attach an existing user account: "+ user.getFirstName() + " " + user.getLastName() + ", " + user.getLogin());
                    data = new String[][] {
                            { "email", user.getLogin() },
                            { "struts.token.name", "struts.token" },
                            { "struts.token", token }
                    };
                    response = portalClient.doPost(settings.getPortalUrl()+"/portal/pages/AttachUserToAccount", data);
                    int i = response.getResponse().indexOf("token");
                    i = response.getResponse().indexOf(':', i);
                    i = response.getResponse().indexOf('"', i)+1;
                    int lastI = response.getResponse().indexOf('"', i);
                    token = response.getResponse().substring(i, lastI);
                } catch (IllegalStateException e2) {
                    log.error("Cannot add user to portal: "+user.getFirstName()+" "+user.getLastName());
                    response = portalClient.doGet(settings.getPortalUrl()+"/portal/pages/ListUsers2"); // todo the token is being returned in the error stream; rewrite it so you do not have to do this GET
                    token = RestTools.extractString(response.getResponse(), "//div[@id='newUserDialog']/form[@id='createUserForm']/input[@name='struts.token']/@value");
                }
            }
            try {
                //todo serialize using JSON library
                String formData = "{\"users\":[{\"loginName\":\""+user.getLogin()+
                        "\", \"firstName\":\""+user.getFirstName()+
                        "\", \"lastName\":\""+user.getLastName()+
                        "\", \"phone\":\"1\", \"email\":\""+user.getLogin()+
                        "\", \"timezone\":\"Europe/Prague\"}]}";
                //todo there should be a simpler way to learn the URL than using getProtocolHost method
                client.doPut(RestTools.getProtocolHost(settings.getRestUrl())+"/qcbin/rest/api/portal/users", formData);
            } catch (IllegalStateException e) {
                log.error("Cannot add user to project: "+user.getFirstName()+" "+user.getLastName());
            }
        }
    }

    public static DevBridgeDownloader downloadDevBridge() {
        DevBridgeDownloader downloader = new DevBridgeDownloader(settings, client);
        client.doPost(settings.getRestUrl()+"scm/dev-bridge/bundle", null, downloader);  // /scm/dev-bridge - downloads only war file!
        return downloader;
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
        try {
            File agentConfigFile = new File(settings.getSvnAgentFolder()+"\\config\\agent.xml");
            String agentConfig = FileUtils.readFileToString(agentConfigFile);                    //todo not the quickest solution but easy to understand
            agentConfig = agentConfig
                    .replace("<AGM_HOST/QCBIN_URL_HERE>", "https://" + settings.getHost() + "/qcbin")   //todo should parse as XML and replace XML nodes
                    .replace("<AGM_DOMAIN_HERE>", settings.getDomain())
                    .replace("<AGM_PROJECT_HERE>", settings.getProject())
                    .replace("<AGM_ADMIN_USER_NAME_HERE>", User.getUser(settings.getAdmin()).getLogin())
                    .replace("<AGM_ADMIN_PASSWORD_HERE>", User.getUser(settings.getAdmin()).getPassword());
            FileUtils.writeStringToFile(agentConfigFile, agentConfig);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
