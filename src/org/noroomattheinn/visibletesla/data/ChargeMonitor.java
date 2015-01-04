/*
 * ChargeMonitor.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Oct 22, 2014
 */
package org.noroomattheinn.visibletesla.data;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import org.noroomattheinn.tesla.ChargeState;
import org.noroomattheinn.tesla.StreamState;
import org.noroomattheinn.utils.TrackedObject;
import org.noroomattheinn.visibletesla.vehicle.VTVehicle;

/**
 * ChargeMonitor - Monitor and store data about Charging Cycles.
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
class ChargeMonitor {
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private final VTVehicle vtVehicle;
    private final TrackedObject<ChargeCycle> lastChargeCycle;
    private ChargeCycle cycleInProgress = null;
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public ChargeMonitor(VTVehicle vehicle, TrackedObject<ChargeCycle> lastCycle) {
        this.vtVehicle = vehicle;
        this.lastChargeCycle = lastCycle;
        this.cycleInProgress = null;
        vtVehicle.chargeState.addListener(new ChangeListener<ChargeState>() {
            @Override public void changed(ObservableValue<? extends ChargeState> ov,
                    ChargeState old, ChargeState chargeState) {
                boolean charging = chargeState.isCharging();
                if (cycleInProgress == null ) {
                    if (charging) { startCycle(chargeState); }
                } else {    // We're in the middle of a cycle
                    if (charging) { updateRunningTotals(chargeState); }
                    else { completeCycle(chargeState); }
                }
            }
        });
    }
    
/*------------------------------------------------------------------------------
 *
 * Private methods to keep track of a charge cycle
 * 
 *----------------------------------------------------------------------------*/
    
    private void startCycle(ChargeState chargeState) {
        cycleInProgress = new ChargeCycle();
        cycleInProgress.superCharger = chargeState.fastChargerPresent;
        cycleInProgress.phases = chargeState.chargerPhases;
        cycleInProgress.startTime = chargeState.timestamp;
        cycleInProgress.startRange = chargeState.range;
        cycleInProgress.startSOC = chargeState.batteryPercent;
        cycleInProgress.odometer = vtVehicle.streamState.get().odometer;
        updateRunningTotals(chargeState);

        // It's possible that a charge began before we got any location information
        StreamState ss = vtVehicle.streamState.get();
        if (ss != null) {
            cycleInProgress.lat = ss.estLat;
            cycleInProgress.lng = ss.estLng;
        } else {
            cycleInProgress.lat = cycleInProgress.lng = 0.0;
        }
    }

    private void updateRunningTotals(ChargeState chargeState) {
        cycleInProgress.newIE(
                chargeState.chargerVoltage,
                cycleInProgress.superCharger ?
                    chargeState.batteryCurrent : chargeState.chargerActualCurrent);
    }

    private void completeCycle(ChargeState chargeState) {
        cycleInProgress.endTime = chargeState.timestamp;
        cycleInProgress.endRange = chargeState.range;
        cycleInProgress.endSOC = chargeState.batteryPercent;
        cycleInProgress.energyAdded = chargeState.energyAdded;

        // If we didn't have location information at startup, see if we've got it now
        if (cycleInProgress.lat == 0 && cycleInProgress.lng == 0) {
            StreamState ss = vtVehicle.streamState.get();
            if (ss != null) {
                cycleInProgress.lat = ss.estLat;
                cycleInProgress.lng = ss.estLng;
            }
        }

        lastChargeCycle.set(cycleInProgress);
        cycleInProgress = null;        
    }
    
}
