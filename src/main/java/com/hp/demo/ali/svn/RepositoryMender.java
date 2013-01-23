package com.hp.demo.ali.svn;

import com.hp.demo.ali.Settings;
import com.hp.demo.ali.entity.Entity;
import com.hp.demo.ali.entity.Field;
import com.hp.demo.ali.entity.User;
import com.hp.demo.ali.excel.EntityIterator;
import org.apache.log4j.Logger;
import org.apache.poi.ss.usermodel.Sheet;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.*;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Created by panuska on 11/20/12.
 */
public class RepositoryMender {

    private static Logger log = Logger.getLogger(RepositoryMender.class.getName());
    private SVNRepository repository;

    int requirementNumber;
    int defectNumber;
    public RepositoryMender(Settings settings) {
        SVNURL svnUrl = null;
        String url = settings.getSvnUrl();
        User user = User.getUser(settings.getSvnUser());
        try {
            svnUrl = SVNURL.parseURIEncoded(url);
            repository = SVNRepositoryFactory.create(svnUrl);
            log.debug("Connection to SVN repository created: "+url);
        } catch (SVNException e) {
            throw new IllegalStateException(e);
        }
        SVNPasswordAuthentication authentication = new SVNPasswordAuthentication(user.getLogin(), user.getPassword(), true, svnUrl, false);
        ISVNAuthenticationManager authenticationManager = new BasicAuthenticationManager(new SVNAuthentication[] {authentication });
        repository.setAuthenticationManager(authenticationManager);
        requirementNumber = settings.getFirstRequirementNumber();
        defectNumber = settings.getFirstDefectNumber();
    }

