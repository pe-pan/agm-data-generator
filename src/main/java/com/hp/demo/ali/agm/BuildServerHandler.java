package com.hp.demo.ali.agm;

import org.hp.almjclient.exceptions.FieldNotFoundException;
import org.hp.almjclient.model.marshallers.Entity;

/**
 * Created by panuska on 4/16/13.
 */
public class BuildServerHandler extends EntityHandler {

    private static String buildServerName;                              //todo as this is static, it can handle only one build server entity

    public static String getBuildServerName() {
        return buildServerName;
    }

    @Override
    public Entity row(Entity entity) {
        try {
            buildServerName = entity.getFieldValue("name").getValue();
            return super.row(entity);
        } catch (FieldNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

}
