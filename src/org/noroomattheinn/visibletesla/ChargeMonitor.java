/*
 * ChargeMonitor.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Oct 22, 2014
 */
package org.noroomattheinn.visibletesla;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import org.noroomattheinn.tesla.ChargeState;
import us.monoid.json.JSONException;
import us.monoid.json.JSONObject;

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
        cycleInProgress.startRange = chargeState.range;
        cycleInProgress.startSOC = chargeState.batteryPercent;
        cycleInProgress.lat = ac.lastKnownStreamState.get().estLat;
        cycleInProgress.lng = ac.lastKnownStreamState.get().estLng;
        cycleInProgress.odo = ac.lastKnownStreamState.get().odometer;
        cycleInProgress.newVoltageReading(chargeState.chargerVoltage);
        cycleInProgress.newCurrentReading(chargeState.chargerActualCurrent);
    }
    
    private void updateCycle(ChargeState chargeState) {
        cycleInProgress.newVoltageReading(chargeState.chargerVoltage);
        cycleInProgress.newCurrentReading(chargeState.chargerActualCurrent);
    }
    
    private void completeCycle(ChargeState chargeState) {
        cycleInProgress.endTime = chargeState.timestamp;
        cycleInProgress.endRange = chargeState.range;
        cycleInProgress.endSOC = chargeState.batteryPercent;
        cycleInProgress.energyAdded = chargeState.energyAdded;
        
        lastChargeCycle.set(cycleInProgress);
        cycleInProgress = null;        
    }
    
    public static class Cycle {
        public boolean superCharger;
        public int phases;
        public long startTime, endTime;
        public double startRange, endRange;
        public double startSOC, endSOC;
        public double lat, lng;
        public double odo;
        public double peakVoltage, avgVoltage;
        public double peakCurrent, avgCurrent;
        public double energyAdded;
        private double totalVoltage, totalCurrent;
        private int nCurrentReadings;
        private int nVoltageReadings;

        public Cycle() {
            this.nCurrentReadings = 0;
            this.nVoltageReadings = 0;
            this.peakVoltage = this.avgVoltage = 0.0;
            this.peakCurrent = this.avgCurrent = 0.0;
            this.totalVoltage = this.totalCurrent = 0.0;
        }

        public Cycle(String jsonForm) throws JSONException {
            JSONObject j = new JSONObject(jsonForm);
            Cycle c = new Cycle();
            c.superCharger = j.optBoolean("superCharger");
            c.phases = j.optInt("phases");
            c.startTime = j.optLong("startTime");
            c.endTime = j.optLong("endTime");
            c.startRange = j.optDouble("startRange");
            c.endRange = j.optDouble("endRange");
            c.startSOC = j.optDouble("startSOC");
            c.endSOC = j.optDouble("endSOC");
            c.lat = j.optDouble("lat");
            c.lng = j.optDouble("lng");
            c.odo = j.optDouble("odometer");
            c.peakVoltage = j.optDouble("peakVoltage");
            c.avgVoltage = j.optDouble("avgVoltage");
            c.peakCurrent = j.optDouble("peakCurrent");
            c.avgCurrent = j.optDouble("avgCurrent");
            c.energyAdded = j.optDouble("energyAdded");
        }
        
        void newVoltageReading(double voltage) {
            totalVoltage += voltage;
            nVoltageReadings++;
            if (voltage > peakVoltage) { peakVoltage = voltage; }
            avgVoltage = totalVoltage / nVoltageReadings;
        }

        void newCurrentReading(double current) {
            totalCurrent += current;
            nCurrentReadings++;
            if (current > peakCurrent) { peakCurrent = current; }
            avgCurrent = totalCurrent / nCurrentReadings;
        }

        public String toJSON() {
            return String.format(
                    "{ " +
                        "'supercharger' : %b, 'phases': %d, " +
                        "'startTime' : %d, 'endTime': %d, " +
                        "'startRange' : %.1f, 'endRange': %.1f, " +
                        "'startSOC' : %.1f, 'endSOC': %.1f, " +
                        "'lat' : %f, 'lng': %f, " +
                        "'odometer' : %.1f, " +
                        "'peakVoltage' : %.1f, 'avgVoltage': %.1f, " +
                        "'peakCurrent' : %.1f, 'avgCurrent': %.1f, " +
                        "'energyAdded': %.1f" +
                    " }",
                    superCharger, phases,
                    startTime, endTime,
                    startRange, endRange,
                    startSOC, endSOC,
                    lat, lng,
                    odo,
                    peakVoltage, avgVoltage,
                    peakCurrent, avgCurrent,
                    energyAdded);
        }
        
        @Override public String toString() { return toJSON(); }
    }
    
}
