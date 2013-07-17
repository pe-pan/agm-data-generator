package com.hp.demo.ali.agm;

import org.hp.almjclient.exceptions.ALMRestException;
import org.hp.almjclient.exceptions.FieldNotFoundException;
import org.hp.almjclient.exceptions.RestClientException;
import org.hp.almjclient.model.marshallers.Entity;
import org.hp.almjclient.services.EntityCRUDService;

/**
 * Created by panuska on 3/15/13.
 */
public class PlanBacklogItemHandler extends EntityHandler {

    @Override
    public Entity row(Entity entity) {
        try {
            String ksStatusId = null;
            Integer id = null;
            try {
                ksStatusId = entity.getFieldValue("kanban-status-id").getValue();
                entity.removeField("kanban-status-id");
                id = entity.getId();
            } catch (FieldNotFoundException e) {
                // ks status not to be set
            }

            EntityCRUDService CRUDService = SheetHandlerRegistry.getFactory().getEntityCRUDService("release-backlog-item");
            CRUDService.update(entity);

            if (ksStatusId != null) { // ks status to be set
                entity.removeAllFields();
                entity.setFieldValue("kanban-status-id", ksStatusId);
                entity.setId(id);
                CRUDService.update(entity);
            }
        } catch (RestClientException e) {
            throw new IllegalStateException(e);
        } catch (ALMRestException e) {
            throw new IllegalStateException(e);
        }
        return null;
    }
}
