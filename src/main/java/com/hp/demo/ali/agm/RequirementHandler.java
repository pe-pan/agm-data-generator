package com.hp.demo.ali.agm;

import com.hp.demo.ali.Settings;
import com.hp.demo.ali.tools.EntityTools;
import org.apache.log4j.Logger;
import org.hp.almjclient.exceptions.ALMRestException;
import org.hp.almjclient.exceptions.RestClientException;
import org.hp.almjclient.model.marshallers.Entity;

/**
 * Created by panuska on 4/16/13.
 */
public class RequirementHandler extends AbstractBacklogItemHandler {
    private static Logger log = Logger.getLogger(RequirementHandler.class.getName());

    private int firstReqId = 0;

    @Override
    public Entity row(Entity entity) {
        try {
            String excelId = EntityTools.getField(entity, "id");
            Entity response = super.row(entity);
            String agmId = response.getId().toString();
            findBacklogItem(excelId, agmId);

                String featureStatus = EntityTools.getField(entity, "feature-status");
                if (featureStatus != null) {                                                //todo bug of AgM REST? feature status was not set when creating the entity!
                    int agmIdI = response.getId();
                    entity.removeAllFields();
                    entity.setId(agmIdI);
                    entity.setFieldValue("feature-status", featureStatus);
                    log.debug("Updating feature's status: "+EntityTools.entityToString(entity));
                    response = CRUDService.update(entity);
                    log.debug("Feature's status updated: "+EntityTools.entityToString(response));
                }
            if (firstReqId == 0) {   // remember req IDs
                firstReqId = Integer.parseInt(agmId);
                Settings.getSettings().setFirstRequirementNumber(firstReqId);
                log.info("First requirement ID: "+firstReqId);
            }
            return response;
        } catch (RestClientException | ALMRestException e) {
            throw new IllegalStateException(e);
        }
    }

}
