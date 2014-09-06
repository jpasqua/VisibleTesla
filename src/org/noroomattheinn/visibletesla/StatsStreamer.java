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
    
    private final AppContext    appContext;
    private final Thread        collector;
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public StatsStreamer(AppContext appContext) {
        this.appContext = appContext;
        this.collector = appContext.tm.launch(new AutoCollect(), "CollectStats");
    }
    
    
    public void stop() {
        collector.interrupt();
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
            appContext.snapshotProducer.produce(appContext.prefs.streamWhenPossible.get());
            appContext.stateProducer.produce(StateProducer.StateType.Charge, null);
        }

        private boolean isCharging() {
            ChargeState.State charge = appContext.lastKnownChargeState.get();
            return(charge.chargingState == ChargeState.Status.Charging ||
                   charge.chargeRate > 0);
        }

        private int decay = 0;
        private boolean isInMotion() {
            if (appContext.lastKnownSnapshotState.get() != null) {
                if (appContext.lastKnownSnapshotState.get().speed > 0.0) {
                    decay = 4;
                    return true;
                }
            }
            return (Math.max(--decay, 0) > 0);
        }
        
        private boolean isCarAwake() {
            boolean awake = appContext.vehicle.isAwake();
            Tesla.logger.info("ICA="+awake);
            return awake;
        }
        
        @Override public void run() {
            try {
                long sleepInterval;

                while (!appContext.shuttingDown.get()) {
                    boolean vehicleWasAsleep = false;
                    if (!appContext.inactivity.isSleeping()) {
                        produceStats();
                        sleepInterval = isInMotion() ? MinInterval : DefaultInterval;
                        Tesla.logger.info("App Awake, interval = " + sleepInterval/1000L);
                    } else if (isCarAwake()) {
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
                            if (!appContext.inactivity.isSleeping()) { Tesla.logger.info("App is awake, start polling"); break; }
                            if (vehicleWasAsleep && isCarAwake()) {
                                appContext.inactivity.setState(Inactivity.Type.Awake);
                                Tesla.logger.info("Something woke the car, start polling");
                                break;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                Tesla.logger.severe("Uncaught exception in StatsStreamer: " + e.getMessage());
            }
        }
    }
}
