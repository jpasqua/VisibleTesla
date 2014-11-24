/*
 * ChargeMonitor.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Oct 22, 2014
 */
package org.noroomattheinn.visibletesla;

import com.google.gson.Gson;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import org.noroomattheinn.tesla.ChargeState;
import org.noroomattheinn.tesla.StreamState;
import us.monoid.json.JSONObject;
import org.noroomattheinn.utils.RestyWrapper;
import org.noroomattheinn.visibletesla.fxextensions.TrackedObject;

/**
 * ChargeMonitor - Monitor and store data about Charging Cycles.
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class ChargeMonitor {
    private final AppContext ac;
    private Cycle cycleInProgress = null;

    public TrackedObject<Cycle> lastChargeCycle = new TrackedObject<>(null);

    ChargeMonitor(AppContext appContext) {
        this.ac = appContext;
        this.cycleInProgress = null;
        ac.lastKnownChargeState.addListener(new ChangeListener<ChargeState>() {
            @Override public void changed(ObservableValue<? extends ChargeState> ov,
                    ChargeState old, ChargeState chargeState) {
                boolean charging = charging(chargeState);
                if (cycleInProgress == null ) {
                    if (charging) { startCycle(chargeState); }
                } else {    // We're in the middle of a cycle
                    if (charging) { updateCycle(chargeState); }
                    else { completeCycle(chargeState); }
                }
            }
        });
    }

    private boolean charging(ChargeState chargeState) {
        if (chargeState.chargingState == ChargeState.Status.Charging) return true;
        if (chargeState.chargerVoltage > 50) return true;
        // Other indicators that charging is in progress?
        return false;
    }

    private void startCycle(ChargeState chargeState) {
        cycleInProgress = new Cycle();
        cycleInProgress.superCharger = chargeState.fastChargerPresent;
        cycleInProgress.phases = chargeState.chargerPhases;
        cycleInProgress.startTime = chargeState.timestamp;
        cycleInProgress.startRange = chargeState.range;
        cycleInProgress.startSOC = chargeState.batteryPercent;
        cycleInProgress.odometer = ac.lastKnownStreamState.get().odometer;
        cycleInProgress.newVoltageReading(chargeState.chargerVoltage);
        cycleInProgress.newCurrentReading(cycleInProgress.superCharger ?
                chargeState.batteryCurrent : chargeState.chargerActualCurrent);

        // It's possible that a charge began before we got any location information
        StreamState ss = ac.lastKnownStreamState.get();
        if (ss != null) {
            cycleInProgress.lat = ss.estLat;
            cycleInProgress.lng = ss.estLng;
        } else {
            cycleInProgress.lat = cycleInProgress.lng = 0.0;
        }
    }

    private void updateCycle(ChargeState chargeState) {
        cycleInProgress.newVoltageReading(chargeState.chargerVoltage);
        cycleInProgress.newCurrentReading(cycleInProgress.superCharger ?
                chargeState.batteryCurrent : chargeState.chargerActualCurrent);
    }

    private void completeCycle(ChargeState chargeState) {
        cycleInProgress.endTime = chargeState.timestamp;
        cycleInProgress.endRange = chargeState.range;
        cycleInProgress.endSOC = chargeState.batteryPercent;
        cycleInProgress.energyAdded = chargeState.energyAdded;

        // If we didn't have location information at startup, see if we've got it now
        if (cycleInProgress.lat == 0 && cycleInProgress.lng == 0) {
            StreamState ss = ac.lastKnownStreamState.get();
            if (ss != null) {
                cycleInProgress.lat = ss.estLat;
                cycleInProgress.lng = ss.estLng;
            }
        }

        lastChargeCycle.set(cycleInProgress);
        cycleInProgress = null;        
    }
    
    public static class Cycle {
        private static Gson gson = new Gson();
        
        public boolean superCharger;
        public int phases;
        public long startTime, endTime;
        public double startRange, endRange;
        public double startSOC, endSOC;
        public double lat, lng;
            // We use 0,0 to mean that the lat,lng has not been set
            // 0,0 is a vlid location, but it's in the middle of the
            // of the ocean, so not a useful location for this purpose
        public double odometer;
        public double peakVoltage, avgVoltage;
        public double peakCurrent, avgCurrent;
        public double energyAdded;
        
        private transient double totalVoltage, totalCurrent;
        private transient int nCurrentReadings;
        private transient int nVoltageReadings;

        public Cycle() {
            this.nCurrentReadings = 0;
            this.nVoltageReadings = 0;
            this.peakVoltage = this.avgVoltage = 0.0;
            this.peakCurrent = this.avgCurrent = 0.0;
            this.totalVoltage = this.totalCurrent = 0.0;
        }

        public static Cycle fromJSON(String json) {
            return gson.fromJson(json, Cycle.class);
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

        private void newVoltageReading(double voltage) {
            totalVoltage += voltage;
            nVoltageReadings++;
            if (voltage > peakVoltage) { peakVoltage = voltage; }
            avgVoltage = totalVoltage / nVoltageReadings;
        }

        private void newCurrentReading(double current) {
            totalCurrent += current;
            nCurrentReadings++;
            if (current > peakCurrent) { peakCurrent = current; }
            avgCurrent = totalCurrent / nCurrentReadings;
        }
    
    }
    
}
