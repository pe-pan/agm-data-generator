package com.hp.demo.ali.agm;

import org.hp.almjclient.model.marshallers.Entity;

/**
 * Created by panuska on 3/14/13.
 */
public interface SheetHandler {

    void init(String sheetName);

    Entity row(Entity entity);

    void terminate();
}
