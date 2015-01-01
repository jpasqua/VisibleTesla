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
import org.noroomattheinn.utils.Utils.Predicate;
import org.noroomattheinn.visibletesla.Prefs;
import org.noroomattheinn.visibletesla.ThreadManager;
import org.noroomattheinn.visibletesla.VTVehicle;
import static org.noroomattheinn.utils.Utils.timeSince;
import org.noroomattheinn.fxextensions.TrackedObject;

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
    
    private final TrackedObject<CarState> carState;
    private Predicate wakeEarly = Utils.alwaysFalse;
    private Predicate passiveCollection = Utils.alwaysFalse;
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public StatsStreamer() {
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
    
    public void setWakeEarly(Predicate wakeEarly) {
        this.wakeEarly = wakeEarly;
    }
    
    public void setPassiveCollection(Predicate pc) {
        this.passiveCollection = pc;
    }
    
/*------------------------------------------------------------------------------
 *
 * PRIVATE - The main body of the production thread
 * 
 *----------------------------------------------------------------------------*/
    
    @Override public void run() {
        try {
            while (!ThreadManager.get().shuttingDown()) {
//                String theState = String.format(
//                        "App State: %s, App Mode: %s, Car State: %s",
//                        App.get().state, App.get().mode.get().name(), carState.get());
//                logger.finer(theState);
                boolean produce = true;
                if (passiveCollection.eval()) {
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
        VTData.get().produceStream(Prefs.get().streamWhenPossible.get());
        VTData.get().produceState(Vehicle.StateType.Charge, null);
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
    
    private boolean wakeupVehicle() {
        if (VTVehicle.get().forceWakeup()) { carState.set(CarState.Idle); return true; }
        return false;
    }
    
}
