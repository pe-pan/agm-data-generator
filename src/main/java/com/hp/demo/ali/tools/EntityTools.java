package com.hp.demo.ali.tools;

import org.apache.log4j.Logger;
import org.hp.almjclient.exceptions.FieldNotFoundException;
import org.hp.almjclient.model.marshallers.Entity;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;

/**
 * Created by panuska on 10/30/12.
 */
public class EntityTools {

    private static Logger log = Logger.getLogger(EntityTools.class.getName());

    public static String entityToString(Entity entity) {
        Set<String> fields = entity.getFieldsKeySet();
        StringBuilder sb = new StringBuilder("{");
        for (String field : fields) {
            String value = getField(entity, field);
            sb.append("\"").append(field).append("\": \"").append(value).append("\", ");
        }
        sb.delete(sb.length()-2, sb.length());
        sb.append("}");
        return sb.toString();
    }

    public static String getField(Entity entity, String fieldName) {
        try {
            return entity.getFieldValue(fieldName).getValue();
        } catch (FieldNotFoundException e) {
            log.debug("No field "+fieldName+" in the entity "+entity);
            return null;
        }
    }

    public static int getIntField(Entity entity, String fieldName) {
        String value = getField(entity, fieldName);
        return value == null ? -1 : Integer.parseInt(value);
    }

    public static long getLongField(Entity entity, String fieldName) {
        String value = getField(entity, fieldName);
        return value == null ? -1 : Long.parseLong(value);
    }

    public static Date getDateField(Entity entity, String fieldName, SimpleDateFormat sdf) {
        try {
            return sdf.parse(getField(entity, fieldName));
        } catch (ParseException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String removeField(Entity entity, String fieldName) {
        String returnValue = getField(entity, fieldName);
        try {
            entity.removeField(fieldName);
        } catch (NullPointerException e) {
            // catching NPE when removing non-existing field
            // todo bug in AgM REST API
        }
        return returnValue;
    }

    public static int removeIntField(Entity entity, String fieldName) {
        String value = removeField(entity, fieldName);
        return value == null ? -1 : Integer.parseInt(value);
    }
}
