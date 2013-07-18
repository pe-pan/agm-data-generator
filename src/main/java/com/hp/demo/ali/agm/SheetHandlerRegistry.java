package com.hp.demo.ali.agm;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by panuska on 3/14/13.
 */
public class SheetHandlerRegistry {

    private Map<String, SheetHandler> registry;

    public SheetHandlerRegistry() {
        registry = new HashMap<>(20);
    }

    public void registerHandler(String name, SheetHandler handler) {
        registry.put(name, handler);
    }

    public SheetHandler getHandler(String name) {
        return registry.get(name);
    }
}
