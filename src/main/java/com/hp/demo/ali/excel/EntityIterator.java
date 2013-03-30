package com.hp.demo.ali.excel;

import com.hp.demo.ali.entity.*;
import org.apache.poi.ss.usermodel.Sheet;

import java.util.Iterator;
import java.util.List;

/**
 * Created by panuska on 10/29/12.
 */
public class EntityIterator<E> implements Iterable, Iterator {

    protected RowIterator rowIterator;
    protected String[] fieldNames;

    private static ObjectFactory entityFactory = new ObjectFactory();
    public static final String NULL = "null";

    public EntityIterator(Sheet sheet) {
        rowIterator = new RowIterator(sheet);
        if (!rowIterator.hasNext()) {
            throw new IllegalStateException("There is no first line in the sheet '"+sheet.getSheetName()+"'. It has to contain name of field names!");
        }
        fieldNames = rowIterator.next();
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
    public E next() {
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
        return (E)entity;
    }

    @Override
    public void remove() {
        rowIterator.remove();
    }

}