    public void mendRepository(Sheet sheet) {
        log.debug("Working on: "+sheet.getSheetName());
        EntityIterator iterator = new EntityIterator(sheet);
        while (iterator.hasNext()) {
            Entity entity =  iterator.next();

            List<Field> fields = entity.getFields().getField();
            Field revisionField = fields.remove(0);
            assert("revision".equals(revisionField.getName())); //revision is the very first field
            long revision = Long.parseLong(revisionField.getValue().getValue());
            log.debug("Let's reset revision: "+revision);
            for (Field field : fields) {
                SVNPropertyValue value = SVNPropertyValue.create(field.getValue().getValue());
                try {
                    log.debug("Setting "+field.getName()+" into "+value);
                    repository.setRevisionPropertyValue(revision, field.getName(), value);
                } catch (SVNException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
    }

    private static SimpleDateFormat svnDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:s.S'Z'");

    public void mendRepository(Date releaseStart, int firstDefect, int lastDefect, int firstRequirement, int lastRequirement) {
        log.debug("Melding repository...");
        Date today = new Date();

        if (releaseStart.getTime() >= today.getTime() || lastDefect < firstDefect || lastRequirement < firstRequirement) {
            throw new IllegalArgumentException("Release start is in future or defect/requirement ids are wrongly set: "+ svnDateFormat.format(releaseStart));
        }

        long lastRevision;
        try {
            lastRevision = repository.getLatestRevision();
        } catch (SVNException e) {
            throw new IllegalStateException(e);
        }
        log.debug("Mending release starting at: "+ svnDateFormat.format(releaseStart)+"; till the revision: "+lastRevision);

        long incrementDate = (today.getTime() - releaseStart.getTime()) / lastRevision; // todo commits will be spread uniformly; create a better algorithm

        int numberOfDefects = lastDefect - firstDefect;
        int numberOfRequirements = lastRequirement - firstRequirement;

        boolean defectsMoreThanRequirements = numberOfDefects > numberOfRequirements;
        int entityRatio;
        if (defectsMoreThanRequirements) {
            entityRatio = numberOfDefects / numberOfRequirements;
        } else {
            entityRatio = numberOfRequirements / numberOfDefects;
        }

        int ratioIncrement = 0;
        int defectId = firstDefect;
        int requirementId = firstRequirement;
        long commitTime = releaseStart.getTime();
        for (long i = 0; i < lastRevision; i++) {
            String message;
            if (defectsMoreThanRequirements) {
                if (ratioIncrement >= entityRatio) {
                    message = "implementing user story #"+requirementId+": ";
                    ratioIncrement = 0;
                    requirementId++;
                    if (requirementId >= lastRequirement) {
                        requirementId = firstRequirement;
                    }
                } else {
                    message = "fixing defect #"+defectId+": ";
                    defectId++;
                    if (defectId >= lastDefect) {
                        defectId = firstDefect;
                    }
                }
            } else {
                if (ratioIncrement >= entityRatio) {
                    message = "fixing defect #"+defectId+": ";
                    ratioIncrement = 0;
                    defectId++;
                    if (defectId >= lastDefect) {
                        defectId = firstDefect;
                    }
                } else {
                    message = "implementing user story #"+requirementId+": ";
                    requirementId++;
                    if (requirementId >= lastRequirement) {
                        requirementId = firstRequirement;
                    }
                }
            }
            ratioIncrement++;
            commitTime += incrementDate;
            try {
                SVNPropertyValue originalValue = repository.getRevisionPropertyValue(i, "svn:log");
                if (originalValue != null && !originalValue.getString().startsWith(message)) {
                    message = message + originalValue.getString();
                }
                SVNPropertyValue svnMessage = SVNPropertyValue.create(message);
                SVNPropertyValue svnDate = SVNPropertyValue.create(svnDateFormat.format(new Date(commitTime)));
                log.debug("Revision "+i+": setting d:"+svnDate.getString()+" and m:"+message);
                repository.setRevisionPropertyValue(i, "svn:log", svnMessage);
                repository.setRevisionPropertyValue(i, "svn:date", svnDate);
            } catch (SVNException e) {
                new IllegalStateException(e);
            }
        }
    }

    public void setProperty(long revision, String propertyName, String propertyValue) {
        log.debug("At revision "+revision+" setting "+propertyName+" = "+propertyValue);
        try {
            repository.setRevisionPropertyValue(revision, propertyName, SVNPropertyValue.create(propertyValue));
        } catch (SVNException e) {
            throw new IllegalStateException(e);
        }
    }

    public String getProperty(long revision, String propertyName) {
        try {
            return repository.getRevisionPropertyValue(revision, propertyName).toString();
        } catch (SVNException e) {
            throw new IllegalStateException(e);
        }
    }

    User[] users = User.getUsers();
    int userIndex = 0;

    public void alterRepository(long startRevision, long endRevision, long startDate, long endDate, int requirements, int defects, int unassigned, int teamMembers) {
        // time
        long numOfRevisions = endRevision - startRevision;
        long time = startDate;
        long timeIncrease = (endDate - time) / numOfRevisions;

        // reqs/defs/unassigned
//        int linksBlock = (int)numOfRevisions / (requirements + defects + unassigned);
        int requirementCount = requirements;
        int defectCount = defects;
        int unassignedCount = unassigned;

        int requirementStart = requirementNumber;     // to remember where starts the req/def indexes in this block
        int defectStart = defectNumber;
        int requirementMax = requirementNumber;       // to remember the maximal values in this block
        int defectMax = defectNumber;

        // team members
        int userBlock = (int) numOfRevisions / teamMembers;

        for (long i = startRevision; i < endRevision; i++) {
            //time
            setProperty(i, "svn:date", svnDateFormat.format(new Date(time)));
            time += timeIncrease;

            //reqs/defs/unassigned
//            if (i % linksBlock == 0) {
//                if (requirements > 0) {
//                    requirements--;
//                    requirementNumber++;
//                } else if (defects > 0) {
//                    defects--;
//                    defectNumber++;
//                }
//            }
            String originalMessage = getProperty(i, "svn:log");

            if (requirementCount >= defectCount && requirementCount >= unassignedCount) {
                // requirements
                setProperty(i, "svn:log", "implementing user story #"+requirementNumber+": "+originalMessage);
                requirementCount--;
                requirementNumber++;
            } else if (defectCount >= unassignedCount) {
                // defects
                setProperty(i, "svn:log", "fixing defect #"+defectNumber+": "+originalMessage);
                defectCount--;
                defectNumber++;
            } else {
                // unassigned
                unassignedCount--;
                if (requirementCount <= 0 && defectCount <= 0 && unassignedCount <= 0) {
                    // reset counter
                    requirementCount = requirements;
                    defectCount = defects;
                    unassignedCount = unassigned;
                    requirementMax = requirementNumber;    // store the maximal values
                    defectMax = defectNumber;
                    requirementNumber = requirementStart;  // reset values back to beginning (when block started)
                    defectNumber = defectStart;
                }
            }

//            if (requirements > 0) {
//                setProperty(i, "svn:log", "implementing user story #"+requirementNumber+": "+originalMessage);
//            } else if (defects > 0) {
//                setProperty(i, "svn:log", "fixing defect #"+defectNumber+": "+originalMessage);
//            }

            //team members
            if (i%userBlock == 0) {
                userIndex++;
            }
            setProperty(i, "svn:author", users[userIndex % users.length].getLogin());
        }
        requirementNumber = requirementMax;               // set the req/def indexes to the maximal values it ever had in this block (so next block does not repeat the same values)
        defectNumber = defectMax;
    }
}
