/*
 * StatsCollector - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Nov 30, 2014
 */
package org.noroomattheinn.visibletesla.data;

import com.google.common.collect.Range;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.NavigableMap;
import javafx.beans.property.IntegerProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import org.noroomattheinn.tesla.ChargeState;
import org.noroomattheinn.tesla.StreamState;
import org.noroomattheinn.timeseries.CachedTimeSeries;
import org.noroomattheinn.timeseries.IndexedTimeSeries;
import org.noroomattheinn.timeseries.Row;
import org.noroomattheinn.timeseries.TimeSeries;
import org.noroomattheinn.utils.GeoUtils;
import org.noroomattheinn.utils.Utils;
import org.noroomattheinn.utils.ThreadManager;
import org.noroomattheinn.visibletesla.vehicle.VTVehicle;
import static org.noroomattheinn.tesla.Tesla.logger;

/**
 * StatsCollector: Collect stats as they are generated, store them in
 * a TimeSeries, and allow queries against the data.
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
class StatsCollector implements ThreadManager.Stoppable {
    private static final long TenMinutes = 10 * 60 * 1000;

/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/

    private final VTData vtData;
    private final VTVehicle vtVehicle;
    private final CachedTimeSeries ts;
    private final IntegerProperty minTime;
    private final IntegerProperty minDist;
    private final File container;
    private DBConverter converter;
    private VTData.TimeBasedPredicate collectNow = new VTData.TimeBasedPredicate() {
        @Override public void setTime(long time) { }
        @Override public boolean eval() { return false; }
    };
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    /**
     * Create a new StatsCollector that will monitor new states being generated
     * by the StatsStreamer and persist them as appropriate. Not every state will
     * be persisted and not every value from each state is persisted. A state may
     * not be persisted if a previous state was persisted too "recently". This
     * constructor might result in upgrading the underlying repository if its
     * format has changed.
     * 
     * @throws IOException  If the underlying persistent store has a problem.
     */
    StatsCollector(
            File container, VTData vtData, VTVehicle v, Range<Long> loadPeriod,
            IntegerProperty minTime, IntegerProperty minDist)
            throws IOException {
        this.container = container;
        this.vtData = vtData;
        this.minTime = minTime;
        this.minDist = minDist;
        this.vtVehicle = v;
        
        this.ts = new CachedTimeSeries(
                container, vtVehicle.getVehicle().getVIN(), VTData.schema, loadPeriod);
        
        vtVehicle.streamState.addListener(new ChangeListener<StreamState>() {
            @Override public void changed(
                    ObservableValue<? extends StreamState> ov,
                    StreamState old, StreamState cur) {
                handleStreamState(cur);
            }
        });
        
        vtVehicle.chargeState.addListener(new ChangeListener<ChargeState>() {
            @Override public void changed(
                    ObservableValue<? extends ChargeState> ov,
                    ChargeState old, ChargeState cur) {
                handleChargeState(cur);
            }
        });
        
        ThreadManager.get().addStoppable((ThreadManager.Stoppable)this);
    }
    
    /**
     * Create a Row based on the Charge and Stream States provided. The timestamp
     * if based on the timestamp of the newest state object
     * @param cs    The ChargeState from which various column values will be pulled
     * @param ss    The StreamState from which various column values will be pulled
     * @return      The newly created and initialized Row
     */
    static Row rowFromStates(ChargeState cs, StreamState ss) {
        Row r = new Row(Math.max(cs.timestamp, ss.timestamp), 0L, VTData.schema.nColumns);
        
        r.set(VTData.schema, VTData.VoltageKey, cs.chargerVoltage);
        r.set(VTData.schema, VTData.CurrentKey, cs.chargerActualCurrent);
        r.set(VTData.schema, VTData.EstRangeKey, cs.range);
        r.set(VTData.schema, VTData.SOCKey, cs.batteryPercent);
        r.set(VTData.schema, VTData.ROCKey, cs.chargeRate);
        r.set(VTData.schema, VTData.BatteryAmpsKey, cs.batteryCurrent);
        r.set(VTData.schema, VTData.LatitudeKey, ss.estLat);
        r.set(VTData.schema, VTData.LongitudeKey, ss.estLng);
        r.set(VTData.schema, VTData.HeadingKey, ss.heading);
        r.set(VTData.schema, VTData.SpeedKey, ss.speed);
        r.set(VTData.schema, VTData.OdometerKey, ss.odometer);
        r.set(VTData.schema, VTData.PowerKey, ss.power);
        
        return r;
    }
    
    /**
     * Return a TimeSeries for all of the collected data. 
     * @return The TimeSeries
     */
    TimeSeries getFullTimeSeries() { return ts; }
    
    /**
     * Return an IndexedTimeSeries for only the data loaded into memory. The
     * range of data loaded is controlled by a preference.
     * @return The IndexedTimeSeries
     */
    IndexedTimeSeries getLoadedTimeSeries() { return ts.getCachedSeries(); }
    
    /**
     * Return an index on a set of rows covered by the period [startTime..endTime].
     * 
     * @param startTime Starting time for the period
     * @param endTime   Ending time for the period
     * @return A map from time -> Row for all rows in the time range
     */
    NavigableMap<Long,Row> getRangeOfLoadedRows(long startTime, long endTime) {
        return getLoadedTimeSeries().getIndex(Range.open(startTime, endTime));
    }
    
    /**
     * Return an index on the cached rows in the data store.
     *
     * @return A map from time -> Row for all rows in the store
     */
    NavigableMap<Long,Row> getAllLoadedRows() {
        return getLoadedTimeSeries().getIndex();
    }
    
    boolean export(File file, Range<Long> exportPeriod, String[] columns) {
        return ts.export(file, exportPeriod, Arrays.asList(columns), true);
    }
    
    /**
     * Shut down the StatsCollector.
     */
    @Override public void stop() { ts.close(); }
    
    boolean upgradeRequired() {
        converter = new DBConverter(container, vtVehicle.getVehicle().getVIN());
        return converter.conversionRequired();
    }
    
    boolean doUpgrade() {
        try {
            converter.convert();
        } catch (IOException e) {
            logger.severe("Unable to upgrade database: " + e);
            return false;
        }
        return true;
    }
    
    void setCollectNow(VTData.TimeBasedPredicate p) {
        collectNow = p;
    }
    
