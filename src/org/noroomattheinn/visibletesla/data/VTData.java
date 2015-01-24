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
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.ProgressIndicator;
import org.noroomattheinn.tesla.ChargeState;
import org.noroomattheinn.tesla.StreamState;
import org.noroomattheinn.tesla.Vehicle;
import org.noroomattheinn.timeseries.Row;
import org.noroomattheinn.timeseries.RowDescriptor;
import org.noroomattheinn.utils.CalTime;
import org.noroomattheinn.utils.Executor.FeedbackListener;
import org.noroomattheinn.utils.TrackedObject;
import org.noroomattheinn.utils.Utils.Predicate;
import org.noroomattheinn.visibletesla.vehicle.VTVehicle;
import static org.noroomattheinn.tesla.Tesla.logger;


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
    
    static final String[] Columns = {
        VoltageKey, CurrentKey, EstRangeKey, SOCKey, ROCKey, BatteryAmpsKey,
        LatitudeKey, LongitudeKey, HeadingKey, SpeedKey, OdometerKey, PowerKey};
    public static final RowDescriptor schema = new RowDescriptor(Columns);

/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
   
    private final File              container;
    private final FeedbackListener  feedbackListener;
    private final VTVehicle         vtVehicle;
    private final Options           options;
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

    public VTData(File container, Options options, VTVehicle v, FeedbackListener fl) {
        this.container = container;
        this.options = options;
        this.vtVehicle = v;
        this.lastChargeCycle = new TrackedObject<>(null);
        this.lastRestCycle = new TrackedObject<>(null);
        this.feedbackListener = fl;
        this.lastStoredStreamState = new TrackedObject<>(new StreamState());
        this.lastStoredChargeState = new TrackedObject<>(null);        
    }
    
    public static class Options {
        public final ObjectProperty<Range<Long>> loadPeriod;
        public final IntegerProperty  locMinTime;
        public final IntegerProperty  locMinDist;
        public final BooleanProperty  streamWhenPossible;
        public final BooleanProperty  submitAnonCharge;
        public final BooleanProperty  submitAnonRest;
        public final BooleanProperty  includeLocData;
        public final DoubleProperty   ditherLocAmt;
        public final BooleanProperty  restLimitEnabled;
        public final ObjectProperty<CalTime>  restLimitFrom;
        public final ObjectProperty<CalTime>  restLimitTo;
        
        public Options() {
            this.loadPeriod = new SimpleObjectProperty<>();
            this.locMinTime  = new SimpleIntegerProperty();
            this.locMinDist  = new SimpleIntegerProperty();
            this.streamWhenPossible = new SimpleBooleanProperty();
            this.submitAnonCharge = new SimpleBooleanProperty();
            this.submitAnonRest = new SimpleBooleanProperty();
            this.includeLocData = new SimpleBooleanProperty();
            this.ditherLocAmt = new SimpleDoubleProperty();
            this.restLimitEnabled = new SimpleBooleanProperty();
            this.restLimitFrom = new SimpleObjectProperty<>();
            this.restLimitTo = new SimpleObjectProperty<>();
        }
        
        public Options(
                ObjectProperty<Range<Long>> loadPeriod,
                IntegerProperty locMinTime, IntegerProperty locMinDist,
                BooleanProperty streamWhenPossible,
                BooleanProperty submitAnonCharge, BooleanProperty submitAnonRest,
                BooleanProperty includeLocData, DoubleProperty ditherLocAmt,
                BooleanProperty restLimitEnabled,
                ObjectProperty<CalTime> restLimitFrom,
                ObjectProperty<CalTime> restLimitTo) {
            this.loadPeriod = loadPeriod;
            this.locMinTime = locMinTime;
            this.locMinDist = locMinDist;
            this.streamWhenPossible = streamWhenPossible;
            this.submitAnonCharge = submitAnonCharge;
            this.submitAnonRest = submitAnonRest;
            this.includeLocData = includeLocData;
            this.ditherLocAmt = ditherLocAmt;
            this.restLimitEnabled = restLimitEnabled;
            this.restLimitFrom = restLimitFrom;
            this.restLimitTo = restLimitTo;
        }
    }
    
/*------------------------------------------------------------------------------
 *
 * Set parameters of the VTData object
 * 
 *----------------------------------------------------------------------------*/
    
    public void setVehicle(Vehicle v) throws IOException {
        statsCollector = new StatsCollector(
                container, this, vtVehicle, options.loadPeriod.get(),
                options.locMinTime, options.locMinDist);
        streamProducer = new StreamProducer(vtVehicle, feedbackListener);
        stateProducer = new StateProducer(vtVehicle, feedbackListener);
        statsStreamer = new StatsStreamer(this, vtVehicle, options.streamWhenPossible);
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
    
    public boolean upgradeRequired(Vehicle v) {
        return StatsCollector.upgradeRequired(container, v);
    }
    
    public boolean doUpgrade(Vehicle v) {
        return StatsCollector.doUpgrade(container, v);
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
 * Private Utility Methods
 * 
 *----------------------------------------------------------------------------*/

    private void initRestStore() throws FileNotFoundException {
        boolean needsInitialLoad = RestStore.requiresInitialLoad(
                container, vtVehicle.getVehicle().getVIN());
        restStore = new RestStore(
                container, vtVehicle, lastRestCycle,
                options.submitAnonRest,
                options.includeLocData,
                options.ditherLocAmt);
        RestMonitor rm = new RestMonitor(
                vtVehicle, lastRestCycle,
                options.restLimitEnabled,
                options.restLimitFrom, options.restLimitTo);
        if (needsInitialLoad) {
            restStore.doIntialLoad(rm, statsCollector.getFullTimeSeries());
        }
    }
    
    private void initChargeStore() throws FileNotFoundException {
        chargeStore = new ChargeStore(
                container, vtVehicle, lastChargeCycle,
                options.submitAnonCharge, options.includeLocData,
                options.ditherLocAmt);
        ChargeMonitor cm = new ChargeMonitor(vtVehicle, lastChargeCycle);
        logger.finest("Created ChargeMonitor: " + cm);
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