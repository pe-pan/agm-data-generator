package com.hp.demo.ali;

import com.hp.demo.ali.excel.AgmEntityIterator;
import com.hp.demo.ali.excel.EntityIterator;
import com.hp.demo.ali.excel.ExcelEntity;
import com.hp.demo.ali.excel.ExcelReader;
import com.hp.demo.ali.rest.AgmRestService;
import org.apache.log4j.Logger;
import org.apache.poi.ss.usermodel.Sheet;
import org.hp.almjclient.exceptions.ALMRestException;
import org.hp.almjclient.exceptions.FieldNotFoundException;
import org.hp.almjclient.exceptions.RestClientException;
import org.hp.almjclient.model.marshallers.Entity;
import org.hp.almjclient.model.marshallers.favorite.Filter;
import org.apache.commons.lang.StringUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

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
        File migratedJobLog = new File(jobLog.getParentFile(), jobLog.getName()+".tmp");
        if (migrateJobLog(reader, jobLog, migratedJobLog)) {
            // job log migrated
            if (!jobLog.delete()) {
                throw new IllegalStateException("Cannot delete the original job log file when migrating it: "+jobLog.getName());
            }
            if (!migratedJobLog.renameTo(jobLog)) {
                throw new IllegalStateException("Cannot move the migrated job log file ("+migratedJobLog.getName()+") to the original name: "+jobLog.getName());
            }
        }
    }

    public static void writeLogLine(String entityName, String entityId, String excelId) {   //todo hack; make it non-static (e.g. use context to register/retrieve common objects like JobLogger is)
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(jobLog, true));
            writer.write(entityName+"#"+excelId+"="+entityId+System.lineSeparator());
            writer.flush();
            writer.close();
        } catch (IOException e) {
            throw new IllegalStateException(e);
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

    void loadJobLog() {
        if (settings.isDeleteAll()) {
            deleteAllData();
        } else {
            if (jobLog.exists()) {
                log.info("Log from previous run found (" + jobLog.getName() + "), previously created data are going to be reused...");
                try {
                    boolean logHasChanged = false;
                    BufferedReader logReader = new BufferedReader(new FileReader(jobLog));
                    File tempJobLog = new File(jobLog.getParent(), jobLog.getName()+".tmp");
                    BufferedWriter logWriter = new BufferedWriter(new FileWriter(tempJobLog));
                    for (String line = logReader.readLine(); line != null; line = logReader.readLine()) {
                        String[] items = StringUtils.split(line, "=#");
                        if (items.length != 3) {
                            throw new IllegalStateException("Job log "+jobLog.getName()+" does not follow the expected format!");
                        }
                        if (settings.isGenerateBuilds() && (items[0].equals("build-type") || items[0].equals("scm-branch"))) {
                            // todo scm-branch-release should be also skipped
                            // do not load build jobs and SCM branches; these have to be always created new ones
                            // otherwise, ALI data synchronization over Dev Bridge would not work
                            try {
                                log.info("Dropping entity "+items[0]+":"+items[2]+" to allow ALI data refresh");
                                AgmRestService.getCRUDService().delete(items[0], Integer.parseInt(items[2]));
                                logHasChanged = true;
                            } catch (RestClientException | ALMRestException e) {
                                log.error("Cannot delete entity "+items[0]+":"+items[2], e);
                            }
                        } else {
                            AgmEntityIterator.putReference(items[0]+"#"+items[1], items[2]);
                            logWriter.write(line+System.lineSeparator());
                        }
                    }
                    logReader.close();
                    logWriter.close();
                    if (logHasChanged) {
                        if (!jobLog.delete()) {
                            log.error("Cannot delete the original job log file when loading it: "+jobLog.getName());
                        }
                        if (!tempJobLog.renameTo(jobLog)) {
                            log.error("Cannot move the temporal job log file ("+tempJobLog.getName()+") to the original name: "+jobLog.getName());
                        }
                    }
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            } else {
                log.info("No log ("+jobLog.getName()+") from previous run found; first run against this tenant?");
            }
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
            if (entityType.equals("release-backlog-item") || entityType.equals("kanban-status")) {
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
                        ids.add(id);
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

    private static Map<Sheet, EntityIterator<ExcelEntity>> iterators = new HashMap<>();
    private static String migrateJobLogLine(String original, ExcelReader reader) {
        String[] items = StringUtils.split(original, ": ");
        if (items.length != 2) {
            log.debug("No colon to split a job log line into 2 items: "+original+". Cannot continue in migration!");
            return null;
        }
        String sheetName= items[0].trim();
        String agmId = items[1].trim();
        Sheet sheet = reader.getSheet(sheetName);
        EntityIterator<ExcelEntity> it = iterators.get(sheet);
        if (it == null) {
            it = new EntityIterator<>(sheet);
            iterators.put(sheet, it);
        }
        if (it.hasNext()) {
            ExcelEntity entity = it.next();
            String excelId = entity.getFieldValue("id");
            String output = sheetName+"#"+excelId+"="+agmId;
            log.debug("Migrating '"+original+"' into '"+output+"'");
            return output;
        } else {
            log.debug("No more lines in sheet "+sheetName+". Cannot continue in migration!");
            return null;
        }
    }

    private static boolean migrateJobLog(ExcelReader reader, File jobLog, File migratedJobLog) {
        if (!jobLog.exists()) {
            log.debug("Job log "+jobLog.getName()+" does not exist; nothing to migrate");
            return false;
        }
        try {
            BufferedReader r = new BufferedReader(new FileReader(jobLog));
            String line = r.readLine();
            if (line.contains("#")) {
                log.debug("Job log "+jobLog.getName()+" seems to be already migrated");
                r.close();
                return false;
            }
            log.info("Job log seems to be in the old format. Migrating it to the new format: "+jobLog.getName());
            BufferedWriter w = new BufferedWriter(new FileWriter(migratedJobLog));
            for(;line != null; line = r.readLine()) {
                String migrated = migrateJobLogLine(line, reader);
                if (migrated == null) {
                    r.close();
                    w.close();
                    throw new IllegalStateException("Job log "+jobLog.getName()+" cannot be migrated. Check logs for errors.");
                }
                w.write(migrated+System.lineSeparator());
            }
            r.close();
            w.close();
            return true;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
