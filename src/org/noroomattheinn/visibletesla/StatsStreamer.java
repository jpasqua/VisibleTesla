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
        private boolean isInMotion() {
            if (snapshot.state != null) {
                if (snapshot.state.speed > 0.0) {
                    decay = 3;
                    return true;
                }
            }
            return (--decay > 0);
        }

        @Override public void run() {
            long dontProbeAgainUntil = 0;
            long sleepInterval = DefaultInterval;
            
            while (!appContext.shuttingDown.get() && !stopCollecting) {
                boolean appIsAwake = (inactivityState != AppContext.InactivityType.Sleep);
                if (appIsAwake || Tesla.isCarAwake(vehicle)) {
                    long now = System.currentTimeMillis();
                    if (now >= dontProbeAgainUntil) {
                        publishStats();
                        if (isInMotion()) {
                            // As long as we're in motion, we don't care whether the
                            // app is in AllowSleep mode, probe again soon
                            sleepInterval = MinInterval;
                            dontProbeAgainUntil = now + sleepInterval;
                            if (!appIsAwake) Tesla.logger.info("We're moving, so probe again really soon");
                        } else if (appIsAwake || isCharging()) {
                            sleepInterval = DefaultInterval;
                            dontProbeAgainUntil = now + sleepInterval;
                            if (!appIsAwake) Tesla.logger.info("We're awake or charging or both, so probe again soon");
                        } else {
                            sleepInterval = TestSleepInterval;
                            dontProbeAgainUntil = now + AllowSleepInterval;
                            if (!appIsAwake) Tesla.logger.info("We're not moving, charging, or awake, so give the car a chance to sleep");
                        }
                    } else {
                        // Don't bother the car yet...
                        sleepInterval = TestSleepInterval;
                        if (!appIsAwake) Tesla.logger.info("We're giving the car a chance to sleep");
                    }
                }
                Utils.sleep(sleepInterval);
            }
        }
    }
}
