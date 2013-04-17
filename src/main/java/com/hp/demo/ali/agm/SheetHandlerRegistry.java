package com.hp.demo.ali.agm;

import org.hp.almjclient.services.impl.ProjectServicesFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by panuska on 3/14/13.
 */
public class SheetHandlerRegistry {

    private Map<String, SheetHandler> registry;

    static private ProjectServicesFactory factory;

    public SheetHandlerRegistry(ProjectServicesFactory factory) {
        registry = new HashMap<String, SheetHandler>(20);
        this.factory = factory;
    }

    static ProjectServicesFactory getFactory() {
        return factory;
    }

    public void registerHandler(String name, SheetHandler handler) {
        registry.put(name, handler);
    }

    public SheetHandler getHandler(String name) {
        return registry.get(name);
    }
}
