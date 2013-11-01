package com.hp.demo.ali;

import org.apache.log4j.Logger;

import java.io.File;
import java.io.FilenameFilter;

/**
 * Created by panuska on 21.10.13.
 */
public class Migrator {
    private static Logger log = Logger.getLogger(Migrator.class.getName());

    public static final String CONF_DIR = "conf";
    public static final String SETTINGS_PROPERTIES_FILE = "settings.properties";
    public static final String PROXY_PROPERTIES_FILE = "proxy.properties";
    public static final String WRAPPER_CUSTOM_CONF_FILE = "wrapper-custom.conf";
    public static final String JOBS_DIR = "jobs";
    public static final String JOB_PREFIX = "job-";
    public static final String JOB_SUFFIX = ".log";
    public static final String JOB_BACKUP_SUFIX = ".bak";
    public static final String TMP_DIR = "tmp";
    public static final String DEV_BRIDGE_ZIP_FILE = "DevBridge.zip";

    static void migrate() {

        // at this moment of run, do not migrate log folder + log file -> log4j has already created this new folder and the new log file
        // just delete the old log file

        File file = new File("log.txt");
        boolean migrated = file.delete();

        // migrate configuration files
        File dir = new File(CONF_DIR);
        migrated |= dir.mkdir();

        file = new File(SETTINGS_PROPERTIES_FILE);
        migrated |= file.renameTo(new File(dir, file.getName()));

        file = new File(PROXY_PROPERTIES_FILE);
        migrated |= file.renameTo(new File(dir, file.getName()));

        file = new File(WRAPPER_CUSTOM_CONF_FILE);
        migrated |= file.renameTo(new File(dir, file.getName()));

        // migrate DevBridge.zip
        dir = new File(TMP_DIR);
        migrated |= dir.mkdir();

        file = new File(DEV_BRIDGE_ZIP_FILE);
        migrated |= file.renameTo(new File(dir, file.getName()));

        // migrate job files
        dir = new File(JOBS_DIR);
        migrated |= dir.mkdir();

        File workingFolder = new File(".");
        File[] jobFiles = workingFolder.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
            return name.startsWith(JOB_PREFIX) && (name.endsWith(JOB_SUFFIX) || name.endsWith(JOB_SUFFIX+JOB_BACKUP_SUFIX));
            }
        });

        for (File jobFile : jobFiles) {
            migrated |= jobFile.renameTo(new File(dir, jobFile.getName()));
        }

        //todo migrate jar file (+ .bat file)

        if (migrated) {
            log.info("Logs, job logs and settings migrated to new folder structure");
        }
    }
}
