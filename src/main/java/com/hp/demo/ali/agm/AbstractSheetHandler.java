package com.hp.demo.ali.agm;

/**
 * Created by panuska on 3/14/13.
 */
abstract public class AbstractSheetHandler implements SheetHandler {

    protected String sheetName;

    @Override
    public void init(String sheetName) {
        this.sheetName = sheetName;
    }

    @Override
    public void terminate() {
    }
}
