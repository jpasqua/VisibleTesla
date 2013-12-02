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

    private static final long DefaultInterval = 2 * 60 * 1000;  //  2 Minutes
    private static final long MinInterval =         30 * 1000;  // 30 Seconds

/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private static boolean stopCollecting = false;
    private AppContext appContext;
    private Thread collector;
    private ChargeState charge;
    private SnapshotState snapshot;
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public StatsStreamer(AppContext appContext, Vehicle v) {
        this.appContext = appContext;
        this.charge = new ChargeState(v);
        this.snapshot = new SnapshotState(v);
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
        
        private boolean publishStats() {
            long time = System.currentTimeMillis(); // Syncrhonize the timestamps to now
            double speed = 0.0;
            
            if (charge.refresh()) {
                charge.state.timestamp = time;
                appContext.lastKnownChargeState.set(charge.state);
            }
            if (snapshot.refresh()) {
                speed = snapshot.state.speed;
                snapshot.state.timestamp = time;
                appContext.lastKnownSnapshotState.set(snapshot.state);
            }

            return speed > 1.0;
        }
    
        @Override public void run() {
            long collectionInterval = DefaultInterval;
            int decay = 0;
            
            while (!appContext.shuttingDown.get() && !stopCollecting) {
                if (inactivityState != AppContext.InactivityType.Sleep) {
                    if (publishStats()) {
                        decay = 3;
                        collectionInterval = MinInterval;
                    } else {
                        decay = Math.max(decay - 1, 0);
                        collectionInterval = decay > 0 ? MinInterval : DefaultInterval;
                    }
                }
                Utils.sleep(collectionInterval);
            }
        }
    }
}
