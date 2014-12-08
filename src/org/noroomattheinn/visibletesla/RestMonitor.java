/*
 * RestMonitor.java - Copyright(c) 2013, 204 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Oct 22, 2014
 */
package org.noroomattheinn.visibletesla;

import java.util.Calendar;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import org.noroomattheinn.tesla.ChargeState;
import org.noroomattheinn.timeseries.Row;
import org.noroomattheinn.utils.CalTime;

/**
 * RestMonitor - Monitor and store data about Rest Cycles.
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class RestMonitor {
/*------------------------------------------------------------------------------
 *
 * Constants and Enums
 * 
 *----------------------------------------------------------------------------*/

    private static final long MIN_REST_PERIOD = 60 * 60 * 1000; // 60 Minutes
    
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private final AppContext    ac;
    private final Calendar      fromLimit, toLimit;
    private final boolean       stradles;
    private RestCycle           cycleInProgress = null;
    
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/

    RestMonitor(AppContext appContext) {
        this.ac = appContext;
        
        if (ac.prefs.vsLimitEnabled.get()) {
            fromLimit = ac.prefs.vsFrom.get();
            toLimit = ac.prefs.vsTo.get();
            stradles = (toLimit.before(fromLimit));
        } else {
            fromLimit = toLimit = null;
            stradles = false;
        }
              
        this.cycleInProgress = null;
        ac.lastChargeState.addListener(new ChangeListener<ChargeState>() {
            @Override public void changed(ObservableValue<? extends ChargeState> ov, ChargeState old, ChargeState cur) {
                handleNewData(
                        ac.statsCollector.rowFromStates(cur, ac.lastStreamState.get()));
            }
        });
    }

    public void handleNewData(Row r) {
        long timestamp = r.timestamp;

        if (outOfRange(timestamp)) {
            if (cycleInProgress != null) { completeCycle(r); }
            return;
        }

        double speed = r.get(StatsCollector.schema, StatsCollector.SpeedKey);
        double voltage = r.get(StatsCollector.schema, StatsCollector.VoltageKey);
        boolean idle = (speed == 0 && voltage < 100);
        
        if (cycleInProgress == null ) { // Not in a cycle
            if (idle) { startCycle(r); }
        } else {                        // In the middle of a cycle
            if (idle) { updateCycle(r); }
            else { completeCycle(r); }
        }
    }
    
    private void startCycle(Row r) {
        cycleInProgress = new RestCycle();
        cycleInProgress.startTime = r.timestamp;
        cycleInProgress.startRange = r.get(StatsCollector.schema, StatsCollector.EstRangeKey);
        cycleInProgress.startSOC = r.get(StatsCollector.schema, StatsCollector.SOCKey);
    }

    private void updateCycle(Row r) {
        cycleInProgress.endTime = r.timestamp;
        cycleInProgress.endRange = r.get(StatsCollector.schema, StatsCollector.EstRangeKey);
        cycleInProgress.endSOC = r.get(StatsCollector.schema, StatsCollector.SOCKey);
        cycleInProgress.lat = r.get(StatsCollector.schema, StatsCollector.LatitudeKey);
        cycleInProgress.lng = r.get(StatsCollector.schema, StatsCollector.LongitudeKey);
    }
    
    private void completeCycle(Row r) {
        updateCycle(r);
        if (cycleInProgress.endTime - cycleInProgress.startTime > MIN_REST_PERIOD) {
            // OK, there's another odd situation to handle. If we start
            // a rest period and then stop getting data, we may miss a
            // charge. In that case the rest may look like we gained
            // power instead of losing power. In that case just toss
            // the rest period. It will skew the data.
            if (cycleInProgress.endRange <= cycleInProgress.startRange) {
                ac.lastRestCycle.set(cycleInProgress);
            }
        }
        cycleInProgress = null;        
    }
    
    private boolean outOfRange(long ts) {
        if (!ac.prefs.vsLimitEnabled.get()) return false;
        CalTime c = new CalTime(ts);
        if (stradles) { return (c.after(toLimit) && c.before(fromLimit)); }
        else { return c.after(toLimit) || c.before(fromLimit); }
    }

}
