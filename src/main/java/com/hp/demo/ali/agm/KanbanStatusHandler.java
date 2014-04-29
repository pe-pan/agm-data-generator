package com.hp.demo.ali.agm;

import com.hp.demo.ali.rest.AgmRestService;
import com.hp.demo.ali.tools.EntityTools;
import org.apache.log4j.Logger;
import org.hp.almjclient.exceptions.ALMRestException;
import org.hp.almjclient.exceptions.RestClientException;
import org.hp.almjclient.model.marshallers.Entity;
import org.hp.almjclient.services.KanbanStatusConfigurationService;

/**
 * Created by panuska on 17.12.13.
 */
public class KanbanStatusHandler extends KanbanStatusInitializer {
    private static Logger log = Logger.getLogger(KanbanStatusHandler.class.getName());

    int previousTeamId = 0;
    String excelTeamId = "NOT_INITIALIZED";
    String kanbanStatuses = "[";                     // contains the set of statuses

    @Override
    public Entity row(Entity entity) {
        int teamId = EntityTools.getIntField(entity, "KS_TEAM_ID");
        if (previousTeamId == 0) {
            previousTeamId = teamId;
            excelTeamId = EntityTools.getField(entity, "team-id");
        }
        if (previousTeamId != teamId) {  // in this row, there are new set of statuses
            terminate();                 // finish the previous set
            previousTeamId = teamId;     // initialize the new set
            excelTeamId = EntityTools.getField(entity, "team-id");
            kanbanStatuses = "[";        // also init
        }
        entity.removeField("team-id");
        entity.removeField("id");
        kanbanStatuses = kanbanStatuses+EntityTools.entityToString(entity)+",";
        return null;
    }

    @Override
    public void terminate() {
        try {
            KanbanStatusConfigurationService service = AgmRestService.getKanbanStatusConfigurationService();
            kanbanStatuses = kanbanStatuses.substring(0, kanbanStatuses.length()-1) + "]";
            log.debug("Setting kanban statuses: "+kanbanStatuses);
            String result = service.configureKanbanSubStatuses(previousTeamId, kanbanStatuses);
            log.debug("Receiving: "+result);
            parseKanbanStatusEntities(result, excelTeamId);
        } catch (RestClientException | ALMRestException e) {
            throw new IllegalStateException(e);
        }
    }
}
