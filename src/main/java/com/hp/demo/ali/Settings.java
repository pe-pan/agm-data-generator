package com.hp.demo.ali;

import com.hp.demo.ali.tools.SheetTools;
import org.apache.log4j.Logger;
import org.apache.poi.ss.usermodel.Sheet;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by panuska on 1/14/13.
 */
public class Settings {

    private static Logger log = Logger.getLogger(Settings.class.getName());
    private boolean generateProject;
    private String environment;
    private String loginUrl;
    private String restUrl;
    private String tenantId;
    private String admin;
    private String aliDevBridgeUrl;
    private boolean meldRepository;
    private String svnUrl;
    private String svnUser;
    private boolean generateBuilds;
    private String hudsonUrl;
    private String jobName;
    private String templateJobName;
    private String buildFolder;
    private String buildTemplateFolder;
    private Date firstBuildDate;
    private int firstBuildNumber;
    private long firstSvnRevision;
    private int firstDefectNumber;
    private int firstRequirementNumber;
    private String portalUrl;
    private String devBridgeHome;
    private String devBridgeFolder;
    private boolean addUsers;

    public Settings(Sheet settings) {
        log.info("Reading settings...");
        generateProject = "yes".equals(SheetTools.getStringValue(settings, 2, 2));
        environment = SheetTools.getStringValue(settings, 3, 2);
        loginUrl = SheetTools.getStringValue(settings, 4, 2);
        restUrl = SheetTools.getStringValue(settings, 5, 2);
        tenantId = SheetTools.getStringValue(settings, 6, 2);
        admin = SheetTools.getStringValue(settings, 7, 2);
        meldRepository = "yes".equals(SheetTools.getStringValue(settings, 8, 2));
        svnUrl = SheetTools.getStringValue(settings, 9, 2);
        svnUser = SheetTools.getStringValue(settings, 10, 2);
        generateBuilds = "yes".equals(SheetTools.getStringValue(settings, 11, 2));
        hudsonUrl = SheetTools.getStringValue(settings, 12, 2);
        jobName = SheetTools.getStringValue(settings, 13, 2);
        templateJobName = SheetTools.getStringValue(settings, 14, 2);
        buildFolder = SheetTools.getStringValue(settings, 15, 2);
        buildTemplateFolder = SheetTools.getStringValue(settings, 16, 2);
        try {
            long days = SheetTools.getLongValue(settings, 17, 2);
            firstBuildDate = new Date(new Date().getTime() + days*24*60*60*1000);
            log.debug("Setting first build date to: "+firstBuildDate.toString());
        } catch (NumberFormatException e) {
            log.debug("First build date is not a relative number to todays'; trying if it's absolute date");
            firstBuildDate = SheetTools.getDateValue(settings, 17, 2, new SimpleDateFormat("dd/MM/yyyy HH:mm"));
            log.debug("First build date is absolute: "+firstBuildDate.toString());
        }
        firstBuildNumber = SheetTools.getIntValue(settings, 18, 2);
        firstSvnRevision = SheetTools.getLongValue(settings, 19, 2);
        firstDefectNumber = SheetTools.getIntValue(settings, 20, 2);
        firstRequirementNumber = SheetTools.getIntValue(settings, 21, 2);
        aliDevBridgeUrl = SheetTools.getStringValue(settings, 22, 2);
        devBridgeHome = SheetTools.getStringValue(settings, 23, 2);
        devBridgeFolder = SheetTools.getStringValue(settings, 24, 2);
        addUsers = "yes".equals(SheetTools.getStringValue(settings, 25, 2));
    }

    public boolean isGenerateProject() {
        return generateProject;
    }

    public String getEnvironment() {
        return environment;
    }

    public String getLoginUrl() {
        return loginUrl;
    }

    public String getRestUrl() {
        return restUrl;
    }

    public void setRestUrl(String restUrl) {
        this.restUrl = restUrl;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getAdmin() {
        return admin;
    }

    public String getAliDevBridgeUrl() {
        return aliDevBridgeUrl;
    }

    public boolean isMeldRepository() {
        return meldRepository;
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

    public String getBuildTemplateFolder() {
        return buildTemplateFolder;
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

    public void setFirstRequirementNumber(int firstRequirementNumber) {
        this.firstRequirementNumber = firstRequirementNumber;
    }

    public String getPortalUrl() {
        return portalUrl;
    }

    public void setPortalUrl(String portalUrl) {
        this.portalUrl = portalUrl;
    }

    public String getDevBridgeHome() {
        return devBridgeHome;
    }

    public String getDevBridgeFolder() {
        return devBridgeFolder;
    }

    public boolean isAddUsers() {
        return addUsers;
    }
}
