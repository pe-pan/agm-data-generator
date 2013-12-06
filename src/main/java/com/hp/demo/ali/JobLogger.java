package com.hp.demo.ali;

import com.hp.demo.ali.excel.ExcelReader;
import com.hp.demo.ali.rest.AgmRestService;
import com.hp.demo.ali.tools.EntityTools;
import org.apache.log4j.Logger;
import org.apache.poi.ss.usermodel.Sheet;
import org.hp.almjclient.exceptions.ALMRestException;
import org.hp.almjclient.exceptions.FieldNotFoundException;
import org.hp.almjclient.exceptions.RestClientException;
import org.hp.almjclient.model.marshallers.Entity;
import org.hp.almjclient.model.marshallers.favorite.Filter;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

/**
 * Created by panuska on 14.10.13.
 */
public class JobLogger {
    private static Logger log = Logger.getLogger(JobLogger.class.getName());

    private static File jobLog = null;                                      //todo hack; make it non-static
    private ExcelReader reader;
    private Settings settings;

    public JobLogger(ExcelReader reader) {
        this.reader = reader;
        this.settings = Settings.getSettings();
        this.jobLog = new File(Migrator.JOBS_DIR, Migrator.JOB_PREFIX+settings.getTenantId()+"-"+settings.getDomain()+"-"+settings.getProject()+Migrator.JOB_SUFFIX);  //todo hack; make it non-static
    }

    public static void writeLogLine(String entityName, String entityId) {   //todo hack; make it non-static (e.g. use context to register/retrieve common objects like JobLogger is)
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(jobLog, true));
            writer.write(entityName+": "+entityId+System.lineSeparator());
            writer.flush();
            writer.close();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private List<String> logItems = new LinkedList<>();
    private void openLog() {
        log.debug("Reading all the job log...");
        try {
            BufferedReader logReader = new BufferedReader(new FileReader(jobLog));
            for (String line = logReader.readLine(); line != null; line = logReader.readLine()) {
                logItems.add(line);
            }
            logReader.close();
            log.debug("Renaming job log file...");
            String bakFileName = jobLog.getName()+Migrator.JOB_BACKUP_SUFIX;
            File bakFile = new File(Migrator.JOBS_DIR, bakFileName);
            bakFile.delete();
            jobLog.renameTo(bakFile);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private String readLogLine() {
        if (logItems.size() > 0) {
            log.debug("Processing job log line #"+logItems.size());
            return logItems.remove(logItems.size()-1);
        } else {
            log.debug("Job log processed.");
            return null;
        }
    }

    void askForDeletePermission() {
        if (!settings.isForceDelete()) {
            log.info("Type 'yes' and press <ENTER> if you wish to continue...");
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
            String input;
            try {
                input = in.readLine();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            if (!input.equals("yes")) {
                System.exit(-1);
            }
        } else {
            log.info("Delete forced; deleting now!");
        }
    }

    void deleteExistingData() {
        if (settings.isDeleteAll()) {
            deleteAllData();
        } else {
            if (settings.isGenerateProject()) {
                deleteJobLogData();
            }
        }
    }

    void deleteJobLogData() {
        if (jobLog.exists()) {
            log.info("Log from previous run found ("+jobLog.getName()+"), previously created data are going to be deleted...");
            askForDeletePermission();
            openLog();
            String previousEntity = "";
            for (String line = readLogLine(); line != null; line = readLogLine()) {
                int columnIndex = line.indexOf(':');
                String entityName = line.substring(0, columnIndex);
                String agmId = line.substring(columnIndex+2);
                log.debug("Deleting "+entityName+" with ID: "+agmId);
                if (!entityName.equals(previousEntity)) {
                    log.info("Deleting entity: "+entityName);
                    previousEntity = entityName;
                }
                if ("release".equals(entityName) && settings.isKeepRelease()) {
                    log.info("Keeping release with ID: "+agmId);
                    settings.setReleaseId(agmId);
                } else {
                    AgmRestService service = AgmRestService.getCRUDService();
                    try {
                        service.delete(entityName, Integer.parseInt(agmId));
                    } catch (RestClientException | ALMRestException e) {
                        log.error("Cannot delete "+entityName+" with ID: "+agmId);
                    }
                }
            }
        } else {
            log.info("No log ("+jobLog.getName()+") from previous run found; first run against this tenant?");
        }
    }

    void deleteAllData() {
        log.info("###############################################################");
        log.info("###############################################################");
        log.info("# You asked to delete ALL data from the tenant! Are you sure? #");
        log.info("###############################################################");
        log.info("###############################################################");
        askForDeletePermission();
        List<Sheet> sheets = reader.getAllEntitySheets();
        ListIterator<Sheet> iterator = sheets.listIterator(sheets.size());
        while (iterator.hasPrevious()) {
            Sheet sheet = iterator.previous();
            String entityType = sheet.getSheetName();
            if (entityType.equals("release-backlog-item")) {
                log.debug("Skipping "+entityType);
                continue;   // skip release backlog items (they are not entities in AgM)
            }
            deleteEntities(entityType);
        }
        jobLog.delete(); //todo delete the job log only if no query above failed
    }

    private void deleteEntities(String entityType) {
        log.info("Deleting entity: " + entityType);
        Filter filter = new Filter(entityType);
        org.hp.almjclient.model.marshallers.Entities entities;
        AgmRestService service = AgmRestService.getCRUDService();
        try {
            entities = service.readCollection(filter);
        } catch (RestClientException | ALMRestException e) {
            log.error("Cannot query for entities: "+entityType, e);
            return; // query failed; don't delete it
        }
        int previousResults = Integer.MAX_VALUE;
        while (entities.getTotalResults() > 0 && entities.getTotalResults() < previousResults) {
            previousResults = entities.getTotalResults();
            List<Integer> ids = new ArrayList<>(entities.getTotalResults());
            for (Entity entity : entities.getEntityList()) {
                Integer id;
                try {
                    id = entity.getId();
                    log.debug("Getting " + entityType + " with ID: " + id);
                    if (id == 0) {
                        log.debug("Skipping the entity with ID 0");  //todo hack!!!!
                    } else {
                        if ("release".equals(entityType) && settings.isKeepRelease() && settings.getReleaseName().equals(EntityTools.getField(entity, "name"))) {
                            log.info("Keeping release with ID: "+id);
                            settings.setReleaseId(id);
                        } else {
                            ids.add(id);
                        }
                    }
                } catch (FieldNotFoundException e) {
                    log.error("Cannot get id of entity "+entityType+" with index="+ids.size());
                }
            }
            try {
                if (ids.size() > 0) {
                    service.delete(entityType, ids, true);
                }
            } catch (RestClientException | ALMRestException e) {
                log.error("Cannot delete "+ids.size()+" entities: "+entityType, e);
                break; // query failed; don't continue querying it
            }
            try {
                entities = service.readCollection(filter);
            } catch (RestClientException | ALMRestException e) {
                log.error("Cannot query for entities: "+entityType, e);
            }
        }
    }
}
