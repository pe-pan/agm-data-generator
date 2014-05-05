package com.hp.demo.ali;

import com.hp.demo.ali.excel.ExcelReader;
import org.apache.log4j.Logger;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

/**
 * Created by panuska on 1/14/13.
 */
public class Settings {

    private static Logger log = Logger.getLogger(Settings.class.getName());
    private boolean generateProject;
    private String loginUrl;
    private String restUrl;
    private String tenantId;
    private String admin;
    private String aliDevBridgeUrl;
    private boolean alterRepository;
    private String svnUrl;
    private String svnUser;
    private boolean generateBuilds;
    private String hudsonUrl;
    private String jobName;
    private String templateJobName;
    private String buildFolder;
    private String hudsonFolder;
    private Date firstBuildDate;
    private int firstBuildNumber;
    private long firstSvnRevision;
    private int firstDefectNumber;
    private int firstRequirementNumber;
    private String portalUrl;
    private String aliDevBridgeFolder;
    private String svnAgentFolder;
    private boolean addUsers;
    private String domain;
    private String project;
    private boolean generateHistory;
    private String solutionName;
    private boolean forceDelete;
    private String instanceId;
    private String accountName;
    private String tenantUrl;
    private boolean deleteAll;
    private String hudsonServiceName;
    private String buildServerName;
    private String branchPath;
    private String updateUrl;

    private static DataFormatter formatter = new DataFormatter(true);

    private static Sheet sheet;
    private static FormulaEvaluator evaluator;
    private Settings(Sheet sheet) {
        this.sheet = sheet;
        this.evaluator = this.sheet.getWorkbook().getCreationHelper().createFormulaEvaluator();
    }

    public boolean isGenerateProject() {
        return generateProject;
    }

    public void setGenerateProject(boolean generateProject) {
        this.generateProject = generateProject;
    }

    public void setGenerateProject(String generateProject) {
        this.generateProject = "yes".equals(generateProject);
    }

    public String getLoginUrl() {
        return loginUrl;
    }

    public void setLoginUrl(String loginUrl) {
        log.debug("Setting Login URL: "+loginUrl);
        this.loginUrl = loginUrl;
    }

    public String getRestUrl() {
        return restUrl;
    }

    public void setRestUrl(String restUrl) {
        log.debug("Setting REST URL: "+restUrl);
        this.restUrl = restUrl;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        log.debug("Setting tenant ID: "+tenantId);
        this.tenantId = tenantId;
    }

    public String getAdmin() {
        return admin;
    }

    public String getAliDevBridgeUrl() {
        return aliDevBridgeUrl;
    }

    public boolean isAlterRepository() {
        return alterRepository;
    }

    public void setAlterRepository(boolean alterRepository) {
        this.alterRepository = alterRepository;
    }

    public void setAlterRepository(String alterRepository) {
        this.alterRepository = "yes".equals(alterRepository);
    }

    public void setMeldRepository(String meldRepository) {
        this.alterRepository = "yes".equals(meldRepository);
    }

    public String getSvnUrl() {
        return svnUrl;
    }

    public String getSvnUser() {
        return svnUser;
    }

    public boolean isGenerateBuilds() {
        return generateBuilds;
    }

    public void setGenerateBuilds(boolean generateBuilds) {
        this.generateBuilds = generateBuilds;
    }

    public void setGenerateBuilds(String generateBuilds) {
        this.generateBuilds = "yes".equals(generateBuilds);
    }

    public String getHudsonUrl() {
        return hudsonUrl;
    }

    public String getJobName() {
        return jobName;
    }

    public String getTemplateJobName() {
        return templateJobName;
    }

    public String getBuildFolder() {
        return buildFolder;
    }

    public String getHudsonFolder() {
        return hudsonFolder;
    }

    public Date getFirstBuildDate() {
        return firstBuildDate;
    }

    public int getFirstBuildNumber() {
        return firstBuildNumber;
    }

    public long getFirstSvnRevision() {
        return firstSvnRevision;
    }

    public int getFirstDefectNumber() {
        return firstDefectNumber;
    }

    public int getFirstRequirementNumber() {
        return firstRequirementNumber;
    }

    public void setFirstDefectNumber(int firstDefectNumber) {
        this.firstDefectNumber = firstDefectNumber;
    }

    public void setFirstDefectNumber(String firstDefectNumber) {
        try {
            this.firstDefectNumber = Integer.parseInt(firstDefectNumber);
        } catch (NumberFormatException e) {
            log.error("Cannot parse this string into an int: "+firstDefectNumber, e);
            this.firstDefectNumber = 0;
        }
    }

