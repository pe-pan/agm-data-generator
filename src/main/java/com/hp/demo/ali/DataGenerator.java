package com.hp.demo.ali;

import com.hp.demo.ali.entity.*;
import com.hp.demo.ali.excel.EntityIterator;
import com.hp.demo.ali.excel.ExcelReader;
import com.hp.demo.ali.rest.RestHelper;
import com.hp.demo.ali.svn.RepositoryMender;
import com.hp.demo.ali.tools.EntityTools;
import org.apache.log4j.Logger;
import org.apache.poi.ss.usermodel.Sheet;

import javax.xml.bind.JAXBException;
import java.io.FileNotFoundException;
import java.util.*;

/**
 * Created by panuska on 10/12/12.
 */
public class DataGenerator {

    private static Logger log = Logger.getLogger(RestHelper.class.getName());

    private static Settings settings;
    private static String restUrl;
    private static String tenantId;

    public static void main(String[] args) throws JAXBException, FileNotFoundException {
        if (args.length != 1 && args.length != 3) {
            System.out.println("Usage: java -jar data-generator.jar excel-configuration-file.xlsx [admin_user_name admin_password]");
            System.out.println("       admin_user_name and admin_password are optional");
            System.out.println("       they overwrite the settings from excel configuration file");
            System.out.println();
            System.exit(-1);
        }

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
            } else {
                restUrl = settings.getRestUrl();
                tenantId = settings.getTenantId();
                log.info("");
            }
            log.debug("REST URL: " + restUrl);
            log.debug("Tenant ID:" + tenantId);
            generateProject(reader);
        }
        if (settings.isGenerateBuilds()) {
            List<Long>skippedRevisions = readSkippedRevisions(reader.getSheet("Skip-Revisions"));
            BuildGenerator generator = new BuildGenerator(settings);
            generator.generate(reader.getSheet("Builds"), skippedRevisions);
            generator.createJob();
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
                generateEntity(reader, entityName, restUrl+entityName+"s/assignmentservice/planning");
            } else {
                log.info("Generating entity: "+entityName);
                generateEntity(reader, entityName, restUrl+entityName+"s?TENANTID="+tenantId);
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
        HashMap<String, String> data = new HashMap<String, String>();
        User admin = User.getUser(settings.getAdmin());
        data.put("username", admin.getLogin());
        data.put("password", admin.getPassword());

        RestHelper.HttpResponse response = RestHelper.postData("https://gateway.saas.hp.com/msg/actions/doLogin.action", data, null);
        String url = null;
        try {
            url = RestHelper.extractString(response.getResponse(), "//div[@id='wrapper']/div[@class='container'][1]/div/a[1]/@href");
        } catch (IllegalStateException e) {
            log.debug(e);
            log.error("Incorrect credentials: " + admin.getLogin() + " / " + admin.getPassword());
            System.exit(-1);
        }

        response = RestHelper.postData(url, null, response.getCookie());
        url = RestHelper.extractString(response.getResponse(), "/html/body/p[2]/a/@href");
        String[] tokens = url.split("[/=&]");

        restUrl = "https://agilemanager-int.saas.hp.com/qcbin/rest/domains/"+tokens[4]+"/projects/"+tokens[5]+"/";
        tenantId = tokens[9];
    }

}
