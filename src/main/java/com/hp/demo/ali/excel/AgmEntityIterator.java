package com.hp.demo.ali.excel;

import org.apache.log4j.Logger;
import org.apache.poi.ss.usermodel.Sheet;
import org.hp.almjclient.model.marshallers.Entity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Created by panuska on 3/29/13.
 */
public class AgmEntityIterator<E> extends EntityIterator implements Iterable, Iterator {
    private static Logger log = Logger.getLogger(AgmEntityIterator.class.getName());

    private Set<String> referenceColumns = new HashSet<String>();
    private String entityType;
    private String entityId;
    private static Map<String, String> idTranslationTable = new HashMap<String, String>();

    public AgmEntityIterator(Sheet sheet) {
        super(sheet);
        for (int i = 0; i < fieldNames.length; i++) {
            String fieldName = fieldNames[i];
            if (fieldName.charAt(0) == '#') {        // todo make it configurable
                fieldName = fieldName.substring(1);  // remove the prefix
                fieldNames[i] = fieldName;           // store the corrected field name
                referenceColumns.add(fieldName);     // also store it as a column containing a reference
            }
        }
        entityType = sheet.getSheetName();
    }

    @Override
    public E next() {
        String[] row = rowIterator.next();
        Map<String, Object> fields = new HashMap<String, Object>(row.length);
        entityId = row[0];
        for (int i = 1; i < row.length; i++) {  // skip very first column (it contains the entity ID)
            String value = row[i];
            if (!NULL.equals(value)) {
                String fieldName = fieldNames[i];
                if (referenceColumns.contains(fieldName)) {  // dereference the value first
                    String originalValue = value;
                    value = idTranslationTable.get(value);
                    if (value == null) {
                        log.error("Cannot translate as the value not found in table; column: "+fieldName+"; value: "+originalValue);
                        value = originalValue;
                        // leave the original value
                    }
                }
                fields.put(fieldName, value);
            }
        }

        return (E)new Entity(entityType, fields);
    }

    public void putReferencePrefix(String prefix, String value) {
        idTranslationTable.put(prefix+entityId, value);
    }

    public static void putReference(String prefix, int index, String value) {
        idTranslationTable.put(prefix+index, value);
    }

    public static void putReference(String key, String value) {
        idTranslationTable.put(key, value);
    }

    public static void logReferences() {
        log.debug("Transition table content: ");
        log.debug(idTranslationTable);
    }
}