    public void setFirstRequirementNumber(int firstRequirementNumber) {
        this.firstRequirementNumber = firstRequirementNumber;
    }

    public void setFirstRequirementNumber(String firstRequirementNumber) {
        try {
            this.firstRequirementNumber = Integer.parseInt(firstRequirementNumber);
        } catch (NumberFormatException e) {
            log.error("Cannot parse this string into an int: "+firstRequirementNumber, e);
            this.firstRequirementNumber = 0;
        }
    }

    public String getPortalUrl() {
        return portalUrl;
    }

    public void setPortalUrl(String portalUrl) {
        log.debug("Setting portal URL: "+portalUrl);
        this.portalUrl = portalUrl;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public void setInstanceId(String instanceId) {
        log.debug("Setting instance ID: "+instanceId);
        this.instanceId = instanceId;
    }

    public String getAliDevBridgeFolder() {
        return aliDevBridgeFolder;
    }

    public String getSvnAgentFolder() {
        return svnAgentFolder;
    }

    public boolean isAddUsers() {
        return addUsers;
    }

    public void setAddUsers(boolean addUsers) {
        this.addUsers = addUsers;
    }

    public void setAddUsers(String addUsers) {
        this.addUsers = "yes".equals(addUsers);
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        log.debug("Setting domain: "+domain);
        this.domain = domain;
    }

    public String getProject() {
        return project;
    }

    public void setProject(String project) {
        log.debug("Setting project: "+project);
        this.project = project;
    }

    public boolean isGenerateHistory() {
        return generateHistory;
    }

    public void setGenerateHistory(boolean generateHistory) {
        this.generateHistory = generateHistory;
    }

    public void setGenerateHistory(String generateHistory) {
        this.generateHistory = "yes".equals(generateHistory);
    }

    public String getSolutionName() {
        return solutionName;
    }

    public void setSolutionName(String solutionName) {
        this.solutionName = solutionName;
    }

    public boolean isForceDelete() {
        return forceDelete;
    }

    public void setForceDelete(boolean forceDelete) {
        this.forceDelete = forceDelete;
    }

    public void setForceDelete(String forceDelete) {
        this.forceDelete = "yes".equals(forceDelete);
    }

    public String getTenantUrl() {
        return tenantUrl;
    }

    public void setTenantUrl(String tenantUrl) {
        this.tenantUrl = tenantUrl;
    }

    public boolean isDeleteAll() {
        return deleteAll;
    }

    public void setDeleteAll(boolean deleteAll) {
        this.deleteAll = deleteAll;
    }

    public void setDeleteAll(String deleteAll) {
        this.deleteAll = "yes".equals(deleteAll);
    }

    public String getHudsonServiceName() {
        return hudsonServiceName;
    }

    public void setHudsonServiceName(String hudsonServiceName) {
        this.hudsonServiceName = hudsonServiceName;
    }

    public String getBuildServerName() {
        return buildServerName;
    }

    public void setBuildServerName(String buildServerName) {
        this.buildServerName = buildServerName;
    }

    public String getBranchPath() {
        return branchPath;
    }

    public void setBranchPath(String branchPath) {
        this.branchPath = branchPath;
    }

    public void setAdmin(String admin) {
        this.admin = admin;
    }

    public void setAliDevBridgeUrl(String aliDevBridgeUrl) {
        this.aliDevBridgeUrl = aliDevBridgeUrl;
    }

    public void setSvnUrl(String svnUrl) {
        this.svnUrl = svnUrl;
    }

    public void setSvnUser(String svnUser) {
        this.svnUser = svnUser;
    }

    public void setHudsonUrl(String hudsonUrl) {
        this.hudsonUrl = hudsonUrl;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public void setTemplateJobName(String templateJobName) {
        this.templateJobName = templateJobName;
    }

    public void setBuildFolder(String buildFolder) {
        this.buildFolder = buildFolder;
    }

    public void setHudsonFolder(String hudsonFolder) {
        this.hudsonFolder = hudsonFolder;
    }

    public void setAliDevBridgeFolder(String aliDevBridgeFolder) {
        this.aliDevBridgeFolder = aliDevBridgeFolder;
    }

    public void setSvnAgentFolder(String svnAgentFolder) {
        this.svnAgentFolder = svnAgentFolder;
    }

    public void setFirstBuildDate(Date firstBuildDate) {
        this.firstBuildDate = firstBuildDate;
    }

    public void setFirstBuildDate(String firstBuildDate) {
        try {
            long days = Long.parseLong(firstBuildDate);
            long currentTimeMillis = settings.isDeleteAll() ? System.currentTimeMillis() - 14 * 24*60*60*1000 : System.currentTimeMillis();
 //           long currentTimeMillis = System.currentTimeMillis() - 17 * 24*60*60*1000;
            this.firstBuildDate = new Date(currentTimeMillis + days*24*60*60*1000);
            log.debug("Setting first build date to: "+this.firstBuildDate.toString());
        } catch (NumberFormatException e) {
            log.debug("First build date is not a relative number to todays'; trying if it's an absolute date");
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");
            try {
                this.firstBuildDate = sdf.parse(firstBuildDate);
                log.debug("First build date is absolute: "+this.firstBuildDate.toString());
            } catch (ParseException e1) {
                log.error("FirstBuildDate is not a relative number to todays'; nor it is an absolute date in this format: "+sdf.toPattern());
                this.firstBuildDate = new Date();
                log.debug("Setting to now: "+this.firstBuildDate.toString());
            }
        }
    }

    public void setFirstBuildNumber(String firstBuildNumber) {
        try {
            this.firstBuildNumber = Integer.parseInt(firstBuildNumber);
        } catch (NumberFormatException e) {
            log.error("Cannot parse this string into an int: "+firstBuildNumber, e);
            this.firstBuildNumber = 0;
        }
    }

    public void setFirstSvnRevision(String firstSvnRevision) {
        try {
            this.firstSvnRevision = Long.parseLong(firstSvnRevision);
        } catch (NumberFormatException e) {
            log.error("Cannot parse this string into a long: "+firstSvnRevision, e);
            this.firstSvnRevision = 0;
        }
    }

    public String getUpdateUrl() {
        return updateUrl;
    }

    public void setUpdateUrl(String updateUrl) {
        this.updateUrl = updateUrl;
    }

    private static Settings settings = null;
    public static void initSettings(ExcelReader reader) {
        Sheet sheet = reader.getSheet("Settings");
        Settings.settings = new Settings(sheet);
        log.info("Reading settings...");

        // initialize from Excel file
        for (Row row : sheet) {
            Cell cell = row.getCell(1);
            if (cell == null) continue;  // skip an empty row
            cell = evaluator.evaluateInCell(cell);
            String propertyName = formatter.formatCellValue(cell).trim();
            if (propertyName.length() == 0) continue; // skip an empty row

            cell  = row.getCell(2);
            if (cell == null) {
                log.error("No value found for property called "+propertyName);
                continue;
            }
            cell  = evaluator.evaluateInCell(cell);
            String propertyValue = formatter.formatCellValue(cell).trim();

            setProperty(propertyName, propertyValue);
        }

        // initialize from text file
        File file = new File(Migrator.CONF_DIR, Migrator.SETTINGS_PROPERTIES_FILE);
        if (file.exists()) {
            Properties properties = new Properties();
            try {
                log.debug("Settings properties file found, loading");
                properties.load(new FileInputStream(file));
            } catch (IOException e) {
                log.error("Cannot open file: "+file.getAbsolutePath(), e);
                return;
            }
            for (String propertyName : properties.stringPropertyNames()) {
                String propertyValue = properties.getProperty(propertyName);
                setProperty(propertyName, propertyValue);
                setExcelProperty(propertyName, propertyValue);
            }
        }
    }

    private static void setExcelProperty(String propertyName, String propertyValue) {
        for (Row row : sheet) {
            for (Cell cell : row) {
                cell  = evaluator.evaluateInCell(cell);
                String value = formatter.formatCellValue(cell).trim();
                if (propertyName.equals(value)) {
                    Cell newCell = row.getCell(cell.getColumnIndex()+1);  //on the very same row, take the next cell
                    if (newCell == null) {
                        newCell = row.createCell(cell.getColumnIndex()+1);
                    }
                    newCell.setCellValue(propertyValue);             //and set its value
                }
            }
        }
    }

    private static void setProperty(String propertyName, String propertyValue) {
        String methodName = "set"+propertyName;
        try {
            log.debug("Calling " + methodName + "(" + propertyValue + ")");
            Method method = Settings.class.getMethod(methodName, String.class);
            if (method != null) {
                method.invoke(settings, propertyValue);
            } else {
                log.error("On "+Settings.class.getSimpleName()+" class, this method does not exist: "+methodName);
            }
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            log.error("On "+Settings.class.getSimpleName()+" class, cannot call the method "+methodName, e);
        }
    }

    public static Settings getSettings() {
        if (settings == null) {
            log.error("Settings not initialized!");
            throw new IllegalStateException("Settings not initialized!");
        }
        return settings;
    }

}
