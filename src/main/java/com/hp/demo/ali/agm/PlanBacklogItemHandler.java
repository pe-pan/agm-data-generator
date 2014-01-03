package com.hp.demo.ali.agm;

import com.hp.demo.ali.tools.EntityTools;
import org.hp.almjclient.exceptions.ALMRestException;
import org.hp.almjclient.exceptions.FieldNotFoundException;
import org.hp.almjclient.exceptions.RestClientException;
import org.hp.almjclient.model.marshallers.Entity;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by panuska on 3/15/13.
 */
public class PlanBacklogItemHandler extends EntityHandler {

    private static Map<String, String> statusAfterChange = new HashMap<>();
    public static String getStatusAfterChange(String id) {
        return statusAfterChange.get(id);
    }

    @Override
    public Entity row(Entity entity) {
        try {
            String ksStatusId = null;
            Integer id = null;
            try {
                ksStatusId = EntityTools.removeField(entity, "kanban-status-id");
                id = entity.getId();
                String ksStatusId2 = EntityTools.removeField(entity, "kanban-status-id-2");
                statusAfterChange.put(""+id, ksStatusId2);
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
