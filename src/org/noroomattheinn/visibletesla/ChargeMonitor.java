/*
 * ChargeMonitor.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Oct 22, 2014
 */
package org.noroomattheinn.visibletesla;

import com.google.gson.Gson;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import org.noroomattheinn.tesla.ChargeState;
import org.noroomattheinn.tesla.StreamState;
import us.monoid.json.JSONObject;
import org.noroomattheinn.utils.RestyWrapper;
import org.noroomattheinn.utils.Utils;

/**
 * ChargeMonitor - Monitor and store data about Charging Cycles.
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class ChargeMonitor {
    private final AppContext ac;
    private Cycle cycleInProgress = null;

    public ObjectProperty<Cycle> lastChargeCycle = new SimpleObjectProperty<>();

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
        cycleInProgress.startRange = Utils.round(chargeState.range, 1);
        cycleInProgress.startSOC = Utils.round(chargeState.batteryPercent, 1);
        cycleInProgress.odometer = Utils.round(ac.lastKnownStreamState.get().odometer, 1);
        cycleInProgress.newVoltageReading(chargeState.chargerVoltage);
        cycleInProgress.newCurrentReading(cycleInProgress.superCharger ?
                chargeState.batteryCurrent : chargeState.chargerActualCurrent);

        // It's possible that a charge began before we got any location information
        StreamState ss = ac.lastKnownStreamState.get();
        if (ss != null) {
            cycleInProgress.lat = Utils.round(ss.estLat, 6);
            cycleInProgress.lng = Utils.round(ss.estLng, 6);
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
        cycleInProgress.endRange = Utils.round(chargeState.range, 1);
        cycleInProgress.endSOC = Utils.round(chargeState.batteryPercent, 1);
        cycleInProgress.energyAdded = Utils.round(chargeState.energyAdded, 1);

        // If we didn't have location information at startup, see if we've got it now
        if (cycleInProgress.lat == 0 && cycleInProgress.lng == 0) {
            StreamState ss = ac.lastKnownStreamState.get();
            if (ss != null) {
                cycleInProgress.lat = Utils.round(ss.estLat, 6);
                cycleInProgress.lng = Utils.round(ss.estLng, 6);
            }
        }

        cycleInProgress.peakVoltage = Utils.round(cycleInProgress.peakVoltage, 1);
        cycleInProgress.avgVoltage = Utils.round(cycleInProgress.avgVoltage, 1);
        cycleInProgress.peakCurrent = Utils.round(cycleInProgress.peakCurrent, 1);
        cycleInProgress.avgCurrent = Utils.round(cycleInProgress.avgCurrent, 1);

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
            Cycle c = gson.fromJson(json, Cycle.class);
            return c;
        }
        
        public JSONObject toJSON() {
            String json = gson.toJson(this);
            return RestyWrapper.newJSONObject(json);
        }
        
        public String toJSONString() { return gson.toJson(this); }
        
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
