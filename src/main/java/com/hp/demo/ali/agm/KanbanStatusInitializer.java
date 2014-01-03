package com.hp.demo.ali.agm;

import com.hp.demo.ali.excel.AgmEntityIterator;
import com.hp.demo.ali.rest.AgmRestService;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.JSONValue;
import org.hp.almjclient.exceptions.ALMRestException;
import org.hp.almjclient.exceptions.RestClientException;
import org.hp.almjclient.model.marshallers.Entity;
import org.hp.almjclient.services.KanbanStatusConfigurationService;

/**
 * Created by panuska on 6/26/13.
 */
public class KanbanStatusInitializer extends EntityHandler {
    @Override
    public Entity row(Entity entity) {
        try {
            int excelTeamId = entity.getId();
            Entity response = super.row(entity);
            int agmId = response.getId();

            KanbanStatusConfigurationService service = AgmRestService.getKanbanStatusConfigurationService();

            String result = service.getKanbanStatuses(agmId);
            parseKanbanStatusEntities(result, sheetName+"#"+excelTeamId);
            return response;
        } catch (ALMRestException | RestClientException e) {
            throw new IllegalStateException(e);
        }
    }

    protected void parseKanbanStatusEntities(String result, String excelTeamId) {
        JSONObject kanbanStatuses = (JSONObject) JSONValue.parse(result);
        JSONArray statuses = (JSONArray) kanbanStatuses.get("entities");

        _parseSubStatuses(statuses, excelTeamId);
    }
    private void _parseSubStatuses(JSONArray statuses, String excelTeamId) {
        for (Object kanbanStatus : statuses) {
            JSONObject status = (JSONObject) kanbanStatus;
            String statusId = status.get("KS_ID").toString();
            String name = (String)status.get("KS_NAME");
            AgmEntityIterator.putReference("ks#"+excelTeamId+"#"+name, statusId);
            JSONArray subStatuses = (JSONArray) status.get("SUBSTATUSES");
            if (subStatuses != null) {
                _parseSubStatuses(subStatuses, excelTeamId);
            }
        }
    }
}
