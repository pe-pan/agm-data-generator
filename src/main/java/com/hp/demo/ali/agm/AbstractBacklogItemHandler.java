package com.hp.demo.ali.agm;

import com.hp.demo.ali.excel.AgmEntityIterator;
import org.hp.almjclient.exceptions.ALMRestException;
import org.hp.almjclient.exceptions.RestClientException;
import org.hp.almjclient.model.marshallers.Entities;
import org.hp.almjclient.model.marshallers.Entity;
import org.hp.almjclient.model.marshallers.favorite.Filter;

/**
 * Created by panuska on 4/16/13.
 */
public abstract class AbstractBacklogItemHandler extends EntityHandler {

    /**
     * Finds RBLI entity related to the defect/requirement entity and writes its ID to translation table.
     */
    protected void findBacklogItem(String excelId, String agmId) throws ALMRestException, RestClientException {
        Filter filter = new Filter("release-backlog-item");
        filter.addQueryClause("entity-id", agmId);
        filter.addQueryClause("entity-type", sheetName);        // a user story may have the same ID as a defect
        Entities entities = CRUDService.readCollection(filter);
        assert entities.getTotalResults() == 1;
        Entity backlogItem = entities.getEntityList().get(0);
        String backlogItemId = backlogItem.getId().toString();
        AgmEntityIterator.putReference("apmuiservice#" + sheetName + "#" + excelId, backlogItemId);
    }
}
