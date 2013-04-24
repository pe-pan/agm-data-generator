package com.hp.demo.ali.excel;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by panuska on 10/12/12.
 */
public class ExcelReader {

    private Workbook workbook;

    public ExcelReader(InputStream workbookStream) {
        try {
            workbook = WorkbookFactory.create(workbookStream);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        } catch (InvalidFormatException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public Sheet getSheet(String sheetName) {
        return workbook.getSheet(sheetName);
    }

    /**
     * Returns list of sheets whose name starts with a lower-cased character (a-z).
     * @return
     */
    public List<Sheet> getAllEntitySheets() {
        int size = workbook.getNumberOfSheets();
        List<Sheet> sheets = new LinkedList<Sheet>();
        for (int i = 0; i < size; i++) {
            Sheet sheet = workbook.getSheetAt(i);
            char firstChar = sheet.getSheetName().charAt(0);
            if ('a' <= firstChar && firstChar <= 'z') {       //todo make it configurable
                sheets.add(sheet);
            }
        }
        return sheets;
    }
}