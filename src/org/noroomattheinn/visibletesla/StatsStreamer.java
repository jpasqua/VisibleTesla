/*
 * StatsStreamer.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Nov 26, 2013
 */

package org.noroomattheinn.visibletesla;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import org.noroomattheinn.tesla.BaseState;
import org.noroomattheinn.tesla.ChargeState;
import org.noroomattheinn.tesla.StreamState;
import org.noroomattheinn.tesla.Tesla;
import org.noroomattheinn.tesla.Vehicle;
import org.noroomattheinn.utils.Utils;
import static org.noroomattheinn.utils.Utils.timeSince;
import org.noroomattheinn.visibletesla.fxextensions.TrackedObject;

/**
 * StatsStreamer: Generate a stream of statistics
 * 
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class StatsStreamer implements Runnable {

/*------------------------------------------------------------------------------
 *
 * Constants and Enums
 * 
 *----------------------------------------------------------------------------*/
    
    private static final long AllowSleepInterval = 30 * 60 * 1000;  // 30 Minutes
    private static final long DefaultInterval =     4 * 60 * 1000;  //  4 Minutes
    private enum CarState {Moving, Charging, Idle, Asleep};
    
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private final AppContext    ac;
    private TrackedObject<CarState> carState = new TrackedObject<>(CarState.Idle);
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public StatsStreamer(AppContext appContext) {
        this.ac = appContext;
        
        // The following two changeListeners look similar, but the order of
        // the if statements is different and significant. Always check the
        // one that changed, then test the other (older) value.
        ac.lastKnownChargeState.addListener(new ChangeListener<BaseState>() {
            @Override public void changed(
                    ObservableValue<? extends BaseState> ov, BaseState t, BaseState cs) {
                if (isCharging()) carState.set(CarState.Charging);
                else if (isInMotion()) carState.set(CarState.Moving);
                else carState.set(CarState.Idle);
                // Can't be asleep - we just got data from it
            }
        });

        ac.lastKnownStreamState.addListener(new ChangeListener<BaseState>() {
            @Override public void changed(
                    ObservableValue<? extends BaseState> ov, BaseState t, BaseState cs) {
                if (isInMotion()) carState.set(CarState.Moving);
                else if (isCharging()) carState.set(CarState.Charging);
                else carState.set(CarState.Idle);
                // Can't be asleep - we just got data from it
            }
        });
        
        appContext.tm.launch((Runnable)this, "CollectStats");
    }
        
/*------------------------------------------------------------------------------
 *
 * PRIVATE - The main body of the production thread
 * 
 *----------------------------------------------------------------------------*/
    
    @Override public void run() {
        WakeEarlyPredicate wakeEarly = new WakeEarlyPredicate(ac);

        try {
            while (!ac.shuttingDown.get()) {
                boolean produce = true;
                if (ac.inactivity.appIsIdle()) {
                    if (ac.inactivity.allowSleepingMode() &&  carIsInactive()) {  // Sleeping or Idle
                        if (carState.get() == CarState.Idle) {
                            if (timeSince(carState.lastSet()) < AllowSleepInterval) {
                                produce = false;
                                if (carIsAsleep()) { carState.set(CarState.Asleep); }
                            }
                        } else {    // carState == Sleeping
                            if (carIsAwake()) { carState.set(CarState.Idle); }
                            else { produce = false; }
                        }
                    }
                }
                if (produce) produce();
                Utils.sleep(DefaultInterval, wakeEarly);
            }
        } catch (Exception e) {
            Tesla.logger.severe("Uncaught exception in StatsStreamer: " + e.getMessage());
        }
    }
    
    private void produce() {
        if (carState.get() == CarState.Asleep) { wakeupVehicle(); }
        ac.streamProducer.produce(ac.prefs.streamWhenPossible.get());
        ac.stateProducer.produce(Vehicle.StateType.Charge, null);
    }
    
/*------------------------------------------------------------------------------
 *
 * PRIVATE - Methods & classes that determine the state of the vehicle and app
 * 
 *----------------------------------------------------------------------------*/
    
    private boolean isCharging() {
        ChargeState charge = ac.lastKnownChargeState.get();
        if (charge == null) return false;
        if (charge.chargingState == ChargeState.Status.Charging) return true;   // Belt...
        return (charge.chargeRate > 0);                                         // and suspenders
    }

    private boolean isInMotion() {
        StreamState ss = ac.lastKnownStreamState.get();
        if (ss == null) return false;
        if (ss.speed > 0.0) return true;        // Belt...
        return (!ss.shiftState.equals("P"));    // and suspenders
    }

    private boolean carIsAsleep() { return !carIsAwake(); }
    private boolean carIsAwake() {
        boolean awake = ac.vehicle.isAwake();
        Tesla.logger.fine("carIsAwake: "+awake);
        return awake;
    }

    private boolean carIsInactive() { return !carIsActive(); }
    private boolean carIsActive() {
        return (carState.get() == CarState.Moving || carState.get() == CarState.Charging);
    }
    
    private static class WakeEarlyPredicate implements Utils.Predicate {
        private final AppContext ac;
        private long lastEval;
        
        WakeEarlyPredicate(AppContext ac) {
            this.ac = ac;
            this.lastEval = System.currentTimeMillis();
        }
        
        @Override public boolean eval() {
            try {
                if (ac.inactivity.mode.lastSet() > lastEval && ac.inactivity.stayAwakeMode()) return true;
                return ac.shuttingDown.get();
            } finally {
                lastEval = System.currentTimeMillis();
            }
        }
    }

    private void wakeupVehicle() {
        if (ac.utils.forceWakeup()) carState.set(CarState.Idle);
    }

}
