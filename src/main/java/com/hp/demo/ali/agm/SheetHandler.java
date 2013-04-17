package com.hp.demo.ali.agm;

import org.hp.almjclient.model.marshallers.Entity;

import java.util.List;

/**
 * Created by panuska on 3/14/13.
 */
public interface SheetHandler {

    void init(String sheetName);

    List<String> row(Entity entity);

    void terminate();
}
