package com.hp.demo.ali.agm;

import org.apache.log4j.Logger;
import org.hp.almjclient.exceptions.ALMRestException;
import org.hp.almjclient.exceptions.RestClientException;
import org.hp.almjclient.model.marshallers.Entity;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by panuska on 4/16/13.
 */
public class SprintListInitializer extends EntityHandler {
    private static Logger log = Logger.getLogger(SprintListInitializer.class.getName());

    @Override
    public void terminate() {
        super.terminate();
        try {
            log.info("Initializing historical sprints...");
            // moving sprints from past to current and back will cause that All existing teams will be assigned to such sprints
            // such past sprints can be then assigned user stories and defect
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            for (Entity sprint : ReleaseHandler.getSprintList().getEntityList()) {
                String id = sprint.getId().toString();
                log.debug("Initializing sprint id: " + id);

                String tense = sprint.getFieldValue("tense").getValue();
                assert tense != null;
                if ("PAST".equals(tense)) {
                    log.debug("Moving the sprint " + id + " to current and back again (to reset assigned teams)");
                    long now = System.currentTimeMillis();
                    long startDate;
                    long endDate;
                    try {
                        startDate = sdf.parse(sprint.getFieldValue("start-date").getValue()).getTime();
                        endDate = sdf.parse(sprint.getFieldValue("end-date").getValue()).getTime();
                    } catch (ParseException e) {
                        throw new IllegalStateException(e);
                    }
                    long diff = now - startDate;

                    assert diff > 0;
                    Map<String, Object> fields = new HashMap<>(3);
                    fields.put("id", sprint.getId());
                    fields.put("start-date", sdf.format(new Date(startDate + diff)));
                    fields.put("end-date", sdf.format(new Date(endDate + diff)));
                    Entity updatedSprint = new Entity("release-cycle", fields);
                    CRUDService.update(updatedSprint);

                    updatedSprint.setFieldValue("start-date", sdf.format(new Date(startDate)));
                    updatedSprint.setFieldValue("end-date", sdf.format(new Date(endDate)));
                    CRUDService.update(updatedSprint);
                }
            }
        } catch (RestClientException | ALMRestException e) {
            throw new IllegalStateException(e);
        }
    }
}
