package com.hp.demo.ali.svn;

import com.hp.demo.ali.Settings;
import com.hp.demo.ali.entity.User;
import org.apache.log4j.Logger;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.*;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by panuska on 11/20/12.
 */
public class RepositoryMender {

    private static Logger log = Logger.getLogger(RepositoryMender.class.getName());
    private SVNRepository repository;

    private int requirementNumber;
    private int defectNumber;

    public RepositoryMender(Settings settings) {
        SVNURL svnUrl;
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

    private static SimpleDateFormat svnDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:s.S'Z'");

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
        int requirementCount = requirements;
        int defectCount = defects;
        int unassignedCount = unassigned;

        int requirementStart = requirementNumber;     // to remember where starts the req/def indexes in this block
        int defectStart = defectNumber;
        int requirementMax = requirementNumber;       // to remember the maximal values in this block
        int defectMax = defectNumber;

        // team members
        int userBlock = (int)Math.ceil((float)numOfRevisions / (float)teamMembers);

        for (long i = startRevision; i < endRevision; i++) {
            //time
            setProperty(i, "svn:date", svnDateFormat.format(new Date(time)));
            time += timeIncrease;

            String originalMessage = getProperty(i, "svn:log");
            if (originalMessage.startsWith("implementing user story #") ||
                    originalMessage.startsWith("fixing defect #")) {
                int index = originalMessage.indexOf(": ");
                if (index > 0) {
                    log.debug("Shortening the original message for "+originalMessage.substring(0, index+2));
                    originalMessage = originalMessage.substring(index+2);
                }
            }

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
