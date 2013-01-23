package com.hp.demo.ali.tools;

import com.hp.demo.ali.entity.Entity;
import com.hp.demo.ali.entity.Field;
import com.hp.demo.ali.entity.Fields;

import java.util.Iterator;
import java.util.List;

/**
 * Created by panuska on 10/30/12.
 */
public class EntityTools {

    public static Field removeIdField(Entity entity) {
        //todo currently, only simplified implementation; it should look for a field with name "id" and remove this one
        //now, it expects that id field is at the very first column
        return entity.getFields().getField().remove(0);
    }

    public static String getFieldValue(Entity entity, String fieldName) {
        Field field = getField(entity, fieldName);
        return field == null ? null : field.getValue().getValue();
    }

    public static int getFieldIntValue(Entity entity, String fieldName) {
        Field field = getField(entity, fieldName);
        return field == null ? 0 : Integer.parseInt(field.getValue().getValue());
    }

    public static long getFieldLongValue(Entity entity, String fieldName) {
        Field field = getField(entity, fieldName);
        return field == null ? 0 : Long.parseLong(field.getValue().getValue());
    }

    public static Field getField(Entity entity, String fieldName) {
        for (Field field : entity.getFields().getField()) {
            if (field.getName().equals(fieldName)) {
                //todo if there is more fields with the same name, it will return value of the very first one
                return field;
            }
        }
        return null;
    }
}
