package com.hp.demo.ali.agm;

import com.hp.demo.ali.JobLogger;
import com.hp.demo.ali.Settings;
import com.hp.demo.ali.excel.AgmEntityIterator;
import com.hp.demo.ali.rest.AgmRestService;
import com.hp.demo.ali.tools.EntityTools;
import org.apache.log4j.Logger;
import org.hp.almjclient.exceptions.ALMRestException;
import org.hp.almjclient.exceptions.FieldNotFoundException;
import org.hp.almjclient.exceptions.RestClientException;
import org.hp.almjclient.model.marshallers.Entity;
import org.hp.almjclient.model.marshallers.favorite.Filter;

import java.util.Set;

/**
 * Created by panuska on 3/14/13.
 */
public class EntityHandler extends AbstractSheetHandler {
    private static Logger log = Logger.getLogger(EntityHandler.class.getName());
    private static int updatedEntities = 0, createdEntities = 0, reconstructedEntities = 0;

    protected AgmRestService CRUDService;

    public EntityHandler() {
        CRUDService = AgmRestService.getCRUDService();
    }

    @Override
    public void init(String sheetName) {
        super.init(sheetName);
        log.info((Settings.getSettings().isDeleteAll() ? "Generating" : "Refreshing")+" entity: "+sheetName);
    }

    @Override
    public Entity row(Entity entity) {
        try {
            String excelId = EntityTools.getField(entity, "id");
            entity.removeField("id");      // remove the original Entity ID (the one written in Excel); if not removed here, it'll lead to NumberFormatException on the row below
            log.debug("Creating/updating " + entity + ": " + EntityTools.entityToString(entity));
            String agmId = AgmEntityIterator.dereference(sheetName+"#"+excelId);
            Entity response;
            if (agmId == null) {
                response = createEntity(entity, excelId);
            } else {
                response = updateEntity(entity, excelId, Integer.parseInt(agmId));
            }
            return response;
        } catch (RestClientException e) {
            throw new IllegalStateException(e);
        } catch (ALMRestException e) {
            log.error(e.getExceptionProperties());
            log.error(e.getId());
            throw new IllegalStateException(e);
        }
    }

    private Filter entityToFilter(Entity entity) {
        Filter filter = new Filter(entity.getType());
        try {
            Set<String> fieldNames = entity.getFieldsKeySet();
            for(String fieldName : fieldNames) {
                String fieldValue = entity.getFieldValue(fieldName).getValue();
                filter.addQueryClause(fieldName, fieldValue);
            }
        } catch (FieldNotFoundException e) {
            log.error("Should never happen", e);
        }
        return filter;
    }

    protected Entity createEntity(Entity entity, String excelId) throws ALMRestException, RestClientException {
        Entity response;
//        try {
            response = AgmRestService.getCRUDService().create(entity);
            createdEntities++;
            log.debug("Created("+createdEntities+") "+response+": "+ EntityTools.entityToString(response));
            //todo this is not transactional (create and writeLogLine should be a single transaction)
/*
        // todo do not try to reconstruct the entity; it does not work anyway
        // todo to make it work, one would have to create the filter other way (e.g. remove the Description field from the filter)
        } catch (ALMRestException | RestClientException e) {
            Filter filter = entityToFilter(entity);
            Entities entities = AgmRestService.getCRUDService().readCollection(filter);
            if (entities.getTotalResults() != 1) {
                throw new IllegalStateException("Cannot create an entity; not sure if already exists in the tenant "+entity, e);
            }
            response = entities.getEntityList().get(0);
            reconstructedEntities++;
            log.debug("Reconstructed("+reconstructedEntities+") "+response+": "+ EntityTools.entityToString(response));
        }
*/
        String agmId = response.getId().toString();
        JobLogger.writeLogLine(sheetName, agmId, excelId);
        AgmEntityIterator.putReference(sheetName + "#" + excelId, agmId);
        return response;
    }

    protected Entity updateEntity(Entity entity, String excelId, int agmId) throws ALMRestException, RestClientException {
        Entity response;
        entity.setId(agmId);
        try {
            response = AgmRestService.getCRUDService().update(entity);
            updatedEntities++;
            log.debug("Updated("+updatedEntities+") "+response+": "+ EntityTools.entityToString(response));
        } catch (ALMRestException | RestClientException e) {
            log.debug("Update failed! Trying to create!", e);
            response = createEntity(entity, excelId);
        }
        return response;
    }

    public static void printStatistics() {
        log.info("Created entities: "+createdEntities);
        log.info("Updated entities: "+updatedEntities);
        log.info("Reconstructed entities: "+reconstructedEntities);
    }
}
