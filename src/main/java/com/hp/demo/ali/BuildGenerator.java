package com.hp.demo.ali;

import com.hp.demo.ali.entity.Entity;
import com.hp.demo.ali.excel.EntityIterator;
import com.hp.demo.ali.rest.RestHelper;
import com.hp.demo.ali.svn.RepositoryMender;
import com.hp.demo.ali.tools.EntityTools;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.apache.poi.ss.usermodel.Sheet;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

/**
 * Created by panuska on 10/12/12.
 */
public class BuildGenerator {

    private static Logger log = Logger.getLogger(BuildGenerator.class.getName());
    private String buildsFolder;
    private Date firstBuildDate;
    private int firstBuildNumber;
    private long startingRevision;
    private String svnUrl;
    private String hudsonUrl;
    private String jobName;
    private String buildTemplateFolder;

    private RepositoryMender mender;

    public BuildGenerator(Settings settings) {
        this.buildsFolder = settings.getBuildFolder();
        buildTemplateFolder = settings.getBuildTemplateFolder();
        this.firstBuildDate = settings.getFirstBuildDate();
        this.firstBuildNumber = settings.getFirstBuildNumber();
        this.startingRevision = settings.getFirstSvnRevision();
        this.svnUrl = settings.getSvnUrl();
        hudsonUrl = settings.getHudsonUrl();
        jobName = settings.getJobName();
        mender = new RepositoryMender(settings);
        log.debug("Build template folder is: " + buildTemplateFolder);
    }

