package com.hp.demo.ali.agm;

import com.hp.demo.ali.tools.EntityTools;
import org.hp.almjclient.exceptions.ALMRestException;
import org.hp.almjclient.exceptions.FieldNotFoundException;
import org.hp.almjclient.exceptions.RestClientException;
import org.hp.almjclient.model.marshallers.Entity;

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
                ksStatusId = EntityTools.removeField(entity, "kanban-status-id");
                id = entity.getId();
            } catch (FieldNotFoundException e) {
                // ks status not to be set
            }

            CRUDService.update(entity);

            if (ksStatusId != null) { // ks status to be set
                entity.removeAllFields();
                entity.setFieldValue("kanban-status-id", ksStatusId);
                entity.setId(id);
                CRUDService.update(entity);
            }
        } catch (RestClientException | ALMRestException e) {
            throw new IllegalStateException(e);
        }
        return null;
    }
}
