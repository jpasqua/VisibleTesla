/*
 * StatsStreamer.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Nov 26, 2013
 */

package org.noroomattheinn.visibletesla.data;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import org.noroomattheinn.tesla.BaseState;
import static org.noroomattheinn.tesla.Tesla.logger;
import org.noroomattheinn.tesla.Vehicle;
import org.noroomattheinn.utils.Utils;
import org.noroomattheinn.visibletesla.AppContext;
import org.noroomattheinn.visibletesla.Prefs;
import org.noroomattheinn.visibletesla.ThreadManager;
import org.noroomattheinn.visibletesla.VTVehicle;
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
    private final TrackedObject<CarState> carState;
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public StatsStreamer() {
        this.ac = AppContext.get();
        this.carState = new TrackedObject<>(CarState.Idle);
        
        // The following two changeListeners look similar, but the order of
        // the if statements is different and significant. Always check the
        // one that changed, then test the other (older) value.
        VTVehicle.get().chargeState.addListener(new ChangeListener<BaseState>() {
            @Override public void changed(
                    ObservableValue<? extends BaseState> ov, BaseState t, BaseState cs) {
                if (isCharging()) carState.update(CarState.Charging);
                else if (isInMotion()) carState.update(CarState.Moving);
                else carState.update(CarState.Idle);
                // Can't be asleep - we just got data from it
            }
        });

        VTVehicle.get().streamState.addListener(new ChangeListener<BaseState>() {
            @Override public void changed(
                    ObservableValue<? extends BaseState> ov, BaseState t, BaseState cs) {
                if (isInMotion()) carState.update(CarState.Moving);
                else if (isCharging()) carState.update(CarState.Charging);
                else carState.update(CarState.Idle);
                // Can't be asleep - we just got data from it
            }
        });
        
        carState.addTracker(false, new Runnable() {
            @Override public void run() {
                logger.finest("Car State changed to: " + carState.get());
            }
        });
        ThreadManager.get().launch((Runnable)this, "CollectStats");
    }
        
/*------------------------------------------------------------------------------
 *
 * PRIVATE - The main body of the production thread
 * 
 *----------------------------------------------------------------------------*/
    
    @Override public void run() {
        WakeEarlyPredicate wakeEarly = new WakeEarlyPredicate();

        try {
            while (!ThreadManager.get().shuttingDown()) {
                String theState = String.format(
                        "App State: %s, App Mode: %s, Car State: %s",
                        ac.appState, ac.appMode, carState.get());
                logger.finer(theState);
                boolean produce = true;
                if (ac.appState.isIdle() && ac.appMode.allowingSleeping()) {
                    if (carState.get() == CarState.Idle) {
                        if (timeSince(carState.lastSet()) < AllowSleepInterval) {
                            produce = false;
                            if (carIsAsleep()) { carState.set(CarState.Asleep); }
                        } else { carState.set(CarState.Idle); } // Reset idle start time
                    } else if (carState.get() == CarState.Asleep) {
                        if (carIsAwake()) carState.set(CarState.Idle);
                        else produce = false;
                    }
                }
                if (produce) produce();
                Utils.sleep(DefaultInterval, wakeEarly);
            }
        } catch (Exception e) {
            logger.severe("Uncaught exception in StatsStreamer: " + e.getMessage());
        }
    }
    
    private void produce() {
        if (carState.get() == CarState.Asleep) {
            if (!wakeupVehicle()) {
                // If we're unable to wake up the car, don't bother trying to
                // produce more data. TO DO: Should we do something so that this
                // is retried sooner rather than later?
                logger.warning("Unable to wakeup the car after many attempts");
                return;
            }
        }
        VTData.get().streamProducer.produce(Prefs.get().streamWhenPossible.get());
        VTData.get().stateProducer.produce(Vehicle.StateType.Charge, null);
    }
    
/*------------------------------------------------------------------------------
 *
 * PRIVATE - Methods & classes that determine the state of the vehicle and app
 * 
 *----------------------------------------------------------------------------*/
    
    private boolean isCharging() { return VTVehicle.get().chargeState.get().isCharging(); }
    private boolean isInMotion() { return  VTVehicle.get().streamState.get().isInMotion(); }
    
    private boolean carIsAsleep() { return !carIsAwake(); }
    private boolean carIsAwake() { return  VTVehicle.get().getVehicle().isAwake(); }

    private boolean carIsInactive() { return !carIsActive(); }
    private boolean carIsActive() {
        return (carState.get() == CarState.Moving || carState.get() == CarState.Charging);
    }
    
    private class WakeEarlyPredicate implements Utils.Predicate {
        private long lastEval  = System.currentTimeMillis();

        @Override public boolean eval() {
            try {
                if (ac.appMode.lastSet() > lastEval && ac.appMode.stayingAwake()) return true;
                return ThreadManager.get().shuttingDown();
            } finally {
                lastEval = System.currentTimeMillis();
            }
        }
    }

    private boolean wakeupVehicle() {
        if (VTVehicle.get().forceWakeup()) { carState.set(CarState.Idle); return true; }
        return false;
    }
    
}
