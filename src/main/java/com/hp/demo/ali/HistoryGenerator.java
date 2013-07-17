package com.hp.demo.ali;

import com.hp.demo.ali.agm.ProjectTaskHandler;
import com.hp.demo.ali.agm.ReleaseHandler;
import org.apache.log4j.Logger;
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
import java.util.List;
import java.util.Map;

/**
 * Created by panuska on 3/25/13.
 */
public class HistoryGenerator {
    private static Logger log = Logger.getLogger(HistoryGenerator.class.getName());

    private ProjectServicesFactory factory;
    public HistoryGenerator(ProjectServicesFactory factory) {
        this.factory = factory;
    }

    public void generate() {
        log.info("Generating history...");
        ServiceResourceAdapter adapter = factory.getServiceResourceAdapter();
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yy");
        try {
            for (int day = 0; ReleaseHandler.getReleaseStartDate().getTime()+((long)day) * 24*60*60*1000 < System.currentTimeMillis(); day++) {
                List<Object> work = ProjectTaskHandler.getWorkOnDay(day);
                if (work != null) {
                    while (work.size() > 0) {
                        String status = (String)work.remove(0);
                        String agmId = (String)work.remove(0);
                        int remaining = "In Progress".equals(status) ? (Integer)work.remove(0) : 0; // once completed, remaining is 0
//                        int invested = 6 - remaining;                // todo estimated work must be always 6

                        Map<String, Object> fields = new HashMap<String, Object>(4);
                        fields.put("id", agmId);
                        fields.put("remaining", remaining);
//                        fields.put("invested", invested);
                        fields.put("status", status);

//                        log.debug("Updating task "+agmId+": remaining="+remaining+" invested="+invested+" status="+status);
                        log.debug("Updating task "+agmId+": remaining="+remaining+" status="+status);
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
                    }
                }
                Date aggrDate = new Date(ReleaseHandler.getReleaseStartDate().getTime()+((long)day) * 24*60*60*1000);
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
