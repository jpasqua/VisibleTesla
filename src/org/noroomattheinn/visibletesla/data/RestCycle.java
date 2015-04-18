/*
 * RestCycle.java - Copyright(c) 2013, 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Nov 25, 2014
 */
package org.noroomattheinn.visibletesla.data;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Object that represents a single rest cycle. A rest cycle is a period where
 * the car is sitting parked and not charging for greater than a minimum period.
 * Rest cycles are interesting for determining the rate of vampire loss.
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class RestCycle extends BaseCycle {
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/

    public double startRange, endRange;
    public double startSOC, endSOC;

    public RestCycle() { }

    public RestCycle(RestCycle basis) {
        this.startTime = basis.startTime;
        this.endTime = basis.endTime;
        this.startRange = basis.startRange;
        this.endRange = basis.endRange;
        this.startSOC = basis.startSOC;
        this.endSOC = basis.endSOC;
        this.lat = basis.lat;
        this.lng = basis.lng;
    }

    @Override public String toJSONString() {
        return String.format(Locale.US,     // Get the correct decimal point char
                "{ " +
                "  \"startTime\": %d, " +
                "  \"endTime\": %d, " +
                "  \"startRange\": %.1f, " +
                "  \"endRange\": %.1f, " +
                "  \"startSOC\": %.1f, " +
                "  \"endSOC\": %.1f, " +
                "  \"lat\": %.6f, " +
                "  \"lng\": %.6f " +
                " }",
                startTime, endTime, startRange, endRange, startSOC, endSOC, lat, lng);
    }

    /**
     * Return the loss in range over this RestCycle
     * @return The loss in range. Always given in miles.
     */
    public double loss() { return startRange - endRange; }
    
    /**
     * Return the average loss in range per hour over this RestCycle.
     * @return The average loss/hour. Always given in miles/hour.
     */
    public double avgLoss() { return loss() / hours(endTime - startTime); }

    /**
     * This RestCycle may span multiple days, split it into a List of RestCycles
     * that each covers at most one day.
     * @param splitList A list of RestCycles with at least one element.
     */
    public void splitIntoDays(List<RestCycle> splitList) {
        int startDay = dayOf(startTime);
        int endDay = dayOf(endTime);
        if (startDay != endDay) {
            long endOfDay = endOfDay(startTime);
            double ratio = ((double)(endOfDay - startTime))/
                           ((double)(endTime - startTime));
            double newEndRange = startRange - ((startRange - endRange) * ratio);
            double newEndSOC = startSOC - ((startSOC - endSOC) * ratio);
            RestCycle first = new RestCycle(this);
            first.endTime = endOfDay;
            first.endRange = newEndRange;
            first.endSOC = newEndSOC;
            splitList.add(first);
            RestCycle remainder = new RestCycle(this);
            remainder.startTime = endOfDay + 2000;  // Inch into the next day
            remainder.startRange = newEndRange;
            remainder.endSOC = newEndSOC;
            remainder.splitIntoDays(splitList);
        } else {
            splitList.add(this);
        }
    }
    
/*------------------------------------------------------------------------------
 *
 * PRIVATE Utility Methods
 * 
 *----------------------------------------------------------------------------*/
    
    private double hours(long millis) {return ((double)(millis))/(60 * 60 * 1000); }

    private int dayOf(long time) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(time);
        return c.get(Calendar.DAY_OF_YEAR);
    }
    
    private long endOfDay(long time) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(time);
        c.set(Calendar.HOUR_OF_DAY, 23);
        c.set(Calendar.MINUTE, 59);
        c.set(Calendar.SECOND, 59);
        return c.getTimeInMillis();
    }
}
