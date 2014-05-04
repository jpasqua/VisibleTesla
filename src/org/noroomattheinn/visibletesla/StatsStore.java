/*
 * StatsStore.java -  - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Nov 26, 2013
 */

package org.noroomattheinn.visibletesla;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import org.noroomattheinn.tesla.ChargeState;
import org.noroomattheinn.tesla.SnapshotState;
import org.noroomattheinn.utils.Utils;
import org.noroomattheinn.visibletesla.stats.Stat;

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
 * Public State
 * 
 *----------------------------------------------------------------------------*/
    
    public final ObjectProperty<Stat> newestVoltage = new SimpleObjectProperty<>();
    public final ObjectProperty<Stat> newestCurrent = new SimpleObjectProperty<>();
    public final ObjectProperty<Stat> newestEstRange = new SimpleObjectProperty<>();
    public final ObjectProperty<Stat> newestSOC = new SimpleObjectProperty<>();
    public final ObjectProperty<Stat> newestROC = new SimpleObjectProperty<>();
    public final ObjectProperty<Stat> newestBatteryAmps = new SimpleObjectProperty<>();
    public final ObjectProperty<Stat> newestPower = new SimpleObjectProperty<>();
    public final ObjectProperty<Stat> newestSpeed = new SimpleObjectProperty<>();
    
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private final Timer timer;
    private Map<String,ObjectProperty<Stat>> keyToProp = Utils.newHashMap(
            VoltageKey, newestVoltage,
            CurrentKey, newestCurrent,
            EstRangeKey, newestEstRange,
            SOCKey, newestSOC,
            ROCKey, newestROC,
            BatteryAmpsKey, newestBatteryAmps,
            PowerKey, newestPower,
            SpeedKey, newestSpeed);
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/


    public StatsStore(AppContext appContext, File locationFile) throws IOException {
        super(appContext, locationFile, Keys);
        
        this.timer = new Timer("00 - VT StatsFlusher", true);
        timer.schedule(flusher, 0L, 5 * 1000L);
        
        appContext.lastKnownSnapshotState.addListener(new ChangeListener<SnapshotState.State>() {
            @Override public void changed(
                    ObservableValue<? extends SnapshotState.State> ov,
                    SnapshotState.State old, SnapshotState.State state) {
                processSnapshot(state);
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
    
    private final TimerTask flusher = new TimerTask() {
        @Override public void run() {
            long now = System.currentTimeMillis();
            if (lastUpdate != 0 && now - lastUpdate > (2 * 1000L))
                repo.flushElements();
        }
    };
    
    private void store(String key, long timestamp, double val) {
        storeItem(key, timestamp, val);
        keyToProp.get(key).set(new Stat(timestamp, key, val));
    }
    
    private synchronized void processChargeState(ChargeState.State state) {
        long timestamp = state.timestamp;
        
        if (deferredSnapshot != null) { recordSnapshot(deferredSnapshot); }
        
        store(VoltageKey, timestamp, state.chargerVoltage);
        store(CurrentKey, timestamp, state.chargerActualCurrent);
        store(EstRangeKey, timestamp, state.range);
        store(SOCKey, timestamp, state.batteryPercent);
        store(ROCKey, timestamp, state.chargeRate);
        store(BatteryAmpsKey, timestamp, state.batteryCurrent);
        lastUpdate = timestamp;
    }
    
    private SnapshotState.State lastState = null;    
    private SnapshotState.State deferredSnapshot = null;    
    private synchronized void processSnapshot(SnapshotState.State state) {
        // If it's the first time through, record the snapshot
        if (lastState == null) { recordSnapshot(state); return; }
        
        // Honor the Prefs setting - Don't record too many snapshots. 
        if (state.timestamp - lastState.timestamp < appContext.prefs.locMinTime.get() * 1000)
            return;
        
        if (state.power != 0 || state.speed != 0) {
            recordSnapshot(state);      // Always record interesting data
        } else {
            deferredSnapshot = state;    // Defer recording zeroes
        }
    }
    
    private  void recordSnapshot(SnapshotState.State state) {
        store(PowerKey, state.timestamp, state.power);
        store(SpeedKey, state.timestamp, Math.round(state.speed * 10.0) / 10.0);
        lastState = state;
        deferredSnapshot = null;
    }    
    
}
