package com.hp.demo.ali.agm;

import com.hp.demo.ali.JobLogger;
import com.hp.demo.ali.excel.AgmEntityIterator;
import com.hp.demo.ali.rest.AgmRestService;
import org.apache.log4j.Logger;
import org.hp.almjclient.exceptions.ALMRestException;
import org.hp.almjclient.exceptions.RestClientException;
import org.hp.almjclient.model.marshallers.Entity;

/**
 * Created by panuska on 3/14/13.
 */
public class EntityHandler extends AbstractSheetHandler {
    private static Logger log = Logger.getLogger(EntityHandler.class.getName());

    protected AgmRestService CRUDService;

    public EntityHandler() {
        CRUDService = AgmRestService.getCRUDService();
    }

    @Override
    public void init(String sheetName) {
        super.init(sheetName);
        log.info("Generating entity: "+sheetName);
    }

    @Override
    public Entity row(Entity entity) {
        try {
            String excelId = entity.getFieldValue("id").getValue();
            entity.removeField("id");      // remove the original Entity ID (the one written in Excel); if not removed here, it'll lead to NumberFormatException on the row below
            log.debug("Creating " + entity);
            Entity response = AgmRestService.getCRUDService().create(entity);
            log.debug("Created "+response);
            String agmId = response.getId().toString();
            AgmEntityIterator.putReference(sheetName + "#" + excelId, agmId);
            JobLogger.writeLogLine(sheetName, agmId);
            return response;
        } catch (RestClientException | ALMRestException e) {
            throw new IllegalStateException(e);
        }
    }
}
