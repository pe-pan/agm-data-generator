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
    private boolean meldRepository;
    private String svnUrl;
    private String svnUser;
    private boolean generateBuilds;
    private String hudsonUrl;
    private String jobName;
    private String buildFolder;
    private String buildTemplateFolder;
    private Date firstBuildDate;
    private int firstBuildNumber;
    private long firstSvnRevision;
    private int firstDefectNumber;
    private int firstRequirementNumber;

    public Settings(Sheet settings) {
        log.debug("Reading settings...");
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
        buildFolder = SheetTools.getStringValue(settings, 14, 2);
        buildTemplateFolder = SheetTools.getStringValue(settings, 15, 2);
        firstBuildDate = SheetTools.getDateValue(settings, 16, 2, new SimpleDateFormat("dd/MM/yyyy HH:mm"));
        firstBuildNumber = SheetTools.getIntValue(settings, 17, 2);
        firstSvnRevision = SheetTools.getLongValue(settings, 18, 2);
        firstDefectNumber = SheetTools.getIntValue(settings, 19, 2);
        firstRequirementNumber = SheetTools.getIntValue(settings, 20, 2);
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

    public String getTenantId() {
        return tenantId;
    }

    public String getAdmin() {
        return admin;
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

}
