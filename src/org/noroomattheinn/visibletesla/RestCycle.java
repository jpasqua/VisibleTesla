/*
 * RestCycle.java - Copyright(c) 2013, 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Nov 25, 2014
 */
package org.noroomattheinn.visibletesla;

import com.google.gson.Gson;
import java.util.Calendar;
import java.util.List;
import org.noroomattheinn.utils.RestyWrapper;
import us.monoid.json.JSONObject;

/**
 * Object that represents a single rest cycle. A rest cycle is a period where
 * the car is sitting parked and not charging for greater than a minimum period.
 * Rest cycles are interesting for determining the rate of vampire loss.
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class RestCycle {
    private static Gson gson = new Gson();

    public long startTime, endTime;
    public double startRange, endRange;
    public double startSOC, endSOC;
    public double lat, lng;

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
    
    public static RestCycle fromJSON(String json) {
        return gson.fromJson(json, RestCycle.class);
    }

    public JSONObject toJSON() {
        return RestyWrapper.newJSONObject(toJSONString());
    }

    public String toJSONString() {
        return String.format(
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

    @Override public String toString() { return toJSONString(); }
    
    public double hours(long millis) {return ((double)(millis))/(60 * 60 * 1000); }
    public double loss() { return startRange - endRange; }
    public double avgLoss() { return loss() / hours(endTime - startTime); }

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
