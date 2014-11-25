/*
 * ChargeMonitor.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Oct 22, 2014
 */
package org.noroomattheinn.visibletesla;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import org.noroomattheinn.tesla.ChargeState;
import org.noroomattheinn.tesla.StreamState;

/**
 * ChargeMonitor - Monitor and store data about Charging Cycles.
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class ChargeMonitor {
    private final AppContext ac;
    private ChargeCycle cycleInProgress = null;

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
        cycleInProgress = new ChargeCycle();
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

        ac.lastChargeCycle.set(cycleInProgress);
        cycleInProgress = null;        
    }
    
}
