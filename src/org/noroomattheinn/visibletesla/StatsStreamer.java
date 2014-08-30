/*
 * StatsStreamer.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Nov 26, 2013
 */

package org.noroomattheinn.visibletesla;

import org.noroomattheinn.tesla.ChargeState;
import org.noroomattheinn.tesla.Tesla;
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
    
    private static boolean      stopCollecting = false;
    private final AppContext    appContext;
    private final Thread        collector;
    private final ChargeState   charge;
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public StatsStreamer(AppContext appContext) {
        this.appContext = appContext;
        this.charge = new ChargeState(appContext.vehicle);
        this.collector = appContext.tm.launch(new AutoCollect(), "00 VT - CollectStats");
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

        private void produceStats() {
            long time = System.currentTimeMillis(); // Syncrhonize the timestamps to now
            
            appContext.snapshotStreamer.produce(appContext.prefs.streamWhenPossible.get());
            if (charge.refresh()) {
                charge.state.timestamp = time;
                appContext.lastKnownChargeState.set(charge.state);
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
            if (appContext.lastKnownSnapshotState.get() != null) {
                if (appContext.lastKnownSnapshotState.get().speed > 0.0) {
                    decay = decaySetting;
                    return true;
                }
            }
            return (--decay > 0);
        }
        
        @Override public void run() {
            try {
                long sleepInterval;

                while (!appContext.shuttingDown.get() && !stopCollecting) {
                    boolean vehicleWasAsleep = false;
                    if (appContext.inactivity.isAwake()) {
                        produceStats();
                        sleepInterval = isInMotion() ? MinInterval : DefaultInterval;
                        Tesla.logger.info("App Awake, interval = " + sleepInterval/1000L);
                    } else if (appContext.vehicle.isAwake()) {
                        produceStats();
                        sleepInterval = isInMotion() ? MinInterval :
                                (isCharging() ? DefaultInterval : AllowSleepInterval);
                        Tesla.logger.info("App Asleep, Car Awake, interval = " + sleepInterval/1000L);
                        if (sleepInterval < AllowSleepInterval)
                            appContext.inactivity.setState(Inactivity.Type.Awake);
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
                            if (appContext.inactivity.isAwake()) { Tesla.logger.info("App is awake, start polling"); break; }
                            if (vehicleWasAsleep && appContext.vehicle.isAwake()) {
                                appContext.inactivity.setState(Inactivity.Type.Awake);
                                Tesla.logger.info("Something woke the car, start polling");
                                break;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Tesla.logger.severe("Uncaught exception in StatsStreamer: " + e.getMessage());
            }
        }
    }
}
