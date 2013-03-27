package com.hp.demo.ali.tools;

import com.hp.demo.ali.entity.Entities;
import com.hp.demo.ali.entity.Entity;
import com.hp.demo.ali.entity.Field;
import com.hp.demo.ali.entity.Fields;
import com.hp.demo.ali.entity.ObjectFactory;
import com.hp.demo.ali.entity.Value;
import org.apache.log4j.Logger;
import org.hp.almjclient.exceptions.FieldNotFoundException;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by panuska on 10/30/12.
 */
public class EntityTools {

    private static Logger log = Logger.getLogger(EntityTools.class.getName());

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

    public static Date getFieldDateValue(Entity entity, String fieldName, SimpleDateFormat sdf) {
        Field field = getField(entity, fieldName);
        try {
            return field == null ? null : sdf.parse(field.getValue().getValue());
        } catch (ParseException e) {
            throw new IllegalStateException(e);
        }
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

    private final static JAXBContext context;
    static {
        try {
            context = JAXBContext.newInstance(Entity.class);
        } catch (JAXBException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String toXml(Entity entity) {
        try {
            final Marshaller marshaller = context.createMarshaller();
            final StringWriter stringWriter = new StringWriter();
            marshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);
            // Marshal the javaObject and write the XML to the stringWriter
            marshaller.marshal(entity, stringWriter);
            String returnValue = stringWriter.toString();
            log.debug(returnValue);
            return returnValue;
        } catch (JAXBException e) {
            throw new IllegalStateException(e);
        }
    }

    public static Entity fromXml(String xmlEntity) {
        try {
            ByteArrayInputStream input = new ByteArrayInputStream(xmlEntity.getBytes());
            Unmarshaller u = context.createUnmarshaller();
            return (Entity) u.unmarshal(input);
        } catch (JAXBException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String toUrlParameters(Entity entity) {        //todo similar to RestClient#serializeParameters(); remove code duplicate
        StringBuilder urlParameters = new StringBuilder("?");
        List<Field> fields = entity.getFields().getField();
        for (Field field : fields) {
            urlParameters.append(field.getName()).append('=').append(/*URLEncoder.encode(*/field.getValue().getValue()/*, "UTF-8")*/).append('&');
        }
        return urlParameters.substring(0, urlParameters.length()-1);
    }

    public static org.hp.almjclient.model.marshallers.Entity convertEntity(Entity entity) {
        List<Field> fields = entity.getFields().getField();
        Map<String, Object> convertedFields = new HashMap<String, Object>(fields.size());
        for (Field field : fields) {
            convertedFields.put(field.getName(), field.getValue().getValue());
        }
        return new org.hp.almjclient.model.marshallers.Entity(entity.getType(),convertedFields);
    }

    private static ObjectFactory factory = new ObjectFactory();

    public static Entity convertEntity(org.hp.almjclient.model.marshallers.Entity entity) {
        Set<String> fieldNames = entity.getFieldsKeySet();
        Entity returnValue = factory.createEntity();
        Fields fields = factory.createFields();
        List<Field> fieldList = fields.getField();
        try {
            for (String fieldName : fieldNames) {
                String valueString = entity.getFieldValue(fieldName).getValue();
                Field field = factory.createField();
                Value value = factory.createValue();
                field.setName(fieldName);
                value.setValue(valueString);
                field.setValue(value);
                fieldList.add(field);
            }
        } catch (FieldNotFoundException e) {
            throw new IllegalStateException(e);
        }
        returnValue.setFields(fields);
        return returnValue;
    }

    public static Entities convertEntities(org.hp.almjclient.model.marshallers.Entities entities) {
        Entities returnValue = factory.createEntities();
        List<org.hp.almjclient.model.marshallers.Entity> entityList = entities.getEntityList();
        for (org.hp.almjclient.model.marshallers.Entity entity : entityList) {
            returnValue.getEntity().add(convertEntity(entity));
        }
        return returnValue;
    }
}
