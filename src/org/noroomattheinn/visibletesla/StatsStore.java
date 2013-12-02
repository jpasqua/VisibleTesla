/*
 * StatsStore.java -  - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Nov 26, 2013
 */

package org.noroomattheinn.visibletesla;

import java.io.File;
import java.util.Timer;
import java.util.TimerTask;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import org.noroomattheinn.tesla.ChargeState;
import org.noroomattheinn.tesla.SnapshotState;

/**
 * Listen for changes in general stats and store them in a StatsRepository
 * 
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class StatsStore extends DataStore {

/*------------------------------------------------------------------------------
 *
 * Constants and Enums
 * 
 *----------------------------------------------------------------------------*/

    public static final String VoltageKey = "C_VLT";
    public static final String CurrentKey = "C_AMP";
    public static final String EstRangeKey = "C_EST";
    public static final String SOCKey = "C_SOC";
    public static final String ROCKey = "C_ROC";
    public static final String BatteryAmpsKey = "C_BAM";
    public static final String PowerKey = "S_PWR";
    public static final String SpeedKey = "S_SPD";
    public static final String[] Keys = {
        VoltageKey, CurrentKey, EstRangeKey, SOCKey, ROCKey,
        BatteryAmpsKey, PowerKey, SpeedKey };
    
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private Timer timer;

/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/


    public StatsStore(AppContext appContext, File locationFile) {
        super(appContext, locationFile, Keys);
        
        this.timer = new Timer("00 - VT StatsFlusher", true);
        timer.schedule(flusher, 0L, 5 * 1000L);
        
        appContext.lastKnownSnapshotState.addListener(new ChangeListener<SnapshotState.State>() {
            @Override public void changed(
                    ObservableValue<? extends SnapshotState.State> ov,
                    SnapshotState.State old, SnapshotState.State cur) {
                processSnaphostState(cur);
            }
        });
        
        appContext.lastKnownChargeState.addListener(new ChangeListener<ChargeState.State>() {
            @Override public void changed(
                    ObservableValue<? extends ChargeState.State> ov,
                    ChargeState.State old, ChargeState.State cur) {
                processChargeState(cur);
            }
        });
        
        load(getLoadPeriod());
    }
    
    @Override public void close() {
        super.close();
        timer.cancel();
    }
        
/*------------------------------------------------------------------------------
 *
 * PRIVATE - Methods related to storing new samples
 * 
 *----------------------------------------------------------------------------*/
    
    private long lastUpdate = 0;
    
    private TimerTask flusher = new TimerTask() {
        @Override public void run() {
            long now = System.currentTimeMillis();
            if (lastUpdate != 0 && now - lastUpdate > (2 * 1000L))
                repo.flushElements();
        }
    };
    
    private synchronized void processChargeState(ChargeState.State state) {
        long timestamp = state.timestamp;

        repo.storeElement(VoltageKey, timestamp, state.chargerVoltage);
        repo.storeElement(CurrentKey, timestamp, state.chargerActualCurrent);
        repo.storeElement(EstRangeKey, timestamp, state.range);
        repo.storeElement(SOCKey, timestamp, state.batteryPercent);
        repo.storeElement(ROCKey, timestamp, state.chargeRate);
        repo.storeElement(BatteryAmpsKey, timestamp, state.batteryCurrent);
        lastUpdate = timestamp;
    }
    
    private synchronized void processSnaphostState(SnapshotState.State state) {
        if (tooManySnapshots()) return;
        long timestamp = state.timestamp;
        double speed = Math.round(state.speed*10.0)/10.0;
        
        repo.storeElement(PowerKey, timestamp, state.power);
        repo.storeElement(SpeedKey, timestamp, speed);
        lastUpdate = timestamp;
    }
    
    private long lastSnapshot = 0;
    private boolean tooManySnapshots() {
        long now = System.currentTimeMillis();
        if (now - lastSnapshot < appContext.prefs.locMinTime.get() * 1000)
            return true;
        lastSnapshot = now;
        return false;
    }
}