/*------------------------------------------------------------------------------
 *
 * PRIVATE - Methods related to storing new samples
 * 
 *----------------------------------------------------------------------------*/
    
    private synchronized void handleChargeState(ChargeState state) {
        Row r = new Row(state.timestamp, 0L, VTData.schema.nColumns);
        
        r.set(VTData.schema, VTData.VoltageKey, state.chargerVoltage);
        r.set(VTData.schema, VTData.CurrentKey, state.chargerActualCurrent);
        r.set(VTData.schema, VTData.EstRangeKey, state.range);
        r.set(VTData.schema, VTData.SOCKey, state.batteryPercent);
        r.set(VTData.schema, VTData.ROCKey, state.chargeRate);
        r.set(VTData.schema, VTData.BatteryAmpsKey, state.batteryCurrent);
        ts.storeRow(r);
        
        vtData.lastStoredChargeState.set(state);
    }
    
    private synchronized void handleStreamState(StreamState state) {
        StreamState lastRecorded = vtData.lastStoredStreamState.get();
        if (worthRecording(state, lastRecorded)) {
            Row r = new Row(state.timestamp, 0L, VTData.schema.nColumns);

            r.set(VTData.schema, VTData.LatitudeKey, state.estLat);
            r.set(VTData.schema, VTData.LongitudeKey, state.estLng);
            r.set(VTData.schema, VTData.HeadingKey, state.heading);
            r.set(VTData.schema, VTData.SpeedKey, Utils.round(state.speed, 1));
            r.set(VTData.schema, VTData.OdometerKey, state.odometer);
            r.set(VTData.schema, VTData.PowerKey, state.power);
            ts.storeRow(r);

            vtData.lastStoredStreamState.set(state);
        }
    }
    
    private boolean worthRecording(StreamState cur, StreamState last) {
        double meters = GeoUtils.distance(cur.estLat, cur.estLng, last.estLat, last.estLng);
        
        // The app becoming active makes it worth recording
        collectNow.setTime(last.timestamp);
        if (collectNow.eval()) return true;
        
        // A big turn makes it worth recording. Note that heading changes can be
        // spurious. They can happen when the car is sitting still. Ignore those.
        double turn =  180.0 - Math.abs((Math.abs(cur.heading - last.heading)%360.0) - 180.0);
        if (turn > 10 && moving(cur)) return true; 
        
        // A long time between readings makes it worth recording
        long timeDelta = Math.abs(cur.timestamp - last.timestamp);
        if (timeDelta > TenMinutes) { return true; }
        
        // A change in motion (moving->stationary or stationaty->moving) is worth recording
        if (moving(last) != moving(cur)) { return true; }
        
        // If you're moving and it's been a while since a reading, it's worth recording
        if ((timeDelta >= minTime.get() * 1000) &&
            (meters >= minDist.get())) return true;
        
        return false;
    }

    private boolean moving(StreamState state) { return state.speed > 0.1; }
}
