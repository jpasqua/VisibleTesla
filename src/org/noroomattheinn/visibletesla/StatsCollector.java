/*
 * StatsCollector - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Nov 30, 2014
 */
package org.noroomattheinn.visibletesla;

import com.google.common.collect.Range;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.NavigableMap;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.stage.FileChooser;
import org.noroomattheinn.tesla.ChargeState;
import org.noroomattheinn.tesla.StreamState;
import org.noroomattheinn.timeseries.CachedTimeSeries;
import org.noroomattheinn.timeseries.IndexedTimeSeries;
import org.noroomattheinn.timeseries.Row;
import org.noroomattheinn.timeseries.RowDescriptor;
import org.noroomattheinn.utils.GeoUtils;
import org.noroomattheinn.visibletesla.dialogs.DateRangeDialog;
import org.noroomattheinn.visibletesla.fxextensions.TrackedObject;

/**
 * StatsCollector: Collect stats as they are generated, store them in
 * a TimeSeries, and allow queries against the data.
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class StatsCollector implements ThreadManager.Stoppable {
/*------------------------------------------------------------------------------
 *
 * Constants and Enums
 * 
 *----------------------------------------------------------------------------*/
    public static String LastExportDirKey = "APP_LAST_EXPORT_DIR";
    
    // Data that comes from the ChargeState
    public static final String VoltageKey =     "C_VLT";
    public static final String CurrentKey =     "C_AMP";
    public static final String EstRangeKey =    "C_EST";
    public static final String SOCKey =         "C_SOC";
    public static final String ROCKey =         "C_ROC";
    public static final String BatteryAmpsKey = "C_BAM";
    
    // Data that comes from the StreamState
    public static final String LatitudeKey =    "L_LAT";
    public static final String LongitudeKey =   "L_LNG";
    public static final String HeadingKey =     "L_HDG";
    public static final String SpeedKey =       "L_SPD";
    public static final String OdometerKey =    "L_ODO";
    public static final String PowerKey =       "L_PWR";
    
    public static final String[] Columns = {
        VoltageKey, CurrentKey, EstRangeKey, SOCKey, ROCKey, BatteryAmpsKey,
        LatitudeKey, LongitudeKey, HeadingKey, SpeedKey, OdometerKey, PowerKey};
    public static final RowDescriptor schema = new RowDescriptor(Columns);

/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/

    private final AppContext ac;
    private final CachedTimeSeries ts;
            
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public final TrackedObject<StreamState> lastStoredStreamState;
    public final TrackedObject<ChargeState> lastStoredChargeState;

    public StatsCollector(AppContext appContext)
            throws IOException {
        upgradeIfNeeded(appContext.appFileFolder(), appContext.vehicle.getVIN());
        
        this.ac = appContext;
        this.ts = new CachedTimeSeries(
                ac.appFileFolder(), ac.vehicle.getVIN(),
                schema, ac.prefs.getLoadPeriod());

        this.lastStoredStreamState = new TrackedObject<>(null);
        this.lastStoredChargeState = new TrackedObject<>(null);
        
        appContext.lastStreamState.addListener(new ChangeListener<StreamState>() {
            @Override public void changed(
                    ObservableValue<? extends StreamState> ov,
                    StreamState old, StreamState cur) {
                handStreamState(cur);
            }
        });
        
        appContext.lastChargeState.addListener(new ChangeListener<ChargeState>() {
            @Override public void changed(
                    ObservableValue<? extends ChargeState> ov,
                    ChargeState old, ChargeState cur) {
                handleChargeState(cur);
            }
        });
    }
    
    public IndexedTimeSeries getLoadedData() { return ts.getCachedSeries(); }
    
    public NavigableMap<Long,Row> getRange(long startTime, long endTime) {
        return getLoadedData().getIndex(Range.open(startTime, endTime));
    }
    
    public NavigableMap<Long,Row> getAll() {
        return getLoadedData().getIndex();
    }
            
    public void export(String[] columns) {
        String initialDir = ac.persistentState.get(
                LastExportDirKey, System.getProperty("user.home"));
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Data");
        fileChooser.setInitialDirectory(new File(initialDir));

        File file = fileChooser.showSaveDialog(ac.stage);
        if (file != null) {
            String enclosingDirectory = file.getParent();
            if (enclosingDirectory != null)
                ac.persistentState.put(LastExportDirKey, enclosingDirectory);
            Range<Long> exportPeriod = DateRangeDialog.getExportPeriod(ac.stage);
            if (exportPeriod == null)
                return;
            ts.export(file, exportPeriod, Arrays.asList(columns), true);
        }
    }
    
    @Override public void stop() { ts.close(); }

/*------------------------------------------------------------------------------
 *
 * PRIVATE - Methods related to storing new samples
 * 
 *----------------------------------------------------------------------------*/
    
    private synchronized void handleChargeState(ChargeState state) {
        long timestamp = state.timestamp;
        
        ts.storeValue(timestamp, VoltageKey, state.chargerVoltage);
        ts.storeValue(timestamp, CurrentKey, state.chargerActualCurrent);
        ts.storeValue(timestamp, EstRangeKey, state.range);
        ts.storeValue(timestamp, SOCKey, state.batteryPercent);
        ts.storeValue(timestamp, ROCKey, state.chargeRate);
        ts.storeValue(timestamp, BatteryAmpsKey, state.batteryCurrent);
        lastStoredChargeState.set(state);
    }
    
    private synchronized void handStreamState(StreamState state) {
        if (state == null || tooClose(state, lastStoredStreamState.get())) return;
        
        double speed = Math.round(state.speed*10.0)/10.0;
        long timestamp = state.timestamp;
        ts.storeValue(timestamp, LatitudeKey, state.estLat);
        ts.storeValue(timestamp, LongitudeKey, state.estLng);
        ts.storeValue(timestamp, HeadingKey, state.heading);
        ts.storeValue(timestamp, SpeedKey, speed);
        ts.storeValue(timestamp, OdometerKey, state.odometer);
        
        lastStoredStreamState.set(state);
    }
    
    private boolean tooClose(StreamState wp1, StreamState wp2) {
        if (wp1 == null || wp2 == null) return false;
        
        double meters = GeoUtils.distance(wp1.estLat, wp1.estLng, wp2.estLat, wp2.estLng);
        
        
        // A big turn makes it "far". Note that heading changes can be spurious.
        // Sometimes we see heading changes when the car is sitting still.
        // Ignore those.
        double turn =  180.0 - Math.abs((Math.abs(wp1.heading - wp2.heading)%360.0) - 180.0);
        if (turn > 10 && meters > 0.05) return false; 
        
        // A long time between readings makes it "far"
        long timeDelta = Math.abs(wp1.timestamp - wp2.timestamp);
        if (timeDelta > 10 * 60 * 1000) return false;
        
        // A short time between readings makes it "too close"
        if (timeDelta < ac.prefs.locMinTime.get() * 1000)  return true; 
        
        // A short distance between readings makes it "too close"
        return (meters < ac.prefs.locMinDist.get());
    }

    private boolean upgradeIfNeeded(File container, String baseName) throws IOException {
        DBConverter converter = new DBConverter(container, baseName);
        if (!converter.conversionRequired()) return false;
        converter.convert();
        return true;
    }
}
