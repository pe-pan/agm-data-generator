package com.hp.demo.ali.excel;

import com.hp.demo.ali.entity.*;
import org.apache.poi.ss.usermodel.Sheet;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by panuska on 10/29/12.
 */
public class EntityIterator implements Iterable, Iterator {

    private RowIterator rowIterator;
    private String[] fieldNames;
    private List<String> referenceColumns = new LinkedList<String>();

    private static ObjectFactory entityFactory = new ObjectFactory();
    public static final String NULL = "null";

    public EntityIterator(Sheet sheet) {
        rowIterator = new RowIterator(sheet);
        if (!rowIterator.hasNext()) {
            throw new IllegalStateException("There is no first line in the sheet '"+sheet.getSheetName()+"'. It has to contain name of field names!");
        }
        fieldNames = rowIterator.next();
        for (int i = 0; i < fieldNames.length; i++) {
            String fieldName = fieldNames[i];
            if (fieldName.charAt(0) == '#') {        // todo make it configurable
                fieldName = fieldName.substring(1);  // remove the prefix
                fieldNames[i] = fieldName;           // store the corrected field name
                referenceColumns.add(fieldName);     // also store it as a column containing a reference
            }
        }
    }

    @Override
    public Iterator iterator() {
        return this;
    }

    @Override
    public boolean hasNext() {
        return rowIterator.hasNext();
    }

    @Override
    public Entity next() {
        String[] row = rowIterator.next();
        Entity entity = entityFactory.createEntity();
        Fields fields = entityFactory.createFields();
        entity.setFields(fields);
        List<Field> fieldList = fields.getField();
        for (int i = 0; i < fieldNames.length; i++) {
            String stringValue = row[i];
            if (NULL.equals(stringValue)) { // skip if "null" is in Excel
                continue;
            }
            String fieldName = fieldNames[i];
            Field field = entityFactory.createField();
            field.setName(fieldName);
            Value value = entityFactory.createValue();
            value.setValue(stringValue);
            field.setValue(value);
            fieldList.add(field);
        }
        return entity;
    }

    @Override
    public void remove() {
        rowIterator.remove();
    }

    public List<String> getReferenceColumns() {
        return referenceColumns;
    }
}
