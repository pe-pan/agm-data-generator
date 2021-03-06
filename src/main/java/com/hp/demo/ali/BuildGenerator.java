package com.hp.demo.ali;

import com.hp.demo.ali.entity.User;
import com.hp.demo.ali.excel.EntityIterator;
import com.hp.demo.ali.excel.ExcelEntity;
import com.hp.demo.ali.excel.ExcelReader;
import com.hp.demo.ali.rest.IllegalRestStateException;
import com.hp.demo.ali.rest.RestClient;
import com.hp.demo.ali.rest.RestTools;
import com.hp.demo.ali.svn.RepositoryMender;
import com.hp.demo.ali.tools.XmlFile;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.xml.bind.DatatypeConverter;
import javax.xml.xpath.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

/**
 * Created by panuska on 10/12/12.
 */
public class BuildGenerator {

    private static Logger log = Logger.getLogger(BuildGenerator.class.getName());
    private Settings settings;

    private Date currentBuildDate;
    private int currentBuildNumber;
    private long startingRevision;

    private RepositoryMender mender;
    private ExcelReader reader;

    public BuildGenerator(ExcelReader reader) {
        this.reader = reader;
        this.settings = Settings.getSettings();
        currentBuildDate = settings.getFirstBuildDate();
        currentBuildNumber = settings.getFirstBuildNumber();
        startingRevision = settings.getFirstSvnRevision();
        mender = new RepositoryMender(settings);
    }

    public void deleteJob() {
        log.debug("Trying to delete job "+settings.getJobName()+" at Hudson...");
        RestClient hudsonClient = new RestClient();
        try {
            hudsonClient.doPost(settings.getHudsonUrl()+"job/"+ RestTools.encodeUrl(settings.getJobName())+"/doDelete", (String) null);
        } catch (IllegalStateException e) {
            log.debug("Cannot delete the job, probably it does not exist");
        }
        try {    // if Hudson was not running, the folder was not deleted; let's delete it also on file system
            FileUtils.deleteDirectory(new File(settings.getBuildFolder() + File.separator + settings.getJobName()));
        } catch (IOException e) {
            log.debug("Cannot delete the job folder, probably it does not exist");
        }
    }

