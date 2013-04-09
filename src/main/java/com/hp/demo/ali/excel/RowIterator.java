package com.hp.demo.ali.excel;

import org.apache.log4j.Logger;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

import java.util.*;

/**
 * Created by panuska on 10/18/12.
 */
public class RowIterator<E> implements Iterable, Iterator {
    private static Logger log = Logger.getLogger(RowIterator.class.getName());

    private Iterator<Row> iterator;
    private static DataFormatter formatter = new DataFormatter(true);

    private String[] buffer;

    RowIterator (Sheet sheet) {
        iterator = sheet.iterator();
    }

    /**
     * Transforms the row content into an array of string values and (unlike poi library):
     * 1. puts in the array also empty (blank/null) cells (does not skip them)
     * 2. removes the empty ending cells (trims the row)
     * @param row
     * @return
     */
    private String[] _readLine(Row row) {
        Iterator<Cell> cellIterator = row.cellIterator();
        List<String> cells = new LinkedList<String>();
        // transform iterator to list
        while (cellIterator.hasNext()) {
            Cell cell =  cellIterator.next();
            cells.add(cell.getColumnIndex(), formatter.formatCellValue(cell).trim());
        }
        // clean the ending "empty" columns
        ListIterator<String> valueIterator = cells.listIterator(cells.size());
        while (valueIterator.hasPrevious()) {
            String value = valueIterator.previous();
            if (value.length() == 0) {
                valueIterator.remove();
            } else {
                break; // once first non-empty value found, leave it
            }
        }
        return cells.toArray(new String[cells.size()]);
    }

    @Override
    public boolean hasNext() {
        return iterator.hasNext();
    }

    @Override
    public String[] next() {
        Row row = iterator.next();

        while(row.getSheet().getWorkbook().getFontAt(row.getCell(row.getFirstCellNum()).getCellStyle().getFontIndex()).getBoldweight() == Font.BOLDWEIGHT_BOLD) {
            log.debug("At sheet "+row.getSheet().getSheetName()+" skipping row number "+row.getRowNum());
            row = iterator.next();   // todo if the very last row is bold, this result in exception!
        }

        // initialize buffer
        int size = row.getLastCellNum(); // the very first line in the sheet must be full (contains all values)
        if (buffer == null) {
            buffer = _readLine(row);
            return Arrays.copyOf(buffer, buffer.length);  // return copy of buffer as buffer is used as static cache
        }

        String[] array = new String[buffer.length];

        for (int i = 0; i < buffer.length; i++) {
            Cell cell = row.getCell(i, Row.RETURN_BLANK_AS_NULL);
            if (cell == null) {
                array[i] = buffer[i];
            } else {
                array[i] = formatter.formatCellValue(cell).trim();
            }
            buffer[i] = array[i]; //remember the value in buffer
        }
        return array;
    }

    @Override
    public void remove() {
        iterator.remove();
    }

    @Override
    public Iterator iterator() {
        return this;
    }
}
