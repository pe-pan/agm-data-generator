package com.hp.demo.ali.agm;

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

    private static Map<Integer, List<Object>> workCalendar = new HashMap<Integer, List<Object>>(1000);

    public static List<Object> getWorkOnDay(int day) {
        return workCalendar.get(day);
    }

    @Override
    public List<String> row(Entity entity) {
        // when calculating history
        // check completed day is > in progress day
        // check in progress remaining hours < estimated
        int dayWhenInProgress = getAndRemoveFieldValue(entity, "day-when-in-progress");
        int newRemaining = getAndRemoveFieldValue(entity, "new-remaining");
        int dayWhenCompleted = getAndRemoveFieldValue(entity, "day-when-completed");
        List<String> returnValue = super.row(entity);
        String agmId = returnValue.get(1);

        if (dayWhenCompleted >= 0 && dayWhenInProgress >= 0) {
            if (dayWhenCompleted <= dayWhenInProgress) {
                log.warn("Day to be in progress ("+dayWhenInProgress+") is after the day to be completed ("+dayWhenCompleted+")");
            }
        }
        if (dayWhenInProgress > 0) {
            List<Object> work = workCalendar.get(dayWhenInProgress);
            if (work == null) {
                work = new LinkedList<Object>();
                workCalendar.put(dayWhenInProgress, work);
            }
            work.add("In Progress");
            work.add(agmId);
            work.add(newRemaining);
            log.debug("On day "+dayWhenInProgress+" task "+agmId+" switching to In Progress; remaining set to "+newRemaining);
        }
        if (dayWhenCompleted > 0) {
            List<Object> work = workCalendar.get(dayWhenCompleted);
            if (work == null) {
                work = new LinkedList<Object>();
                workCalendar.put(dayWhenCompleted, work);
            }
            work.add("Completed");
            work.add(agmId);
            log.debug("On day "+dayWhenCompleted+" task "+agmId+" switching to Completed; remaining set to "+0);
        }
        return returnValue;
    }

    private int getAndRemoveFieldValue(Entity entity, String fieldName) {
        try {
            int returnValue = Integer.parseInt(entity.getFieldValue(fieldName).getValue());
            entity.removeField(fieldName);
            return returnValue;
        } catch (FieldNotFoundException e) {
            return -1;
        }
    }
}
