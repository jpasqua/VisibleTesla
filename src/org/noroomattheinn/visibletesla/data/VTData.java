/*
 * VTData - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Dec 31, 2014
 */
package org.noroomattheinn.visibletesla.data;

import com.google.common.collect.Range;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.NavigableMap;
import javafx.scene.control.ProgressIndicator;
import org.noroomattheinn.tesla.ChargeState;
import org.noroomattheinn.tesla.StreamState;
import org.noroomattheinn.tesla.Vehicle;
import org.noroomattheinn.timeseries.Row;
import org.noroomattheinn.timeseries.RowDescriptor;
import org.noroomattheinn.utils.Executor.FeedbackListener;
import org.noroomattheinn.utils.TrackedObject;
import org.noroomattheinn.utils.Utils.Predicate;
import org.noroomattheinn.visibletesla.vehicle.VTVehicle;


/**
 * VTData: Data collection and storage for VisibleTesla
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class VTData {
    
/*------------------------------------------------------------------------------
 *
 * Constants and Enums
 * 
 *----------------------------------------------------------------------------*/

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
   
    private static VTData instance = null;
    
    private final File              container;
    private final FeedbackListener  feedbackListener;
    private final VTVehicle         vtVehicle;
    private       StatsCollector    statsCollector;
    private       StreamProducer    streamProducer;
    private       StateProducer     stateProducer;
    private       StatsStreamer     statsStreamer;
    private       RestStore         restStore;
    private       ChargeStore       chargeStore;
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public final TrackedObject<ChargeCycle>     lastChargeCycle;
    public final TrackedObject<RestCycle>       lastRestCycle;
    public final TrackedObject<StreamState>     lastStoredStreamState;
    public final TrackedObject<ChargeState>     lastStoredChargeState;


    public static VTData create(File container, VTVehicle v, FeedbackListener fl) {
        if (instance != null) return instance;
        return (instance = new VTData(container, fl, v));
    }
    
    public static VTData get() { return instance; }
        
/*------------------------------------------------------------------------------
 *
 * Set parameters of the VTData object
 * 
 *----------------------------------------------------------------------------*/
    
    public void setVehicle(Vehicle v) throws IOException {
        statsCollector = new StatsCollector(container, this, vtVehicle);
        streamProducer = new StreamProducer(vtVehicle, feedbackListener);
        stateProducer = new StateProducer(vtVehicle, feedbackListener);
        statsStreamer = new StatsStreamer(this, vtVehicle);
        initChargeStore();
        initRestStore();
    }
    
    public void setWakeEarly(Predicate wakeEarly) {
        statsStreamer.setWakeEarly(wakeEarly);
    }

    public void setPassiveCollection(Predicate pc) {
        statsStreamer.setPassiveCollection(pc);
    }

    public void setCollectNow(TimeBasedPredicate p) {
        statsCollector.setCollectNow(p);
    }
    
/*------------------------------------------------------------------------------
 *
 * Upgrade related methods
 * 
 *----------------------------------------------------------------------------*/
    
    public boolean upgradeRequired() {
        return statsCollector.upgradeRequired();
    }
    
    public boolean doUpgrade() {
        return statsCollector.doUpgrade();
    }
    
/*------------------------------------------------------------------------------
 *
 * Producing States and Streams
 * 
 *----------------------------------------------------------------------------*/
    
    public void produceStream(boolean stream) {
        streamProducer.produce(stream);
    }
    
    public void produceState(Vehicle.StateType whichState, ProgressIndicator pi) {
        stateProducer.produce(whichState, pi);
    }
    
/*------------------------------------------------------------------------------
 *
 * Getting and Exporting Cycle information
 * 
 *----------------------------------------------------------------------------*/
    
    public boolean export(File file, Range<Long> exportPeriod, String[] columns) {
        return statsCollector.export(file, exportPeriod, columns);
    }
    
    public boolean exportRests(File toFile, Range<Long> exportPeriod) {
        return restStore.export(toFile, exportPeriod);
    }
    
    public boolean exportCharges(File toFile, Range<Long> exportPeriod) {
        return chargeStore.export(toFile, exportPeriod);
    }
    
    public List<RestCycle> getRestCycles(Range<Long> period) {
        return restStore.getCycles(period);
    }
    
    public List<ChargeCycle> getChargeCycles(Range<Long> period) {
        return chargeStore.getCycles(period);
    }
    
    public boolean exportTripsAsKML(List<Trip> trips, File toFile) {
        KMLExporter ke = new KMLExporter();
        return ke.export(trips, toFile);
    }
    
/*------------------------------------------------------------------------------
 *
 * Accessing stored Data
 * 
 *----------------------------------------------------------------------------*/
    
    /**
     * Return an index on the cached rows in the data store.
     *
     * @return A map from time -> Row for all rows in the store
     */
    public NavigableMap<Long,Row> getAllLoadedRows() {
        return statsCollector.getAllLoadedRows();
    }
    
    /**
     * Return an index on a set of rows covered by the period [startTime..endTime].
     * 
     * @param startTime Starting time for the period
     * @param endTime   Ending time for the period
     * @return A map from time -> Row for all rows in the time range
     */
    public NavigableMap<Long,Row> getRangeOfLoadedRows(long startTime, long endTime) {
        return statsCollector.getRangeOfLoadedRows(startTime, endTime);
    }
    
/*------------------------------------------------------------------------------
 *
 * Hide the constructor
 * 
 *----------------------------------------------------------------------------*/
    
    private VTData(File container, FeedbackListener feedbackListener, VTVehicle v) {
        this.container = container;
        this.vtVehicle = v;
        this.lastChargeCycle = new TrackedObject<>(null);
        this.lastRestCycle = new TrackedObject<>(null);
        this.feedbackListener = feedbackListener;
        this.lastStoredStreamState = new TrackedObject<>(new StreamState());
        this.lastStoredChargeState = new TrackedObject<>(null);        
    }
    
/*------------------------------------------------------------------------------
 *
 * Private Utility Methods
 * 
 *----------------------------------------------------------------------------*/

    private void initRestStore() throws FileNotFoundException {
        boolean needsInitialLoad = RestStore.requiresInitialLoad(
                container, vtVehicle.getVehicle().getVIN());
        restStore = new RestStore(container, vtVehicle, lastRestCycle);
        RestMonitor rm = new RestMonitor(vtVehicle, lastRestCycle);
        if (needsInitialLoad) {
            restStore.doIntialLoad(rm, statsCollector.getFullTimeSeries());
        }
    }
    
    private void initChargeStore() throws FileNotFoundException {
        chargeStore = new ChargeStore(container, vtVehicle, lastChargeCycle);
        ChargeMonitor cm = new ChargeMonitor(vtVehicle, lastChargeCycle);
    }
    
/*------------------------------------------------------------------------------
 *
 * Helper Classes
 * 
 *----------------------------------------------------------------------------*/

    public static interface TimeBasedPredicate extends Predicate {
        void setTime(long time);
    }
}