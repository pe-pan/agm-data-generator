package com.hp.demo.ali.agm;

import org.apache.log4j.Logger;
import org.hp.almjclient.model.marshallers.Entity;

import java.util.List;

/**
 * Created by panuska on 3/14/13.
 */
abstract public class AbstractSheetHandler implements SheetHandler {

    static Logger log = Logger.getLogger(SheetHandler.class.getName());

    protected String sheetName;

    @Override
    public void init(String sheetName) {
        this.sheetName = sheetName;
    }

    @Override
    public void terminate() {
    }
}