    public void generate() {
        log.info("Generating builds...");
        EntityIterator<ExcelEntity> iterator = new EntityIterator<>(reader.getSheet("Builds"));
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-'00'");
        List<Long> skipRevisions = readSkippedRevisions();
        try {
            while (iterator.hasNext()) {
                ExcelEntity entity = iterator.next();
                int nextBuild = entity.getFieldIntValue("next build");
                int totalLines = entity.getFieldIntValue("totalLines");
                int coveredLines = entity.getFieldIntValue("coveredLines");
                int totalTests = entity.getFieldIntValue("totalTests");
                int failedTests = entity.getFieldIntValue("failedTests");
                int skippedTests = entity.getFieldIntValue("skippedTests");
                String status = entity.getFieldValue("status");
                int duration = entity.getFieldIntValue("duration");
                long increaseRevision = entity.getFieldLongValue("revisions");
                int requirements = entity.getFieldIntValue("requirements");
                int defects = entity.getFieldIntValue("defects");
                int unassigned = entity.getFieldIntValue("unassigned");
                int teamMembers = entity.getFieldIntValue("team members");

                currentBuildDate = new Date(currentBuildDate.getTime() + nextBuild);     //nextBuild is in milliseconds
                String outputFolder = settings.getBuildFolder() + File.separator + settings.getJobName() + File.separator + "builds" + File.separator + sdf.format(currentBuildDate);
                log.info("Generating build " + sdf.format(currentBuildDate));
                copyBuildTemplateFolder(outputFolder);
                File buildXmlFile = new File(outputFolder + File.separator + BUILD_XML);
                UUID buildId = UUID.randomUUID();
                long fromRevision = startingRevision;
                startingRevision += increaseRevision;
                long toRevision = startingRevision;
                long[] subset = getRevisionSubSet(skipRevisions, fromRevision, toRevision - 1);
                String oldBuildId = correctBuildFile(buildXmlFile, buildId, currentBuildNumber++, totalLines, coveredLines, totalTests, skippedTests, failedTests, status, duration, fromRevision, toRevision - 1, subset, settings.getSvnUrl());
                File mavenBuildFile = new File(outputFolder + File.separator + MAVEN_XML_PREF + oldBuildId + ".xml");
                File newMavenBuildFile = new File(outputFolder + File.separator + MAVEN_XML_PREF + buildId.toString() + ".xml");
                mavenBuildFile.renameTo(newMavenBuildFile);

                if (settings.isAlterRepository()) {
                    mender.alterRepository(fromRevision, toRevision, currentBuildDate.getTime() - nextBuild, currentBuildDate.getTime(), requirements, defects, unassigned, teamMembers);
                }
                FileUtils.write(new File(outputFolder + File.separator + "changelog.xml"), mender.getRevisionsLog(fromRevision, toRevision - 1));
                FileUtils.write(new File(outputFolder + File.separator + "revision.txt"), settings.getSvnUrl()+settings.getBranchPath()+"/"+(toRevision-1));
            }
            FileUtils.copyFile(  // the new build needs to know SVN credentials
                    new File(settings.getBuildFolder()+File.separator+settings.getTemplateJobName()+File.separator+"subversion.credentials"),
                    new File(settings.getBuildFolder()+File.separator+settings.getJobName()+File.separator+"subversion.credentials"));

            FileUtils.writeStringToFile(
                    new File(settings.getBuildFolder()+File.separator+settings.getJobName()+File.separator+"nextBuildNumber"),
                    Integer.toString(currentBuildNumber));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final String buildTemplateFolder = "/build-template";
    private static final String buildFiles[] = {   //todo this list must get updated anytime the build-template folder gets updated!
        "/aliCoverage.xml",
            "/aliLog",
            "/aliTests.xml",
            "/build.xml",
            "/changelog.xml",
            "/coverage.xml",
            "/coverage1.xml",
            "/coverage2.xml",
            "/junitResult.xml",
            "/log",
            "/maven-build-2c43ca47-15ac-45fa-9f89-1289a679d965.xml",
            "/revision.txt"
    };

    private void copyBuildTemplateFolder(String outputFolder) {
        log.debug("Build template folder is: jar-file!" + buildTemplateFolder);

        new File(outputFolder).mkdirs();
        for (String fileName : buildFiles) {
            try {
                InputStream in = getClass().getResourceAsStream(buildTemplateFolder + fileName);
                OutputStream out = new FileOutputStream(outputFolder+fileName);
                IOUtils.copy(in, out);
                in.close();
                out.close();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private List<Long> readSkippedRevisions() {
        EntityIterator<ExcelEntity> iterator = new EntityIterator<>(reader.getSheet("Skip-Revisions"));
        List<Long> revisions = new LinkedList<>();
        while (iterator.hasNext()) {
            ExcelEntity entity =  iterator.next();
            long revision = entity.getFieldLongValue("revisions to skip");
            revisions.add(revision);
        }
        return revisions;
    }

    private long[] getRevisionSubSet(List<Long> skipRevisions, long fromRevision, long toRevision) {
        if (skipRevisions == null || skipRevisions.size() == 0) {
            return null;
        }
        assert (skipRevisions.get(0) >= fromRevision);
        List<Long> subset = new LinkedList<>();
        while (skipRevisions.size() > 0 && skipRevisions.get(0) <= toRevision) {
            subset.add(skipRevisions.remove(0));
        }
        long[] returnValue = new long[subset.size()];
        int i = 0;
        for (Long item : subset) {
            returnValue[i++] = item;
        }
        return returnValue;
    }

    private static final String BUILD_XML = "build.xml";
    private static final String MAVEN_XML_PREF = "maven-build-";

    private String correctBuildFile(File buildXmlFile, UUID buildId, int buildNumber, int totalLines, int coveredLines,
                                    int totalTests, int skippedTests, int failedTests, String status, int duration, long revisionFrom, long revisionTo, long[] skip, String svnUrl) {
        log.debug("Setting the build id into: " + buildId + " and build number into: " + buildNumber);
        XmlFile file = new XmlFile(buildXmlFile);

        String oldBuildId = file.setNodeValue("//build/actions/maven-build-record/id/text()", buildId.toString());
        file.setNodeValue("//build/actions/hudson.scm.SubversionTagAction/tags/entry/hudson.scm.SubversionSCM_-SvnInfo/revision/text()", revisionTo);
        file.setNodeValue("//build/actions/hudson.scm.SVNRevisionState/revisions/entry/long/text()", revisionTo);
        file.setNodeValue("//build/actions/com.hp.alm.ali.hudson.BuildAction/codeCoverage/content/hudson.FilePath/default/remote/text()", buildXmlFile.getParent()+File.separator+"aliCoverage.xml");
        file.setNodeValue("//build/actions/com.hp.alm.ali.hudson.BuildAction/codeCoverage/result/total/text()", totalLines);
        file.setNodeValue("//build/actions/com.hp.alm.ali.hudson.BuildAction/codeCoverage/result/covered/text()", coveredLines);
        file.setNodeValue("//build/actions/com.hp.alm.ali.hudson.BuildAction/testResults/content/hudson.FilePath/default/remote/text()", buildXmlFile.getParent()+File.separator+"aliTests.xml");
        file.setNodeValue("//build/actions/com.hp.alm.ali.hudson.BuildAction/testResults/result/total/text()", totalTests);
        file.setNodeValue("//build/actions/com.hp.alm.ali.hudson.BuildAction/testResults/result/failed/text()", failedTests);
        file.setNodeValue("//build/actions/com.hp.alm.ali.hudson.BuildAction/testResults/result/skipped/text()", skippedTests);
        file.setNodeValue("//build/number/text()", buildNumber);
        file.setNodeValue("//build/result/text()", status);
        file.setNodeValue("//build/duration/text()", duration);
        generateRevisionNodes(file.getDocument(), revisionFrom, revisionTo, skip);
        file.setNodeValue("//build/actions/com.hp.alm.ali.hudson.BuildAction/codeChanges/changes/repositoryChanges/element/id/text()", svnUrl);
        file.setNodeValue("//build/actions/com.hp.alm.ali.hudson.BuildAction/codeChanges/changes/repositoryChanges/element/location/text()", svnUrl);
        file.setNodeValue("//build/actions/com.hp.alm.ali.hudson.BuildAction/codeChanges/revision/text()", revisionTo);
        file.save(buildXmlFile);
        return oldBuildId;
    }

    private static XPathFactory xpf = XPathFactory.newInstance();
    private static XPath xpath = xpf.newXPath();

    private void generateRevisionNodes(Document document, long revisionFrom, long revisionTo, long[] skip) {
        XPathExpression expression;
        try {
            expression = xpath.compile("//build/actions/com.hp.alm.ali.hudson.BuildAction/codeChanges/changes/repositoryChanges/element/revisionChanges/com.hp.alm.scm.build.RevisionChange");
            Node revisionChangeNode = (Node) expression.evaluate(document, XPathConstants.NODE);
            if (skip == null || skip.length == 0) {
                // nothing to skip
                setRevisions(revisionChangeNode, revisionFrom, revisionTo);
            } else {
                Node parent = revisionChangeNode.getParentNode();
                setRevisions(revisionChangeNode, revisionFrom, skip[0] - 1);
                for (int i = 1; i < skip.length; i++) {
                    Node brother = revisionChangeNode.cloneNode(true);
                    if (setRevisions(brother, skip[i - 1] + 1, skip[i] - 1)) {
                        parent.appendChild(brother);
                    }
                }
                Node brother = revisionChangeNode.cloneNode(true);
                if (setRevisions(revisionChangeNode, skip[skip.length - 1] + 1, revisionTo)) {
                    parent.appendChild(brother);
                }
            }
        } catch (XPathExpressionException e) {
            throw new IllegalStateException(e);
        }
    }

    private boolean setRevisions(Node revisionChangeNode, long revisionFrom, long revisionTo) throws XPathExpressionException {
        if (revisionFrom > revisionTo) {    // skip two or more subsequent revisions
            return false;
        }
        log.debug("Setting revisions from " + revisionFrom + " to " + revisionTo);
        XPathExpression expression = xpath.compile("revFrom/text()");
        Node revFromNode = (Node) expression.evaluate(revisionChangeNode, XPathConstants.NODE);
        revFromNode.setTextContent(Long.toString(revisionFrom));
        expression = xpath.compile("revTo/text()");
        Node revToNode = (Node) expression.evaluate(revisionChangeNode, XPathConstants.NODE);
        revToNode.setTextContent(Long.toString(revisionTo));
        return true;
    }

    public void createJob() {
        log.info("Creating job " + settings.getJobName() + " at "+settings.getBuildServerName()+"...");
        RestClient client = new RestClient();
        try {
            configureJob(settings.getJobName(), settings.getTemplateJobName(), settings.getSvnUrl());
            String[][] data = new String[][] {
                    { "nextBuildNumber", ""+currentBuildNumber },
                    { "json", "{\"nextBuildNumber\": \""+currentBuildNumber+"\"}" },

            };
            client.doPost(settings.getHudsonUrl() + "job/" + settings.getJobName() + "/nextbuildnumber/submit", data);
        } catch (IllegalRestStateException e) {
            log.error("Create job exception caught; Code: " + e.getResponseCode(), e);
        }
    }

    public void configureSecondJob() {
        if (!settings.configureSecondJob()) return;
        try {
            log.info("Configuring job " + settings.getSecondJobName() + " at "+settings.getBuildServerName()+"...");
            configureJob(settings.getSecondJobName(), settings.getSecondJobName(), settings.getSecondSvnUrl());
        } catch (IllegalRestStateException e) {
            log.error("Exception when configuring job "+settings.getSecondJobName()+"; Code: " + e.getResponseCode(), e);
        }
    }

    /**
     * Gets the config from template; changes values of SVN URL and ALI connections settings and either creates a new job or changes the existing
     * @param jobName if equals to templateJobName, just reconfigures this job; creates a new job otherwise
     * @param templateJobName where to take the original config.xml
     * @param svnUrl
     */
    private void configureJob(String jobName, String templateJobName, String svnUrl) {
        final String configUrl = settings.getHudsonUrl()+"job/"+RestTools.encodeUrl(templateJobName)+"/config.xml";
        RestClient client = new RestClient();
        String templateConfig = client.doGet(configUrl).getResponse();

        XmlFile xmlFile = new XmlFile(templateConfig);
        xmlFile.setNodeValue("//scm/locations/hudson.scm.SubversionSCM_-ModuleLocation/remote", svnUrl+settings.getBranchPath());
        xmlFile.setNodeValue("//publishers/com.hp.alm.ali.hudson.BuildRecorder/almLocation", settings.getRestUrl());
        xmlFile.setNodeValue("//publishers/com.hp.alm.ali.hudson.BuildRecorder/almDomain", settings.getDomain());
        xmlFile.setNodeValue("//publishers/com.hp.alm.ali.hudson.BuildRecorder/almProject", settings.getProject());
        xmlFile.setNodeValue("//publishers/com.hp.alm.ali.hudson.BuildRecorder/almUsername", User.getUser(settings.getAdmin()).getLogin());
        xmlFile.setNodeValue("//publishers/com.hp.alm.ali.hudson.BuildRecorder/almPassword", DatatypeConverter.printBase64Binary(User.getUser(settings.getAdmin()).getPassword().getBytes()));
        xmlFile.setNodeValue("//publishers/com.hp.alm.ali.hudson.BuildRecorder/almBuildServer", settings.getBuildServerName());
        if (jobName.equals(templateJobName)) {                 // only update existing
            client.doPost(configUrl, xmlFile.saveToString());
        } else {                                               // create a new one
            client.doPost(settings.getHudsonUrl()+"/createItem?name="+RestTools.encodeUrl(jobName), xmlFile.saveToString());
        }
    }

    private void startProcess (String activity, String[] args) {
        try {
            log.debug("Executing "+ Arrays.toString(args));
            Process process = Runtime.getRuntime().exec(args);
            log.debug(activity+" Hudson service STD:" + IOUtils.toString(process.getInputStream()));
            process.waitFor();
        } catch (InterruptedException e) {
            log.error(activity+" of Hudson service interrupted!", e);
        } catch (IOException e) {
            log.error("Cannot execute the command or read its std. output; cmd: "+Arrays.toString(args), e);
        }
    }

    public void configureHudson(ProxyConfigurator proxyConfigurator) {
        String content = proxyConfigurator.getHudsonProxyConfiguration();
        File file = new File(settings.getHudsonFolder()+File.separator+"proxy.xml");

        String originalContent;
        try {
            originalContent = FileUtils.readFileToString(file);
        } catch (IOException e) {
            log.debug("Cannot read Hudson proxy configuration from: " + file.getAbsolutePath(), e);
            originalContent = "";
        }
        try {
            if (!content.equals(originalContent)) {
                log.debug("Writing Hudson proxy configuration "+content+"into: "+file.getAbsolutePath());
                FileUtils.write(file, content);
                log.info("New proxy settings written; restarting Hudson service");
                final String[] startHudsonService = {"cmd.exe", "/c", "sc", "start", settings.getHudsonServiceName()};
                final String[] stopHudsonService = {"cmd.exe", "/c", "sc", "stop", settings.getHudsonServiceName()};
                startProcess("Stopping", stopHudsonService);
                startProcess("Starting", startHudsonService);
            } else {
                log.debug("Identical Hudson proxy settings already exists; nothing necessary to be written");
            }
        } catch (IOException e) {
            log.error("Cannot write Hudson proxy configuration into: "+file.getAbsolutePath(), e);
        }
    }

}
