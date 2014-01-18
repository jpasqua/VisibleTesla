/*
 * StatsStreamer.java -  - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Nov 26;
 */

package org.noroomattheinn.visibletesla;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import org.noroomattheinn.tesla.ChargeState;
import org.noroomattheinn.tesla.SnapshotState;
import org.noroomattheinn.tesla.Tesla;
import org.noroomattheinn.tesla.Vehicle;
import org.noroomattheinn.utils.Utils;

/**
 * StatsStreamer: Generate a stream of stats on demand.
 * 
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class StatsStreamer {

/*------------------------------------------------------------------------------
 *
 * Constants and Enums
 * 
 *----------------------------------------------------------------------------*/

    private static final long TestSleepInterval = 5 * 60 * 1000;  //  5 Minutes
    private static final long DefaultInterval =   2 * 60 * 1000;  //  2 Minutes
    private static final long MinInterval =           30 * 1000;  // 30 Seconds

/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private static boolean stopCollecting = false;
    private final AppContext appContext;
    private final Thread collector;
    private final ChargeState charge;
    private final SnapshotState snapshot;
    private final Vehicle vehicle;
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public StatsStreamer(AppContext appContext, Vehicle v) {
        this.appContext = appContext;
        this.charge = new ChargeState(v);
        this.snapshot = new SnapshotState(v);
        this.vehicle = v;
        this.collector = appContext.launchThread(new AutoCollect(), "00 CollectStats");
    }
    
    
    public void stop() {
        stopCollecting = true;
        try { collector.join(); } catch (InterruptedException e) {}
    }

    
/*------------------------------------------------------------------------------
 *
 * This section has the code pertaining to the background thread that
 * is responsible for collecting stats on a regular basis
 * 
 *----------------------------------------------------------------------------*/
    
    private class AutoCollect implements Runnable {
        private static final long AllowSleepInterval = 30 * 60 * 1000;   // 30 Minutes

        private AppContext.InactivityType inactivityState;
        
        public AutoCollect() {
            inactivityState = appContext.inactivityState.get();
            appContext.inactivityState.addListener(new ChangeListener<AppContext.InactivityType>() {
                @Override public void changed(
                        ObservableValue<? extends AppContext.InactivityType> ov,
                        AppContext.InactivityType old, AppContext.InactivityType cur) {
                    inactivityState = cur;
                }
            });
        }
        
        private void publishStats() {
            long time = System.currentTimeMillis(); // Syncrhonize the timestamps to now
            
            if (charge.refresh()) {
                charge.state.timestamp = time;
                appContext.lastKnownChargeState.set(charge.state);
            }
            if (snapshot.refresh()) {
                snapshot.state.timestamp = time;
                appContext.lastKnownSnapshotState.set(snapshot.state);
            } else {
                Tesla.logger.warning("Snapshot refresh failed!");
            }
        }

        private boolean isCharging() {
            if (charge.state == null) return false;
            return(charge.state.chargingState == ChargeState.Status.Charging ||
                   charge.state.chargeRate > 0);
        }

        private int decay = 0;
        private boolean isInMotion() { return isInMotion(4); }
        private boolean isInMotion(int decaySetting) {
            if (snapshot.state != null) {
                if (snapshot.state.speed > 0.0) {
                    decay = decaySetting;
                    return true;
                }
            }
            return (--decay > 0);
        }
        
        private boolean appIsAwake() { return inactivityState != AppContext.InactivityType.Sleep; }
        
        @Override public void run() {
            long sleepInterval;
            
            while (!appContext.shuttingDown.get() && !stopCollecting) {
                boolean vehicleWasAsleep = false;
                if (appIsAwake()) {
                    publishStats();
                    sleepInterval = isInMotion() ? MinInterval : DefaultInterval;
                    Tesla.logger.info("App Awake, interval = " + sleepInterval/1000L);
                } else if (vehicle.isAwake()) {
                    publishStats();
                    sleepInterval = isInMotion() ? MinInterval :
                            (isCharging() ? DefaultInterval : AllowSleepInterval);
                    Tesla.logger.info("App Asleep, Car Awake, interval = " + sleepInterval/1000L);
                    if (sleepInterval < AllowSleepInterval)
                        appContext.inactivityState.set(AppContext.InactivityType.Awake);
                } else {
                    sleepInterval = AllowSleepInterval;
                    vehicleWasAsleep = true;
                    Tesla.logger.info("App Asleep, Car Asleep, interval = " + sleepInterval/1000L);
                }

                if (sleepInterval < AllowSleepInterval) {
                    Utils.sleep(sleepInterval);
                } else {
                    for (; sleepInterval > 0; sleepInterval -= TestSleepInterval) {
                        Utils.sleep(TestSleepInterval);
                        if (appIsAwake()) { Tesla.logger.info("App is awake, start polling"); break; }
                        if (vehicleWasAsleep && vehicle.isAwake()) {
                            appContext.inactivityState.set(AppContext.InactivityType.Awake);
                            Tesla.logger.info("Something woke the car, start polling");
                            break; 
                        }
                    }
                }
            }
        }
    }
}
