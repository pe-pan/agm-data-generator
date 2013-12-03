package com.hp.demo.ali.excel;

import org.apache.poi.ss.usermodel.Sheet;

import java.util.Iterator;

/**
 * Created by panuska on 10/29/12.
 */
public class EntityIterator<E> implements Iterable, Iterator {

    protected RowIterator rowIterator;
    protected String[] fieldNames;

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
        ExcelEntity entity = new ExcelEntity();
        for (int i = 0; i < fieldNames.length; i++) {
            String stringValue = row[i];
            if (NULL.equals(stringValue)) { // skip if "null" is in Excel
                continue;
            }
            String fieldName = fieldNames[i];
            entity.setFieldValue(fieldName, stringValue);
        }
        return (E)entity;
    }

    @Override
    public void remove() {
        rowIterator.remove();
    }

}
