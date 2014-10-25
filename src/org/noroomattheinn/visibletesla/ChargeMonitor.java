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
import us.monoid.json.JSONObject;
import org.noroomattheinn.utils.RestyWrapper;

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
        cycleInProgress.startRange = sig(chargeState.range, 1);
        cycleInProgress.startSOC = sig(chargeState.batteryPercent, 1);
        cycleInProgress.lat = sig(ac.lastKnownStreamState.get().estLat,6);
        cycleInProgress.lng = sig(ac.lastKnownStreamState.get().estLng,6);
        cycleInProgress.odometer = sig(ac.lastKnownStreamState.get().odometer, 1);
        cycleInProgress.newVoltageReading(chargeState.chargerVoltage);
        cycleInProgress.newCurrentReading(chargeState.chargerActualCurrent);
    }
    
    private void updateCycle(ChargeState chargeState) {
        cycleInProgress.newVoltageReading(chargeState.chargerVoltage);
        cycleInProgress.newCurrentReading(chargeState.chargerActualCurrent);
    }
    
    private void completeCycle(ChargeState chargeState) {
        cycleInProgress.endTime = chargeState.timestamp;
        cycleInProgress.endRange = sig(chargeState.range, 1);
        cycleInProgress.endSOC = sig(chargeState.batteryPercent, 1);
        cycleInProgress.energyAdded = sig(chargeState.energyAdded, 1);
        
        cycleInProgress.peakVoltage = sig(cycleInProgress.peakVoltage, 1);
        cycleInProgress.avgVoltage = sig(cycleInProgress.avgVoltage, 1);
        cycleInProgress.peakCurrent = sig(cycleInProgress.peakCurrent, 1);
        cycleInProgress.avgCurrent = sig(cycleInProgress.avgCurrent, 1);
        
        lastChargeCycle.set(cycleInProgress);
        cycleInProgress = null;        
    }
    
    private double sig(double val, int n) {
        double pow = Math.pow(10, n);
        val = Math.floor(val * pow)/pow;
        return val;
    }
    
    
    public static class Cycle {
        private static Gson gson = new Gson();
        
        public boolean superCharger;
        public int phases;
        public long startTime, endTime;
        public double startRange, endRange;
        public double startSOC, endSOC;
        public double lat, lng;
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
