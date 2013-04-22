package com.hp.demo.ali.agm;

import com.hp.demo.ali.DataGenerator;
import org.apache.log4j.Logger;
import org.hp.almjclient.exceptions.ALMRestException;
import org.hp.almjclient.exceptions.FieldNotFoundException;
import org.hp.almjclient.exceptions.RestClientException;
import org.hp.almjclient.model.marshallers.Entity;
import org.hp.almjclient.services.EntityCRUDService;

import java.util.ArrayList;
import java.util.List;

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
    public List<String> row(Entity entity) {
        try {
            log.debug("Creating "+entity);
            Entity response = CRUDService.create(entity);
            log.debug("Created "+response);
            String agmId = response.getId().toString();
            List<String> returnValue = new ArrayList<String>(2);
            returnValue.add(sheetName + "#");
            returnValue.add(agmId);
            DataGenerator.writeLogLine(sheetName, agmId);
            return returnValue;
        } catch (RestClientException e) {
            throw new IllegalStateException(e);
        } catch (ALMRestException e) {
            throw new IllegalStateException(e);
        }
    }
}
