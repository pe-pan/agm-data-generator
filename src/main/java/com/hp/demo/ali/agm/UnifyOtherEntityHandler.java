package com.hp.demo.ali.agm;

import com.hp.demo.ali.rest.AgmRestService;
import org.apache.log4j.Logger;
import org.hp.almjclient.exceptions.ALMRestException;
import org.hp.almjclient.exceptions.RestClientException;
import org.hp.almjclient.model.marshallers.Entities;
import org.hp.almjclient.model.marshallers.Entity;
import org.hp.almjclient.model.marshallers.favorite.Filter;

/**
 * Created by panuska on 23.6.15.
 */
public class UnifyOtherEntityHandler extends EntityHandler {
    private static Logger log = Logger.getLogger(UnifyOtherEntityHandler.class.getName());

    private String uniqueParameter; // parameter whose value must be unique across all workspaces

    public UnifyOtherEntityHandler(String uniqueParameter) {
        super();
        this.uniqueParameter = uniqueParameter;
    }

    public Entity row(Entity entity) {
        try { //there can be only a single build-server/scm-repo having location URL equal to this value; if there is more such (in different workspaces), the value of others must be reset to a unique value
            Filter filter = new Filter(sheetName);
            String uniqueValue = entity.getFieldValue(uniqueParameter).getValue();   // this value must be made unique
            filter.addQueryClause(uniqueParameter, uniqueValue);
            log.info("Looking for " + sheetName + " having parameter " + uniqueParameter + " equal to " + uniqueValue);
            Entities buildServers = AgmRestService.getCRUDService().readCollection(filter);

            log.info("There is " + buildServers.getTotalResults() + " such " + sheetName + "; resetting its "+uniqueParameter+" parameter to unique value");
            for (Entity buildServer : buildServers.getEntityList()) {
                String buildServerWorkspaceId = buildServer.getFieldValue("product-group-id").getValue();
                String newValue = uniqueValue + "#value-changed-by-ADG#" + buildServerWorkspaceId;
                log.info("Parameter " + uniqueParameter+" of "+sheetName + ":" + buildServer.getId() + " (from workspace "+buildServerWorkspaceId+") setting into " + newValue);
                buildServer.getFieldValue(uniqueParameter).setValue(newValue);
                AgmRestService.getCRUDService().update(buildServer);
            }
        } catch (ALMRestException | RestClientException e) {
            log.error("Cannot make the parameter unique!", e);
        }
        return super.row(entity);
    }
}
