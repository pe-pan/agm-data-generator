package com.hp.demo.ali;

import com.hp.demo.ali.agm.BuildServerHandler;
import com.hp.demo.ali.entity.Entity;
import com.hp.demo.ali.entity.User;
import com.hp.demo.ali.excel.EntityIterator;
import com.hp.demo.ali.rest.RestClient;
import com.hp.demo.ali.svn.RepositoryMender;
import com.hp.demo.ali.tools.EntityTools;
import com.hp.demo.ali.tools.Scrambler;
import com.hp.demo.ali.tools.XmlFile;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.apache.poi.ss.usermodel.Sheet;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.xml.xpath.*;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
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

    public BuildGenerator(Settings settings) {
        this.settings = settings;
        currentBuildDate = settings.getFirstBuildDate();
        currentBuildNumber = settings.getFirstBuildNumber();
        startingRevision = settings.getFirstSvnRevision();
        mender = new RepositoryMender(settings);
        log.debug("Build template folder is: " + settings.getBuildTemplateFolder());
    }

    public void deleteJob() {
        log.debug("Trying to delete job "+settings.getJobName()+" at Hudson...");
        RestClient hudsonClient = new RestClient();
        try {
            hudsonClient.doPost(settings.getHudsonUrl()+"job/"+settings.getJobName()+"/doDelete", (String) null);
        } catch (IllegalStateException e) {
            log.debug("Cannot delete the job, probably it does not exist");
        }
        try {    // if Hudson was not running, the folder was not deleted; let's delete it also on file system
            FileUtils.deleteDirectory(new File(settings.getBuildFolder() + File.separator + settings.getJobName()));
        } catch (IOException e) {
            log.debug("Cannot delete the job folder, probably it does not exist");
        }
    }

    public void generate(Sheet sheet, List<Long> skipRevisions) {
        log.info("Generating builds...");
        EntityIterator<Entity> iterator = new EntityIterator<>(sheet);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-'00'");
        try {
            while (iterator.hasNext()) {
                Entity entity = iterator.next();
                int nextBuild = EntityTools.getFieldIntValue(entity, "next build");
                int totalLines = EntityTools.getFieldIntValue(entity, "totalLines");
                int coveredLines = EntityTools.getFieldIntValue(entity, "coveredLines");
                int totalTests = EntityTools.getFieldIntValue(entity, "totalTests");
                int failedTests = EntityTools.getFieldIntValue(entity, "failedTests");
                int skippedTests = EntityTools.getFieldIntValue(entity, "skippedTests");
                String status = EntityTools.getFieldValue(entity, "status");
                int duration = EntityTools.getFieldIntValue(entity, "duration");
                long increaseRevision = EntityTools.getFieldLongValue(entity, "revisions");
                int requirements = EntityTools.getFieldIntValue(entity, "requirements");
                int defects = EntityTools.getFieldIntValue(entity, "defects");
                int unassigned = EntityTools.getFieldIntValue(entity, "unassigned");
                int teamMembers = EntityTools.getFieldIntValue(entity, "team members");

                currentBuildDate = new Date(currentBuildDate.getTime() + nextBuild);     //nextBuild is in milliseconds
                String outputFolder = settings.getBuildFolder() + File.separator + settings.getJobName() + File.separator + "builds" + File.separator + sdf.format(currentBuildDate);
                log.info("Generating build " + sdf.format(currentBuildDate));
                FileUtils.copyDirectory(new File(settings.getBuildTemplateFolder()), new File(outputFolder));
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

                if (settings.isMeldRepository()) {
                    mender.alterRepository(fromRevision, toRevision, currentBuildDate.getTime() - nextBuild, currentBuildDate.getTime(), requirements, defects, unassigned, teamMembers);
                }
                FileUtils.write(new File(outputFolder + File.separator + "changelog.xml"), mender.getRevisionsLog(fromRevision, toRevision - 1));
                FileUtils.write(new File(outputFolder + File.separator + "revision.txt"), settings.getSvnUrl()+"/trunk/"+(toRevision-1));  //todo /trunk/ is hard-coded -> should be taken from Excel/scm-branch/path
            }
            FileUtils.copyFile(  // the new build needs to know SVN credentials
                    new File(settings.getBuildFolder()+File.separator+settings.getTemplateJobName()+File.separator+"subversion.credentials"),
                    new File(settings.getBuildFolder()+File.separator+settings.getJobName()+File.separator+"subversion.credentials"));
            XmlFile file = new XmlFile(new File(settings.getBuildFolder()+File.separator+settings.getTemplateJobName()+File.separator+"config.xml"));
            file.setNodeValue("/project/project-properties/entry/external-property/originalValue[@class='com.hp.alm.ali.hudson.BuildRecorder']/almLocation", settings.getRestUrl());
            file.setNodeValue("/project/project-properties/entry/external-property/originalValue[@class='com.hp.alm.ali.hudson.BuildRecorder']/almDomain", settings.getDomain());
            file.setNodeValue("/project/project-properties/entry/external-property/originalValue[@class='com.hp.alm.ali.hudson.BuildRecorder']/almProject", settings.getProject());
            file.setNodeValue("/project/project-properties/entry/external-property/originalValue[@class='com.hp.alm.ali.hudson.BuildRecorder']/almUsername", User.getUser(settings.getAdmin()).getLogin());
            file.setNodeValue("/project/project-properties/entry/external-property/originalValue[@class='com.hp.alm.ali.hudson.BuildRecorder']/almPassword", Scrambler.scramble(User.getUser(settings.getAdmin()).getPassword()));
            file.setNodeValue("/project/project-properties/entry/external-property/originalValue[@class='com.hp.alm.ali.hudson.BuildRecorder']/almBuildServer", BuildServerHandler.getBuildServerName());
            file.save(new File(settings.getBuildFolder() + File.separator + settings.getJobName() + File.separator + "config.xml"));

            FileUtils.writeStringToFile(
                    new File(settings.getBuildFolder()+File.separator+settings.getJobName()+File.separator+"nextBuildNumber"),
                    Integer.toString(currentBuildNumber));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
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
        log.info("Creating job " + settings.getJobName() + " at Hudson...");
        RestClient client = new RestClient();
        log.debug("Reloading from disk (just generated data)");
        try {
            client.doGet(settings.getHudsonUrl()+ "reload");
        } catch (IllegalStateException e) {
            log.debug("Reload exception caught...");
            //todo reload returns 503 (exception) -> check it's the correct one
        }
    }
}
