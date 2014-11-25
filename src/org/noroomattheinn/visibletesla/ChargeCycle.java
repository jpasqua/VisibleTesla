/*
 * ChargeCycle.java - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Nov 25, 2014
 */
package org.noroomattheinn.visibletesla;

import com.google.gson.Gson;
import org.noroomattheinn.utils.RestyWrapper;
import us.monoid.json.JSONObject;

/**
 * Object that represents a single charge cycle.
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class ChargeCycle {
    private static Gson gson = new Gson();

    public boolean superCharger;
    public int phases;
    public long startTime, endTime;
    public double startRange, endRange;
    public double startSOC, endSOC;
    public double lat, lng;
        // We use 0,0 to mean that the lat,lng has not been set
        // 0,0 is a valid location, but it's in the middle of the
        // of the ocean, so not a useful location for this purpose
    public double odometer;
    public double peakVoltage, avgVoltage;
    public double peakCurrent, avgCurrent;
    public double energyAdded;

    private transient double totalVoltage, totalCurrent;
    private transient int nCurrentReadings;
    private transient int nVoltageReadings;

    public ChargeCycle() {
        this.nCurrentReadings = 0;
        this.nVoltageReadings = 0;
        this.peakVoltage = this.avgVoltage = 0.0;
        this.peakCurrent = this.avgCurrent = 0.0;
        this.totalVoltage = this.totalCurrent = 0.0;
    }

    public static ChargeCycle fromJSON(String json) {
        return gson.fromJson(json, ChargeCycle.class);
    }

    public JSONObject toJSON() {
        return RestyWrapper.newJSONObject(toJSONString());
    }

    public String toJSONString() {
        return String.format(
                "{ " +
                "  \"superCharger\": %b, " +
                "  \"phases\": %d, " +
                "  \"startTime\": %d, " +
                "  \"endTime\": %d, " +
                "  \"startRange\": %.1f, " +
                "  \"endRange\": %.1f, " +
                "  \"startSOC\": %.1f, " +
                "  \"endSOC\": %.1f, " +
                "  \"lat\": %.6f, " +
                "  \"lng\": %.6f, " +
                "  \"odometer\": %.1f, " +
                "  \"peakVoltage\": %.1f, " +
                "  \"avgVoltage\": %.1f, " +
                "  \"peakCurrent\": %.1f, " +
                "  \"avgCurrent\": %.1f, " +
                "  \"energyAdded\": %.1f " +
                " }",
                superCharger, phases, startTime, endTime,
                startRange, endRange, startSOC, endSOC, lat, lng, odometer,
                peakVoltage, avgVoltage, peakCurrent, avgCurrent, energyAdded
                );
    }

    @Override public String toString() { return toJSONString(); }

    public void newVoltageReading(double voltage) {
        totalVoltage += voltage;
        nVoltageReadings++;
        if (voltage > peakVoltage) { peakVoltage = voltage; }
        avgVoltage = totalVoltage / nVoltageReadings;
    }

    public void newCurrentReading(double current) {
        totalCurrent += current;
        nCurrentReadings++;
        if (current > peakCurrent) { peakCurrent = current; }
        avgCurrent = totalCurrent / nCurrentReadings;
    }

}
