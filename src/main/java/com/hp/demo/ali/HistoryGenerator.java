package com.hp.demo.ali;

import com.hp.demo.ali.agm.ReleaseHandler;
import com.hp.demo.ali.excel.AgmEntityIterator;
import org.apache.log4j.Logger;
import org.apache.poi.ss.usermodel.Sheet;
import org.hp.almjclient.connection.ServiceResourceAdapter;
import org.hp.almjclient.exceptions.ALMRestException;
import org.hp.almjclient.exceptions.RestClientException;
import org.hp.almjclient.model.marshallers.Entities;
import org.hp.almjclient.model.marshallers.Entity;
import org.hp.almjclient.model.marshallers.favorite.Filter;
import org.hp.almjclient.services.EntityCRUDService;
import org.hp.almjclient.services.impl.ProjectServicesFactory;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by panuska on 3/25/13.
 */
public class HistoryGenerator {
    private static Logger log = Logger.getLogger(HistoryGenerator.class.getName());

    private Sheet sheet;
    private ProjectServicesFactory factory;
    public HistoryGenerator(Sheet sheet, ProjectServicesFactory factory) {
        this.sheet = sheet;
        this.factory = factory;
    }

    public void generate() {
        log.info("Generating history...");
        AgmEntityIterator<Entity> iterator = new AgmEntityIterator<Entity>(sheet);
        long oldDate = -1;
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yy");
        long shift = 0;
        try {
            while (iterator.hasNext()) {
                Entity entity = iterator.next();

                String entityId = entity.getFieldValue("entity-id").getValue();
                String remaining = entity.getFieldValue("remaining").getValue();
                String invested = entity.getFieldValue("invested").getValue();
                String status = entity.getFieldValue("status").getValue();

                Map<String, Object> fields = new HashMap<String, Object>(4);
                fields.put("id", entityId);
                fields.put("remaining", remaining);
                fields.put("invested", invested);
                fields.put("status", status);

                log.debug("Updating task "+entityId+": remaining="+remaining+" invested="+invested+" status="+status);
                Entity projectTask = new Entity("project-task", fields);
                EntityCRUDService service = factory.getEntityCRUDService("project-task");
                projectTask = service.update(projectTask);
                String backlogItemId = projectTask.getFieldValue("release-backlog-item-id").getValue();

                Filter filter = new Filter("project-task");
                filter.addQueryClause("status", "<> COMPLETED");
                filter.addQueryClause("release-backlog-item-id", backlogItemId);
                Entities unfinishedTasks = service.readCollection(filter);
                switch (unfinishedTasks.getEntityList().size()) {
                    case 2: status = "In Progress"; break;
                    case 1: status = "In Testing"; break;
                    case 0: status = "Done"; break;
                }
                log.debug("Setting backlog item "+backlogItemId+" to "+status);
                service = factory.getEntityCRUDService("release-backlog-item");
                fields = new HashMap<String, Object>(2);
                fields.put("id", backlogItemId);
                fields.put("status", status);
                Entity backlogItem = new Entity("release-backlog-item", fields);
                service.update(backlogItem);

                long date = Long.parseLong(entity.getFieldValue("date").getValue());
                if (oldDate == -1) {    // initialize oldDate
                    oldDate = date;
                }
                ServiceResourceAdapter adapter = factory.getServiceResourceAdapter();
                for (; oldDate < date; oldDate++) {
                    Date aggrDate = new Date(ReleaseHandler.getReleaseStartDate().getTime()+(oldDate+shift) * 24*60*60*1000);
                    if (shift == 0 && aggrDate.getDay() == 6) {  // once I found Saturday, shift to over weekend
                        log.debug("Weekend found; we are not usually working on weekends...");  //todo does the algorithm really works fine? (I doubt...)
                        log.debug("Calculating aggregation for date: "+sdf.format(aggrDate));
                        adapter.get(String.class, factory.getProjectRestMetaData().getCollectionBaseUrl()+"internal/services/calculateAggregation/"+sdf.format(aggrDate));

                        shift++;
                        aggrDate = new Date(ReleaseHandler.getReleaseStartDate().getTime()+(oldDate+shift) * 24*60*60*1000);
                        log.debug("Calculating aggregation for date: "+sdf.format(aggrDate));
                        adapter.get(String.class, factory.getProjectRestMetaData().getCollectionBaseUrl()+"internal/services/calculateAggregation/"+sdf.format(aggrDate));

                        shift++;
                        aggrDate = new Date(ReleaseHandler.getReleaseStartDate().getTime()+(oldDate+shift) * 24*60*60*1000);
                    }
                    log.debug("Calculating aggregation for date: "+sdf.format(aggrDate));
                    adapter.get(String.class, factory.getProjectRestMetaData().getCollectionBaseUrl()+"internal/services/calculateAggregation/"+sdf.format(aggrDate));
                }
            }
            ServiceResourceAdapter adapter = factory.getServiceResourceAdapter();
            for (; ReleaseHandler.getReleaseStartDate().getTime()+(oldDate+shift) * 24*60*60*1000 < System.currentTimeMillis(); oldDate++) {
                Date aggrDate = new Date(ReleaseHandler.getReleaseStartDate().getTime()+oldDate * 24*60*60*1000);
                log.debug("Calculating aggregation for date: "+sdf.format(aggrDate));
                adapter.get(String.class, factory.getProjectRestMetaData().getCollectionBaseUrl()+"internal/services/calculateAggregation/"+sdf.format(aggrDate));
            }
        } catch (RestClientException e) {
            throw new IllegalStateException(e);
        } catch (ALMRestException e) {
            throw new IllegalStateException(e);
        }
    }
}
