package com.hp.demo.ali;

import com.hp.demo.ali.excel.AgmEntityIterator;
import com.hp.demo.ali.rest.AgmClient;
import com.hp.demo.ali.entity.User;
import com.hp.demo.ali.excel.EntityIterator;
import com.hp.demo.ali.excel.ExcelReader;
import com.hp.demo.ali.rest.DevBridgeDownloader;
import com.hp.demo.ali.rest.RestClient;
import com.hp.demo.ali.rest.RestTools;
import com.hp.demo.ali.tools.EntityTools;
import org.apache.ant.compress.taskdefs.Unzip;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.apache.poi.ss.usermodel.Sheet;
import org.hp.almjclient.connection.ServiceResourceAdapter;
import org.hp.almjclient.exceptions.ALMRestException;
import org.hp.almjclient.exceptions.EntityNotFoundException;
import org.hp.almjclient.exceptions.FieldNotFoundException;
import org.hp.almjclient.exceptions.RestClientException;
import org.hp.almjclient.model.marshallers.Entities;
import org.hp.almjclient.model.marshallers.Entity;
import org.hp.almjclient.model.marshallers.favorite.Filter;
import org.hp.almjclient.services.ConnectionService;
import org.hp.almjclient.services.EntityCRUDService;
import org.hp.almjclient.services.impl.ConnectionManager;

import javax.xml.bind.JAXBException;
import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by panuska on 10/12/12.
 */
public class DataGenerator {

    private static Logger log = Logger.getLogger(RestClient.class.getName());

    private static Settings settings;
    private static AgmClient agmClient = AgmClient.getAgmClient();

    private static org.hp.almjclient.services.impl.ProjectServicesFactory factory;
    private static BuildGenerator buildGenerator;

    public static String buildServerName;
    public static Date releaseStartDate;

    private static final int CONNECTION_TIMEOUT = 600000;

