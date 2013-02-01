package com.hp.demo.ali;

import com.hp.demo.ali.entity.*;
import com.hp.demo.ali.excel.EntityIterator;
import com.hp.demo.ali.excel.ExcelReader;
import com.hp.demo.ali.rest.RestHelper;
import com.hp.demo.ali.tools.EntityTools;
import org.apache.log4j.Logger;
import org.apache.poi.ss.usermodel.Sheet;

import javax.xml.bind.JAXBException;
import java.io.FileNotFoundException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by panuska on 10/12/12.
 */
public class DataGenerator {

    private static Logger log = Logger.getLogger(RestHelper.class.getName());

    private static Settings settings;

    public static void main(String[] args) throws JAXBException, FileNotFoundException {
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

            if (settings.isGenerateProject()) {
                if (settings.getRestUrl().length() == 0 || settings.getTenantId().length() == 0) {
                    resolveTenantUrl();
                }
                log.debug("REST URL: " + settings.getRestUrl());
                log.debug("Tenant ID:" + settings.getTenantId());
                generateProject(reader);
            }
            if (settings.isGenerateBuilds()) {
                List<Long>skippedRevisions = readSkippedRevisions(reader.getSheet("Skip-Revisions"));
                BuildGenerator generator = new BuildGenerator(settings);
                generator.generate(reader.getSheet("Builds"), skippedRevisions);
                generator.createJob();
            }
            if (settings.isGenerateProject() && settings.isGenerateBuilds()) {
                configureAliDevBridge();
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
            RestHelper.Login(adminUser.getLogin(), adminUser.getPassword(), settings.getLoginUrl());
        } else if ("SaaS".equals(settings.getEnvironment())) {
            RestHelper.LoginSaaS(adminUser.getLogin(), adminUser.getPassword(), settings.getLoginUrl());
        } else {
            throw new IllegalStateException("Unknown environment type: "+settings.getEnvironment());
        }

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
            User user = new User(id, login, password);
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
            if ("apmuiservice".equals(sheetName)) {
                agmId = RestHelper.moveEntity(excelEntity, agmAddress);
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
                Entity agmEntity = RestHelper.postEntity(excelEntity, agmAddress);
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
            }
            idTranslationTable.put(sheetName+"#"+excelId, agmId);
        }
    }

    public static void resolveTenantUrl() {
        log.info("Resolving Tenant ID, domain and project name...");
        HashMap<String, String> data = new HashMap<String, String>();
        User admin = User.getUser(settings.getAdmin());
        data.put("username", admin.getLogin());
        data.put("password", admin.getPassword());

        RestHelper.HttpResponse response = RestHelper.postData("https://gateway.saas.hp.com/msg/actions/doLogin.action", data);
        String url = null;
        try {
            url = RestHelper.extractString(response.getResponse(), "//div[@id='wrapper']/div[@class='container'][1]/div/a[1]/@href");
        } catch (IllegalStateException e) {
            log.debug(e);
            log.error("Incorrect credentials: " + admin.getLogin() + " / " + admin.getPassword());
            System.exit(-1);
        }

        response = RestHelper.postData(url, null);
        url = RestHelper.extractString(response.getResponse(), "/html/body/p[2]/a/@href");
        String[] tokens = url.split("[/=&]");

        settings.setRestUrl("https://agilemanager-int.saas.hp.com/qcbin/rest/domains/"+tokens[4]+"/projects/"+tokens[5]+"/");
        settings.setTenantId(tokens[9]);
    }

    public static void synchronizeAliDevBridge() {
        log.debug("Synchronizing builds and source code changes");

        HashMap<String, String> data = new HashMap<String, String>(2);
        User admin = User.getUser(settings.getAdmin());
        data.put("j_username", admin.getLogin());
        data.put("j_password", admin.getPassword());
        data.put("a", "");

        RestHelper.HttpResponse response = RestHelper.postData(settings.getAliDevBridgeUrl()+"/j_spring_security_check", data);

        String script = RestHelper.extractString(response.getResponse(), "/html/head/script[2]/text()");

        String token = "\"fid\""; // after this token is the value in " " characters
        String scriptEnd = script.substring(script.indexOf(token)+token.length());
        int first = scriptEnd.indexOf('"')+1;
        int last = scriptEnd.indexOf('"', first);
        String fid = scriptEnd.substring(first, last);

        data = new HashMap<String, String>(1);
        data.put("fid", fid);
        RestHelper.postData(settings.getAliDevBridgeUrl()+"/rest/task/start/BuildSyncTask", data);
        log.info("Build synchronization started!");
        RestHelper.postData(settings.getAliDevBridgeUrl()+"/rest/task/start/SourceSyncTask", data);
        log.info("Source code synchronization started!");
    }

    public static void configureAliDevBridge() {
        log.info("Configuring ALI Dev Bridge...");
        HashMap<String, String> data = new HashMap<String, String>();
        data.put("bridge_url", settings.getAliDevBridgeUrl());

        RestHelper.HttpResponse response = RestHelper.postData(settings.getRestUrl() + "scm/dev-bridge/deployment-url", data);
        //todo check response code
    }
}
