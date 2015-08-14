package com.hp.demo.ali.tools;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Created by panuska on 1.11.13.
 */
public class ResourceTools {

    private static Logger log = Logger.getLogger(ResourceTools.class.getName());

    /**
     * Returns the input stream either from the provided file (if exists) or from the built-in resource.
     */
    public static InputStream getCustomResource(File file) {
        InputStream in;
        if (file.exists()) {
            log.debug("File "+file.getAbsolutePath()+" found, using this one.");
            try {
                in = new FileInputStream(file);
            } catch (FileNotFoundException e) {
                throw new IllegalStateException(e);
            }
        } else {
            log.debug("No file "+file.getAbsolutePath()+" found, using the built-in one.");
            in = ResourceTools.class.getResourceAsStream("/"+file.getName());
        }
        return in;
    }

    public static String getCustomResourceContent(File file) {
        try {
            return IOUtils.toString(getCustomResource(file));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