    public static void main(String[] args) throws JAXBException, IOException {
        if (args.length != 1 && args.length != 3) {
            System.out.println("Usage: java -jar data-generator.jar excel-configuration-file.xlsx [admin_user_name admin_password]");
            System.out.println("       admin_user_name and admin_password are optional");
            System.out.println("       they overwrite the settings from excel configuration file");
            System.out.println();
            System.exit(-1);
        }
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        long startTime = System.currentTimeMillis();
        log.info("Starting at: "+sdf.format(new Date(startTime)));
        try {
            ExcelReader reader = new ExcelReader(args[0]);
            readUsers(reader);
            Settings.initSettings(reader.getSheet("Settings"));
            settings = Settings.getSettings();
            if (args.length == 3) {
                User admin = User.getUser(settings.getAdmin());
                admin.setLogin(args[1]);
                admin.setPassword(args[2]);
            }

            resolveTenantUrl();
            final ConnectionService connection = ConnectionManager.getConnection(CONNECTION_TIMEOUT, null, null);
            connection.setTenantId(Integer.parseInt(settings.getTenantId()));
            User admin = User.getUser(settings.getAdmin());
            connection.connect(settings.getRestUrl(), admin.getLogin(), admin.getPassword());
            factory = connection.getProjectServicesFactory(settings.getDomain(), settings.getProject());

            DevBridgeDownloader downloader = null;
            if (settings.isGenerateProject()) {
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
                    downloader = agmClient.downloadDevBridge();
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
                        EntityCRUDService service = factory.getEntityCRUDService(entityName);
                        try {
                            service.delete(Integer.parseInt(agmId));
                        } catch (EntityNotFoundException e) {
                            log.error("Cannot delete "+entityName+" with ID: "+agmId);
                        }
                    }
                } else {
                    log.info("No log ("+jobLog.getName()+") from previous run found; first run against this tenant?");
                    downloader = agmClient.downloadDevBridge();
                }
                if (settings.isAddUsers()) { // todo move this if out of [ if (settings.isGenerateProject()) ] condition
                    addUsers();
                }

                generateProject(reader);
            }
            if (settings.isGenerateHistory()) {
                HistoryGenerator historyGenerator = new HistoryGenerator(reader.getSheet("Work"), factory); //todo History is a reserved name in Excel
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
        } catch (RestClientException e) {
            throw new IllegalStateException(e);
        } catch (ALMRestException e) {
            throw new IllegalStateException(e);
        } finally {
            long endTime = System.currentTimeMillis();
            log.info("Finished at: "+sdf.format(new Date(endTime)));
            long total = endTime - startTime;

            log.info(String.format("The generator ran for: %02d:%02d.%03d", total / 60000, (total%60000)/1000, total%100));
        }
    }

    private static void generateProject(ExcelReader reader) {
        log.info("Generating project data...");
        List<Sheet> entitySheets = reader.getAllEntitySheets();
        for (Sheet sheet : entitySheets) {
            String entityName = sheet.getSheetName();
            generateEntity(reader, entityName);
        }
        AgmEntityIterator.logReferences();
    }

    private static Sheet readUsers(ExcelReader reader) {
        log.info("Reading list of users...");
        Sheet users = reader.getSheet("Users");
        EntityIterator<com.hp.demo.ali.entity.Entity> iterator = new EntityIterator<com.hp.demo.ali.entity.Entity>(users);
        while (iterator.hasNext()) {
            com.hp.demo.ali.entity.Entity userEntity = iterator.next();
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
        EntityIterator<com.hp.demo.ali.entity.Entity> iterator = new EntityIterator<com.hp.demo.ali.entity.Entity>(sheet);
        List<Long> revisions = new LinkedList<Long>();
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

    private static Entities sprintList = null;  //team-member must be created after release
    private static Map<String, String> featureMap = new HashMap<String, String>();

    private static void generateEntity(ExcelReader reader, String sheetName) {
        log.info("Generating entity: "+sheetName);
        Sheet sheet = reader.getSheet(sheetName);
        AgmEntityIterator<Entity> iterator = new AgmEntityIterator<Entity>(sheet);

        int firstReqId = 0;
        int firstDefId = 0;

        /**
         * Translates the IDs from excel to the ones used in AGM.
         */
        try {
            while (iterator.hasNext()) {
                Entity excelEntity = iterator.next();
                String agmId = null;
                if ("release-backlog-item".equals(sheetName)) {
                    EntityCRUDService CRUDservice = factory.getEntityCRUDService("release-backlog-item");
                    CRUDservice.update(excelEntity);
                } else {
                    if (sheetName.equals("release")) {
                    // todo an evil hack ; remove it -> handlers can resolve it
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

                        releaseStartDate = new Date(settings.getFirstBuildDate().getTime() + (Long.parseLong(excelEntity.getFieldValue("start-date").getValue()) * 24*60*60*1000));
                        log.debug("Setting start of the release to: "+sdf.format(releaseStartDate));
                        excelEntity.setFieldValue("start-date", sdf.format(releaseStartDate));

                        Date endDate = new Date(releaseStartDate.getTime() + (Long.parseLong(excelEntity.getFieldValue("end-date").getValue()) * 24*60*60*1000));
                        log.debug("Setting end of the release to: "+sdf.format(endDate));
                        excelEntity.setFieldValue("end-date", sdf.format(endDate));
                    }
                    if (sheetName.equals("build-server")) {
                        //todo an evil hack; remove it -> handles can resolve it
                       buildServerName = excelEntity.getFieldValue("name").getValue();
                    }

                    EntityCRUDService CRUDservice = factory.getEntityCRUDService(sheetName);
                    Entity response = CRUDservice.create(excelEntity);
                    agmId = response.getId().toString();
                    iterator.putReferencePrefix(sheetName + "#", agmId);
                    writeLogLine(sheetName, agmId);

                    if (sheetName.equals("requirement")) {
                      // todo an evil hack ; remove it -> handlers can resolve it

                        Filter filter = new Filter("release-backlog-item");
                        filter.addQueryClause("entity-id", agmId);
                        CRUDservice = factory.getEntityCRUDService("release-backlog-item");
                        Entities entities = CRUDservice.readCollection(filter);
                        Entity backlogItem = entities.getEntityList().get(0);
                        String backlogItemId = backlogItem.getId().toString();
                        String typeId = excelEntity.getFieldValue("type-id").getValue();
                        if ("71".equals(typeId)) { // feature
                            String themeId = excelEntity.getFieldValue("parent-id").getValue();
                            if (themeId != null) {
                                featureMap.put(agmId, themeId);                                            // remember theme ID
                            }
                            backlogItem.setFieldValue("theme-id", themeId);
                            CRUDservice.update(backlogItem);                                           // update backlog item
                        } else if ("70".equals(typeId)) { // user story
                            String featureId = excelEntity.getFieldValue("parent-id").getValue();
                            String themeId = featureMap.get(featureId);                                // get theme ID
                            backlogItem.setFieldValue("feature-id", featureId);
                            backlogItem.setFieldValue("theme-id", themeId);
                            CRUDservice.update(backlogItem);                                           // update backlog item
                        }
                        if (firstReqId == 0) {   // remember req IDs
                            firstReqId = Integer.parseInt(agmId);
                            settings.setFirstRequirementNumber(firstReqId);
                            log.info("First requirement ID: "+firstReqId);
                        }
                        iterator.putReferencePrefix("apmuiservice#" + sheetName + "#", backlogItemId);
                    } else if (sheetName.equals("defect")) {
                        Filter filter = new Filter("release-backlog-item");
                        filter.addQueryClause("entity-id", agmId);
                        CRUDservice = factory.getEntityCRUDService("release-backlog-item");
                        Entities entities = CRUDservice.readCollection(filter);
                        Entity backlogItem = entities.getEntityList().get(0);
                        String backlogItemId = backlogItem.getId().toString();
                        iterator.putReferencePrefix("apmuiservice#" + sheetName + "#", backlogItemId);

                        filter = new Filter("project-task");                                 // delete the automatically created task
                        filter.addQueryClause("release-backlog-item-id", backlogItemId);
                        CRUDservice = factory.getEntityCRUDService("project-task");
                        Entities tasks = CRUDservice.readCollection(filter);
                        List<Integer> taskIds = new ArrayList<Integer>(tasks.getEntityList().size());
                        for (Entity entity : tasks.getEntityList()) {
                            taskIds.add(entity.getId());
                        }
                        log.debug("Deleting all the by default created tasks. In total: "+taskIds.size());
                        if (taskIds.size() > 0) {
                            CRUDservice.delete("project-task", taskIds, true);
                        }

                        if (firstDefId == 0) {
                            // remember def IDs
                            firstDefId = Integer.parseInt(agmId);
                            settings.setFirstDefectNumber(firstDefId);
                            log.info("First defect ID: "+firstDefId);
                        }
                    } else if (sheetName.equals("release")) {
                        // todo an evil hack ; remove it -> handlers can resolve it
                        sprintList = getSprints(agmId);
                        learnSprints(sprintList);
                    }
                }
            }
            if (sheetName.equals("team-member")) {  // once team-members are created, initialize sprints
                initializeSprints(sprintList);
            }
        } catch (RestClientException e) {
            throw new IllegalStateException(e);
        } catch (ALMRestException e) {
            throw new IllegalStateException(e);
        }
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
            System.exit(-1);
            throw e;        //will never be executed
        }
        settings.setHost(tenantProperties[0]);
        settings.setDomain(tenantProperties[1]);
        settings.setProject(tenantProperties[2]);
        settings.setTenantId(tenantProperties[3]);
        settings.setRestUrl(tenantProperties[4]);
        settings.setPortalUrl(tenantProperties[5]);
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

        ServiceResourceAdapter adapter = factory.getServiceResourceAdapter();
        try {
            Map<String, String> headers = new HashMap<String, String>(1);
            headers.put("INTERNAL_DATA", "20101021");
            adapter.addSessionCookie("STATE="+"20101021");
            adapter.postWithHeaders(String.class, factory.getProjectRestMetaData().getCollectionBaseUrl() + "scm/dev-bridge/deployment-url", "bridge_url=" + settings.getAliDevBridgeUrl(), headers, ServiceResourceAdapter.ContentType.NONE);
        } catch (RestClientException e) {
            throw new IllegalStateException(e);
        } catch (ALMRestException e) {
            throw new IllegalStateException(e);
        }
        //todo check response code
    }

    private static Entities getSprints(String releaseId) throws ALMRestException, RestClientException {
        log.debug("Getting sprints of release " + releaseId + " ...");
        EntityCRUDService CRUDservice = factory.getEntityCRUDService("release-cycle");
        Filter filter = new Filter("release-cycle");
        filter.addQueryClause("parent-id", releaseId);
        Entities entities;
        entities = CRUDservice.readCollection(filter);
        return entities;
    }

    public static void learnSprints(Entities sprintList) throws FieldNotFoundException {
        log.info("Learning created sprints...");
        int i = 1;
        for (Entity sprint : sprintList.getEntityList()) {
            String id = sprint.getId().toString();
            AgmEntityIterator.putReference("sprint#", i++, id);
            log.debug("Learning sprint id: "+id);
        }
    }

    public static void initializeSprints(Entities entities) throws RestClientException, ALMRestException {
        log.info("Initializing historical sprints...");
        // moving sprints from past to current and back will cause that All existing teams will be assigned to such sprints
        // such past sprints can be then assigned user stories and defect
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        for (Entity sprint : entities.getEntityList()) {
            String id = sprint.getId().toString();
            log.debug("Initializing sprint id: "+id);

            String tense = sprint.getFieldValue("tense").getValue();
            assert tense != null;
            if ("PAST".equals(tense)) {
                log.debug("Moving the sprint "+id+" to current and back again (to reset assigned teams)");
                long now = System.currentTimeMillis();
                long startDate;
                long endDate;
                try {
                    startDate = sdf.parse(sprint.getFieldValue("start-date").getValue()).getTime();
                    endDate = sdf.parse(sprint.getFieldValue("end-date").getValue()).getTime();
                } catch (ParseException e) {
                    throw new IllegalStateException(e);
                }
                long diff = now - startDate;

                assert diff > 0;
                EntityCRUDService CRUDservice = factory.getEntityCRUDService("release-cycle");
                Map<String, Object> fields = new HashMap<String, Object>(3);
                fields.put("id", sprint.getId());
                fields.put("start-date", sdf.format(new Date(startDate + diff)));
                fields.put("end-date", sdf.format(new Date(endDate + diff)));
                Entity updatedSprint = new Entity("release-cycle", fields);
                CRUDservice.update(updatedSprint);

                updatedSprint.setFieldValue("start-date", sdf.format(new Date(startDate)));
                updatedSprint.setFieldValue("end-date", sdf.format(new Date(endDate)));
                CRUDservice.update(updatedSprint);
            }
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
                ServiceResourceAdapter adapter = factory.getServiceResourceAdapter();
                Map<String, String> headers = new HashMap<String, String>(1);
                headers.put("INTERNAL_DATA", "20120922");
                adapter.addSessionCookie("STATE="+"20120922");
                adapter.putWithHeaders(String.class, settings.getRestUrl()+"/rest/api/portal/users", formData, headers, ServiceResourceAdapter.ContentType.JSON);
            } catch (ALMRestException e) {
                log.error("Cannot add user to project: "+user.getFirstName()+" "+user.getLastName());
            } catch (RestClientException e) {
                log.error("Cannot add user to project: "+user.getFirstName()+" "+user.getLastName());
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
