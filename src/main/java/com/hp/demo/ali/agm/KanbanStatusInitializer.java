package com.hp.demo.ali.agm;

import com.hp.demo.ali.excel.AgmEntityIterator;
import com.hp.demo.ali.rest.AgmRestService;
import org.hp.almjclient.exceptions.ALMRestException;
import org.hp.almjclient.exceptions.RestClientException;
import org.hp.almjclient.model.marshallers.Entity;
import org.hp.almjclient.services.KanbanStatusConfigurationService;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

/**
 * Created by panuska on 6/26/13.
 */
public class KanbanStatusInitializer extends EntityHandler {
    @Override
    public Entity row(Entity entity) {
        try {
            String originalId = entity.getId().toString();
            Entity response = super.row(entity);
            String agmId = response.getId().toString();

            KanbanStatusConfigurationService service = AgmRestService.getKanbanStatusConfigurationService();

            String result = service.getKanbanStatuses(new Integer(agmId));
            JSONObject kanbanStatuses = (JSONObject) JSONValue.parse(result);
            JSONArray statuses = (JSONArray) kanbanStatuses.get("entities");

            for (Object kanbanStatus : statuses) {
                JSONObject status = (JSONObject) kanbanStatus;
                String statusId = status.get("KS_ID").toString();
                String name = (String)status.get("KS_NAME");
                AgmEntityIterator.putReference("ks#"+sheetName+"#"+originalId+"#"+name, statusId);
            }
            return response;
        } catch (ALMRestException | RestClientException e) {
            throw new IllegalStateException(e);
        }
    }
}
