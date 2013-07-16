package com.hp.demo.ali.agm;

import com.hp.demo.ali.DataGenerator;
import com.hp.demo.ali.excel.AgmEntityIterator;
import org.apache.log4j.Logger;
import org.hp.almjclient.exceptions.ALMRestException;
import org.hp.almjclient.exceptions.RestClientException;
import org.hp.almjclient.model.marshallers.Entity;
import org.hp.almjclient.services.EntityCRUDService;

/**
 * Created by panuska on 3/14/13.
 */
public class EntityHandler extends AbstractSheetHandler {
    private static Logger log = Logger.getLogger(EntityHandler.class.getName());

    protected EntityCRUDService CRUDService;

    @Override
    public void init(String sheetName) {
        super.init(sheetName);
        log.info("Generating entity: "+sheetName);
        CRUDService = SheetHandlerRegistry.getFactory().getEntityCRUDService(sheetName);
    }

    @Override
    public Entity row(Entity entity) {
        try {
            String excelId = entity.getFieldValue("id").getValue();
            entity.removeField("id");      // remove the original Entity ID (the one written in Excel); if not removed here, it'll lead to NumberFormatException on the row below
            log.debug("Creating " + entity);
            Entity response = CRUDService.create(entity);
            log.debug("Created "+response);
            String agmId = response.getId().toString();
            AgmEntityIterator.putReference(sheetName + "#" + excelId, agmId);
            DataGenerator.writeLogLine(sheetName, agmId);
            return response;
        } catch (RestClientException e) {
            throw new IllegalStateException(e);
        } catch (ALMRestException e) {
            throw new IllegalStateException(e);
        }
    }
}