    public void generate(Sheet sheet, List<Long> skipRevisions) {
        log.debug("Generating builds...");
        EntityIterator iterator = new EntityIterator(sheet);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-'00'");
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

            firstBuildDate = new Date(firstBuildDate.getTime() + nextBuild);     //nextBuild is in milliseconds
            String outputFolder = buildsFolder + File.separator + jobName + File.separator + "builds" + File.separator + sdf.format(firstBuildDate);
            try {
                log.debug("Copying template folder to " + outputFolder);
                FileUtils.copyDirectory(new File(buildTemplateFolder), new File(outputFolder));
                File buildXmlFile = new File(outputFolder + File.separator + BUILD_XML);
                UUID buildId = UUID.randomUUID();
                long fromRevision = startingRevision;
                startingRevision += increaseRevision;
                long toRevision = startingRevision;
                long[] subset = getRevisionSubSet(skipRevisions, fromRevision, toRevision-1);
                String oldBuildId = correctBuildFile(buildXmlFile, buildId, firstBuildNumber++, totalLines, coveredLines, totalTests, skippedTests, failedTests, status, duration, fromRevision, toRevision-1, subset, svnUrl);
                File mavenBuildFile = new File(outputFolder + File.separator + MAVEN_XML_PREF + oldBuildId + ".xml");
                File newMavenBuildFile = new File(outputFolder + File.separator + MAVEN_XML_PREF + buildId.toString() + ".xml");
                mavenBuildFile.renameTo(newMavenBuildFile);

//                mender.alterRepository(fromRevision, toRevision, firstBuildDate.getTime() - nextBuild, firstBuildDate.getTime(), requirements, defects, unassigned, teamMembers);

            } catch (IOException e) {
                throw new IllegalStateException(e);
            }


        }
    }

    private long[] getRevisionSubSet(List<Long> skipRevisions, long fromRevision, long toRevision) {
        if (skipRevisions == null || skipRevisions.size() == 0) {
            return null;
        }
        assert(skipRevisions.get(0) >= fromRevision);
        List<Long> subset = new LinkedList<Long>();
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
        log.debug("Setting the build id into: "+buildId+" and build number into: "+buildNumber);
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        Document document = null;
        try {
            document = dbf.newDocumentBuilder().parse(buildXmlFile);

            String oldBuildId = setNodeValue(document, "//build/actions/maven-build-record/id/text()", buildId.toString());
            setNodeValue(document, "/build/actions/com.hp.alm.ali.hudson.BuildAction/codeCoverage/result/total/text()", totalLines);
            setNodeValue(document, "//build/actions/com.hp.alm.ali.hudson.BuildAction/codeCoverage/result/covered/text()", coveredLines);
            setNodeValue(document, "//build/actions/com.hp.alm.ali.hudson.BuildAction/testResults/result/total/text()", totalTests);
            setNodeValue(document, "//build/actions/com.hp.alm.ali.hudson.BuildAction/testResults/result/failed/text()", failedTests);
            setNodeValue(document, "//build/actions/com.hp.alm.ali.hudson.BuildAction/testResults/result/skipped/text()", skippedTests);
            setNodeValue(document, "//build/number/text()", buildNumber);
            setNodeValue(document, "//build/result/text()", status);
            setNodeValue(document, "//build/duration/text()", duration);
            generateRevisionNodes(document, revisionFrom, revisionTo, skip);
            setNodeValue(document, "//build/actions/com.hp.alm.ali.hudson.BuildAction/codeChanges/changes/repositoryChanges/element/id/text()", svnUrl);
            setNodeValue(document, "//build/actions/com.hp.alm.ali.hudson.BuildAction/codeChanges/changes/repositoryChanges/element/location/text()", svnUrl);
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer t = tf.newTransformer();
            t.transform(new DOMSource(document), new StreamResult(new FileOutputStream(buildXmlFile)));
            return oldBuildId;
        } catch (SAXException e) {
            throw new IllegalStateException(e);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException(e);
        } catch (TransformerConfigurationException e) {
            throw new IllegalStateException(e);
        } catch (TransformerException e) {
            throw new IllegalStateException(e);
        }
    }

    private static XPathFactory xpf = XPathFactory.newInstance();
    private static XPath xpath = xpf.newXPath();

    private void generateRevisionNodes(Document document, long revisionFrom, long revisionTo, long[] skip) {
        XPathExpression expression = null;
        try {
            expression = xpath.compile("//build/actions/com.hp.alm.ali.hudson.BuildAction/codeChanges/changes/repositoryChanges/element/revisionChanges/com.hp.alm.scm.build.RevisionChange");
            Node revisionChangeNode = (Node) expression.evaluate(document, XPathConstants.NODE);
            if (skip == null || skip.length == 0) {
                // nothing to skip
                setRevisions(revisionChangeNode, revisionFrom, revisionTo);
            } else {
                Node parent = revisionChangeNode.getParentNode();
                setRevisions(revisionChangeNode, revisionFrom, skip[0]-1);
                for (int i = 1; i < skip.length; i++) {
                    Node brother = revisionChangeNode.cloneNode(true);
                    if (setRevisions(brother, skip[i-1]+1, skip[i]-1)) {
                        parent.appendChild(brother);
                    }
                }
                Node brother = revisionChangeNode.cloneNode(true);
                if (setRevisions(revisionChangeNode, skip[skip.length-1]+1, revisionTo)) {
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
        log.debug("Setting revisions from "+revisionFrom+" to "+revisionTo);
        XPathExpression expression = xpath.compile("revFrom/text()");
        Node revFromNode = (Node) expression.evaluate(revisionChangeNode, XPathConstants.NODE);
        revFromNode.setTextContent(Long.toString(revisionFrom));
        expression = xpath.compile("revTo/text()");
        Node revToNode = (Node) expression.evaluate(revisionChangeNode, XPathConstants.NODE);
        revToNode.setTextContent(Long.toString(revisionTo));
        return true;
    }

    private String setNodeValue(Document document, String xpathString, String value) {
        try {
            XPathExpression expression = xpath.compile(xpathString);
            Node node = (Node) expression.evaluate(document, XPathConstants.NODE);
            String oldValue = node.getTextContent();
            node.setTextContent(value);
            return oldValue;
        } catch (XPathExpressionException e) {
            throw new IllegalStateException("Exception when compiling xpath "+xpathString+" in document "+document.getDocumentURI(), e);
        }
    }

    private String setNodeValue(Document document, String xpathString, long value) {
        return setNodeValue(document, xpathString, Long.toString(value));
    }

    public void createJob() {
        HashMap<String, String> data = new HashMap<String, String>();
        data.put("name", jobName);
        data.put("mode", "copy");
        data.put("from", "uimafit");   //todo uimafit - should not be encoded here
        data.put("json", "{\"name\": \"BookStore2\", \"mode\": \"copy\", \"from\": \"uimafit\", \"Submit\": \"OK\"}");
        data.put("Submit", "OK");
        RestHelper.postData(hudsonUrl + "view/All/createItem", data, null);
        //todo verify status code -> fail or log error
    }
}
