package com.hp.demo.ali.tools;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Sheet;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by panuska on 11/15/12.
 */
public class SheetTools {

    private static DataFormatter formatter = new DataFormatter(true);
    public static String getStringValue(Sheet sheet, int row, int column) {
        return formatter.formatCellValue(sheet.getRow(row).getCell(column)).trim();
    }

    public static int getIntValue(Sheet sheet, int row, int column) {
        return Integer.parseInt(getStringValue(sheet, row, column));
    }

    public static long getLongValue(Sheet sheet, int row, int column) {
        return Long.parseLong(getStringValue(sheet, row, column));
    }

    public static Date getDateValue(Sheet sheet, int row, int column, SimpleDateFormat dateFormat) {
        try {
            String firstBuildValue = SheetTools.getStringValue(sheet, row, column);
            return dateFormat.parse(firstBuildValue);
        } catch (ParseException e) {
            throw new IllegalArgumentException("Value at ("+(char)('A'+column)+","+(row+1)+") of "+sheet.getSheetName()+" sheet does not follow this pattern: "+dateFormat.toPattern(), e);
        }
    }
}
