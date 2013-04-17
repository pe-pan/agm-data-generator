package com.hp.demo.ali.agm;


import com.hp.demo.ali.Settings;
import com.hp.demo.ali.excel.AgmEntityIterator;
import org.hp.almjclient.exceptions.ALMRestException;
import org.hp.almjclient.exceptions.RestClientException;
import org.hp.almjclient.model.marshallers.Entities;
import org.hp.almjclient.model.marshallers.Entity;
import org.hp.almjclient.model.marshallers.favorite.Filter;
import org.hp.almjclient.services.EntityCRUDService;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Created by panuska on 3/14/13.
 */
public class ReleaseHandler extends EntityHandler {

    private static Date releaseStartDate;                        //todo as this is static, can handle only one release

    public static Date getReleaseStartDate() {
        return releaseStartDate;
    }

    private static Entities sprintList;                         //todo as this is static, can handle only one release

    public static Entities getSprintList() {
        return sprintList;
    }

    private static SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

    @Override
    public List<String> row(Entity entity) {
        try {
            //calculates the release date using current date (it's relative to the current date)
            releaseStartDate = new Date(Settings.getSettings().getFirstBuildDate().getTime() + (Long.parseLong(entity.getFieldValue("start-date").getValue()) * 24*60*60*1000));
            log.debug("Setting start of the release to: "+sdf.format(releaseStartDate));
            entity.setFieldValue("start-date", sdf.format(releaseStartDate));

            Date endDate = new Date(releaseStartDate.getTime() + (Long.parseLong(entity.getFieldValue("end-date").getValue()) * 24*60*60*1000));
            log.debug("Setting end of the release to: "+sdf.format(endDate));
            entity.setFieldValue("end-date", sdf.format(endDate));

            List<String> returnValue = super.row(entity);

            // learns all the created sprints
            sprintList = getSprints(returnValue.get(1));
            log.info("Learning created sprints ("+sprintList.getEntityList().size()+")...");
            int i = 1;
            for (Entity sprint : sprintList.getEntityList()) {
                String id = sprint.getId().toString();
                log.debug("Learning sprint#"+i+" with id: "+id);
                AgmEntityIterator.putReference("sprint#", i++, id);
            }
            return returnValue;
        } catch (ALMRestException e) {
            throw new IllegalStateException(e);
        } catch (RestClientException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Entities getSprints(String releaseId) throws ALMRestException, RestClientException {
        log.debug("Getting sprints of release " + releaseId + " ...");
        EntityCRUDService CRUDservice = SheetHandlerRegistry.getFactory().getEntityCRUDService("release-cycle");
        Filter filter = new Filter("release-cycle");
        filter.addQueryClause("parent-id", releaseId);
        Entities entities;
        entities = CRUDservice.readCollection(filter);
        return entities;
    }
}
