package com.hp.demo.ali.tools;

import org.apache.log4j.Logger;
import org.hp.almjclient.exceptions.FieldNotFoundException;
import org.hp.almjclient.model.marshallers.Entity;

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
            try {
                String value = entity.getFieldValue(field).getValue();
                sb.append("'").append(field).append("': '").append(value).append("', ");
            } catch (FieldNotFoundException e) {
                log.debug("Should not happen; there should be the field: "+field, e);
            }
        }
        sb.delete(sb.length()-2, sb.length());
        sb.append("}");
        return sb.toString();
    }

}
