package com.hp.demo.ali.agm;

import com.hp.demo.ali.Settings;
import com.hp.demo.ali.excel.AgmEntityIterator;
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
            String excelId = entity.getFieldValue("id").getValue();
            Entity response = super.row(entity);
            String agmId = response.getId().toString();
            _findBacklogItem(agmId);

            String typeId = entity.getFieldValue("type-id").getValue();
            if ("71".equals(typeId)) { // feature
                String themeId = entity.getFieldValue("parent-id").getValue();
                if (themeId != null) {
                    featureMap.put(agmId, themeId);                                            // remember theme ID
                }
                _backlogItem.setFieldValue("theme-id", themeId);
                CRUDService.update(_backlogItem);                                           // update backlog item
            } else if ("70".equals(typeId)) { // user story
                String featureId = entity.getFieldValue("parent-id").getValue();
                _updateBacklogItem(featureId);
            }
            if (firstReqId == 0) {   // remember req IDs
                firstReqId = Integer.parseInt(agmId);
                Settings.getSettings().setFirstRequirementNumber(firstReqId);
                log.info("First requirement ID: "+firstReqId);
            }
            AgmEntityIterator.putReference("apmuiservice#" + sheetName + "#" + excelId, _backlogItemId);
            return response;
        } catch (RestClientException e) {
            throw new IllegalStateException(e);
        } catch (ALMRestException e) {
            throw new IllegalStateException(e);
        }
    }

}
