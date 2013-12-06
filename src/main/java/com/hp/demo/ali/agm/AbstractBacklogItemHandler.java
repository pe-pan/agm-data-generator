package com.hp.demo.ali.agm;

import org.apache.log4j.Logger;
import org.hp.almjclient.exceptions.ALMRestException;
import org.hp.almjclient.exceptions.RestClientException;
import org.hp.almjclient.model.marshallers.Entities;
import org.hp.almjclient.model.marshallers.Entity;
import org.hp.almjclient.model.marshallers.favorite.Filter;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by panuska on 4/16/13.
 */
public abstract class AbstractBacklogItemHandler extends EntityHandler {
    private static Logger log = Logger.getLogger(AbstractBacklogItemHandler.class.getName());

    protected Entity _backlogItem;
    protected String _backlogItemId;

    protected static Map<String, String> featureMap = new HashMap<>();

    /**
     * Sets _backlogItem, _backlogItemId and fields for later use.
     * @param agmId The entity ID used in AgM
     * @throws ALMRestException
     * @throws RestClientException
     */
    protected void _findBacklogItem(String agmId) throws ALMRestException, RestClientException {
        Filter filter = new Filter("release-backlog-item");
        filter.addQueryClause("entity-id", agmId);
        filter.addQueryClause("entity-type", sheetName);        // a user story may have the same ID as a defect
        Entities entities = CRUDService.readCollection(filter);
        assert entities.getTotalResults() == 1;
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
}
