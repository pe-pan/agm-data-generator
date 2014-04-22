package com.hp.demo.ali.agm;

import com.hp.demo.ali.Settings;
import com.hp.demo.ali.excel.AgmEntityIterator;
import com.hp.demo.ali.tools.EntityTools;
import org.apache.log4j.Logger;
import org.hp.almjclient.exceptions.ALMRestException;
import org.hp.almjclient.exceptions.RestClientException;
import org.hp.almjclient.model.marshallers.Entity;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by panuska on 4/16/13.
 */
public class DefectHandler extends AbstractBacklogItemHandler {
    private static Logger log = Logger.getLogger(DefectHandler.class.getName());

    private int firstDefId = 0;

    @Override
    public Entity row(Entity entity) {
        try {
            String featureId = EntityTools.removeField(entity, "parent-id");      // set feature/theme the defect belongs to

            String excelId = EntityTools.getField(entity, "id");
            Entity response = super.row(entity);
            String agmId = response.getId().toString();
            _findBacklogItem(agmId);
            _updateBacklogItem(featureId);
            AgmEntityIterator.putReference("apmuiservice#" + sheetName + "#" + excelId, _backlogItemId);

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
