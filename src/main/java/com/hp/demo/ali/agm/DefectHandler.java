package com.hp.demo.ali.agm;

import com.hp.demo.ali.Settings;
import org.hp.almjclient.exceptions.ALMRestException;
import org.hp.almjclient.exceptions.RestClientException;
import org.hp.almjclient.model.marshallers.Entities;
import org.hp.almjclient.model.marshallers.Entity;
import org.hp.almjclient.model.marshallers.favorite.Filter;
import org.hp.almjclient.services.EntityCRUDService;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by panuska on 4/16/13.
 */
public class DefectHandler extends AbstractBacklogItemHandler {

    private int firstDefId = 0;

    private EntityCRUDService taskCRUDService;

    @Override
    public void init(String sheetName) {
        super.init(sheetName);
        taskCRUDService = SheetHandlerRegistry.getFactory().getEntityCRUDService("project-task");
    }

    @Override
    public List<String> row(Entity entity) {
        try {
            String featureId = entity.getFieldValue("parent-id").getValue();      // set feature/theme the defect belongs to
            entity.removeField("parent-id");

            List<String> returnValue = super.row(entity);
            String agmId = returnValue.get(1);
            _findBacklogItem(agmId);
            _updateBacklogItem(featureId);
            _addBacklogItemId(returnValue);

            Filter filter = new Filter("project-task");                                 // delete the automatically created task
            filter.addQueryClause("release-backlog-item-id", _backlogItemId);
            Entities tasks = taskCRUDService.readCollection(filter);
            List<Integer> taskIds = new ArrayList<Integer>(tasks.getEntityList().size());
            for (Entity entityTask : tasks.getEntityList()) {
                taskIds.add(entityTask.getId());
            }
            log.debug("Deleting all the by default created tasks. In total: "+taskIds.size());
            if (taskIds.size() > 0) {
                taskCRUDService.delete("project-task", taskIds, true);
            }

            if (firstDefId == 0) {
                // remember def IDs
                firstDefId = Integer.parseInt(agmId);
                Settings.getSettings().setFirstDefectNumber(firstDefId);
                log.info("First defect ID: "+firstDefId);
            }
            return returnValue;
        } catch (ALMRestException e) {
            throw new IllegalStateException(e);
        } catch (RestClientException e) {
            throw new IllegalStateException(e);
        }
    }
}
