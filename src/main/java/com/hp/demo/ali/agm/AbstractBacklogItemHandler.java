package com.hp.demo.ali.agm;

import org.hp.almjclient.exceptions.ALMRestException;
import org.hp.almjclient.exceptions.RestClientException;
import org.hp.almjclient.model.marshallers.Entities;
import org.hp.almjclient.model.marshallers.Entity;
import org.hp.almjclient.model.marshallers.favorite.Filter;
import org.hp.almjclient.services.EntityCRUDService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by panuska on 4/16/13.
 */
public abstract class AbstractBacklogItemHandler extends EntityHandler {

    protected Entity _backlogItem;
    protected String _backlogItemId;
//    protected EntityCRUDService _CRUDService;

    protected static Map<String, String> featureMap = new HashMap<String, String>();

    /**
     * Sets _backlogItem, _backlogItemId and fields for later use.
     * @param agmId
     * @throws ALMRestException
     * @throws RestClientException
     */
    protected void _findBacklogItem(String agmId) throws ALMRestException, RestClientException {
        Filter filter = new Filter("release-backlog-item");
        filter.addQueryClause("entity-id", agmId);
//        _CRUDService = SheetHandlerRegistry.getFactory().getEntityCRUDService("release-backlog-item");
        Entities entities = CRUDService.readCollection(filter);
        _backlogItem = entities.getEntityList().get(0);
        _backlogItemId = _backlogItem.getId().toString();
    }

    protected void _updateBacklogItem(String featureId) throws ALMRestException, RestClientException {
        String themeId = featureMap.get(featureId);                                // get theme ID
        _backlogItem.setFieldValue("feature-id", featureId);
        _backlogItem.setFieldValue("theme-id", themeId);
        log.debug("Updating "+_backlogItem);
        CRUDService.update(_backlogItem);                                           // update backlog item
    }

    protected void _addBacklogItemId(List<String> returnValue) {
        returnValue.add("apmuiservice#" + sheetName + "#");
        returnValue.add(_backlogItemId);
    }
}
