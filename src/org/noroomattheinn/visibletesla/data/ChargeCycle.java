/*
 * ChargeCycle.java - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Nov 25, 2014
 */
package org.noroomattheinn.visibletesla.data;

import java.util.Locale;
import static org.noroomattheinn.tesla.Tesla.logger;

/**
 * An object that represents a single charge cycle.
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class ChargeCycle extends BaseCycle {
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/

    private transient double totalVoltage, totalCurrent;
    private transient int nReadings;

/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public boolean superCharger;
    public int phases;
    public double startRange, endRange;
    public double startSOC, endSOC;
    public double odometer;
    public double peakVoltage, avgVoltage;
    public double peakCurrent, avgCurrent;
    public double energyAdded;

    public ChargeCycle() {
        this.nReadings = 0;
        this.peakVoltage = this.avgVoltage = 0.0;
        this.peakCurrent = this.avgCurrent = 0.0;
        this.totalVoltage = this.totalCurrent = 0.0;
    }

    @Override public String toJSONString() {
        if (Double.isNaN(lat) || Double.isNaN(lng)) {
            lat = lng = 0.0;
            logger.warning("Lat/Lng is unknown");
        }
        return String.format(Locale.US,     // Get the correct decimal point char
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

    /**
     * Keep a running total and average of current and voltage (I and E). This 
     * method accepts a new reading for each and factors them into the running
     * total and average.
     * @param voltage   The current Voltage reading (E)
     * @param current   The current Current reading (I)
     */
    public void newIE(double voltage, double current) {
        nReadings++;

        totalVoltage += voltage;
        if (voltage > peakVoltage) { peakVoltage = voltage; }
        avgVoltage = totalVoltage / nReadings;
        
        totalCurrent += current;
        if (current > peakCurrent) { peakCurrent = current; }
        avgCurrent = totalCurrent / nReadings;
    }

}
