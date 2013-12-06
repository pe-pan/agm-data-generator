package com.hp.demo.ali.agm;

import com.hp.demo.ali.tools.EntityTools;
import org.apache.log4j.Logger;
import org.hp.almjclient.exceptions.FieldNotFoundException;
import org.hp.almjclient.model.marshallers.Entity;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Created by panuska on 4/20/13.
 */
public class ProjectTaskHandler extends EntityHandler {
    private static Logger log = Logger.getLogger(ProjectTaskHandler.class.getName());

    private static Map<Integer, List<Object>> workCalendar = new HashMap<>(1000);

    public static List<Object> getWorkOnDay(int day) {
        return workCalendar.get(day);
    }

    @Override
    public Entity row(Entity entity) {
        // when calculating history
        // check completed day is > in progress day
        // check in progress remaining hours < estimated
        int dayWhenInProgress = EntityTools.removeIntField(entity, "day-when-in-progress");
        int newRemaining = EntityTools.removeIntField(entity, "new-remaining");
        int dayWhenCompleted = EntityTools.removeIntField(entity, "day-when-completed");
        String originalTeamId = EntityTools.removeField(entity, "team-id");
        Entity response = super.row(entity);
        String agmId;
        try {
            agmId = response.getId().toString();
        } catch (FieldNotFoundException e) {
            throw new IllegalStateException(e);
        }

        if (dayWhenCompleted >= 0 && dayWhenInProgress >= 0) {
            if (dayWhenCompleted <= dayWhenInProgress) {
                log.warn("Day to be in progress ("+dayWhenInProgress+") is after the day to be completed ("+dayWhenCompleted+")");
            }
        }
        if (dayWhenInProgress > 0) {
            List<Object> work = workCalendar.get(dayWhenInProgress);
            if (work == null) {
                work = new LinkedList<>();
                workCalendar.put(dayWhenInProgress, work);
            }
            work.add("In Progress");
            work.add(agmId);
            work.add(newRemaining);
            work.add(originalTeamId);
            log.debug("On day "+dayWhenInProgress+" task "+agmId+" belonging to "+originalTeamId+" switching to In Progress; remaining set to "+newRemaining);
        }
        if (dayWhenCompleted > 0) {
            List<Object> work = workCalendar.get(dayWhenCompleted);
            if (work == null) {
                work = new LinkedList<>();
                workCalendar.put(dayWhenCompleted, work);
            }
            work.add("Completed");
            work.add(agmId);
            work.add(originalTeamId);
            log.debug("On day "+dayWhenCompleted+" task "+agmId+" belonging to "+originalTeamId+" switching to Completed; remaining set to "+0);
        }
        return response;
    }
}
