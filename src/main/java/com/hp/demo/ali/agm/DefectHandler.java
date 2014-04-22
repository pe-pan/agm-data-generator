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
public class DefectHandler extends AbstractBacklogItemHandler {
    private static Logger log = Logger.getLogger(DefectHandler.class.getName());

    private int firstDefId = 0;

    @Override
    public Entity row(Entity entity) {
        try {
            String excelId = EntityTools.getField(entity, "id");
            Entity response = super.row(entity);
            String agmId = response.getId().toString();
            findBacklogItem(excelId, agmId);

            if (firstDefId == 0) {
                // remember def IDs
                firstDefId = Integer.parseInt(agmId);
                Settings.getSettings().setFirstDefectNumber(firstDefId);
                log.info("First defect ID: "+firstDefId);
            }
            return response;
        } catch (ALMRestException | RestClientException e) {
            throw new IllegalStateException(e);
        }
    }
}
