/*
 * StatsStreamer.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Nov 26, 2013
 */

package org.noroomattheinn.visibletesla;

import org.noroomattheinn.tesla.ChargeState;
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
    
    private final AppContext    ac;
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public StatsStreamer(AppContext appContext) {
        this.ac = appContext;
        appContext.tm.launch(new AutoCollect(), "CollectStats");
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
            ac.streamProducer.produce(ac.prefs.streamWhenPossible.get());
            ac.stateProducer.produce(Vehicle.StateType.Charge, null);
        }

        private boolean isCharging() {
            ChargeState charge = ac.lastKnownChargeState.get();
            return(charge.chargingState == ChargeState.Status.Charging ||
                   charge.chargeRate > 0);
        }

        private int decay = 0;
        private boolean isInMotion() {
            if (ac.lastKnownStreamState.get() != null) {
                if (ac.lastKnownStreamState.get().speed > 0.0) {
                    decay = 4;
                    return true;
                }
            }
            return (Math.max(--decay, 0) > 0);
        }
        
        private boolean isCarAwake() {
            boolean awake = ac.vehicle.isAwake();
            //Tesla.logger.info("ICA="+awake);
            return awake;
        }
        
        @Override public void run() {
            try {
                long sleepInterval;

                while (!ac.shuttingDown.get()) {
                    boolean carWasAsleep = false;
                    if (ac.inactivity.isAwake()) {
                        produceStats();
                        sleepInterval = isInMotion() ? MinInterval : DefaultInterval;
                        Tesla.logger.info("App Awake, interval = " + sleepInterval/1000L);
                    } else if (isCarAwake()) {
                        produceStats();
                        sleepInterval = isInMotion() ? MinInterval :
                                (isCharging() ? DefaultInterval : AllowSleepInterval);
                        Tesla.logger.info("App Asleep, Car Awake, interval = " + sleepInterval/1000L);
                        if (sleepInterval < AllowSleepInterval)
                            ac.inactivity.setState(Inactivity.Type.Awake);
                    } else {
                        sleepInterval = AllowSleepInterval;
                        carWasAsleep = true;
                        Tesla.logger.info("App Asleep, Car Asleep, interval = " + sleepInterval/1000L);
                    }

                    if (sleepInterval < AllowSleepInterval) {
                        ac.utils.sleep(sleepInterval);
                    } else {
                        for (; sleepInterval > 0; sleepInterval -= TestSleepInterval) {
                            Utils.sleep(TestSleepInterval, becameAwake);
                            if (ac.shuttingDown.get()) break;
                            if (ac.inactivity.isAwake()) { Tesla.logger.info("App is awake, start polling"); break; }
                            if (carWasAsleep && isCarAwake()) {
                                ac.inactivity.setState(Inactivity.Type.Awake);
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
    
    Utils.Predicate becameAwake = new Utils.Predicate() {
        @Override public boolean eval() {
            return ac.shuttingDown.get() || ac.inactivity.isAwake(); }
    };
}
