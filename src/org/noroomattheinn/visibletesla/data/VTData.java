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
import javafx.scene.control.ProgressIndicator;
import org.noroomattheinn.tesla.Vehicle;
import org.noroomattheinn.utils.Executor.FeedbackListener;
import org.noroomattheinn.utils.TrackedObject;
import org.noroomattheinn.utils.Utils.Predicate;


/**
 * VTData: Data collection and storage for VisibleTesla
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class VTData {
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
   
    private static VTData instance = null;
    
    private final File              container;
    private final FeedbackListener  feedbackListener;
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
    public       StatsCollector                 statsCollector;

    public static VTData create(File container, FeedbackListener feedbackListener) {
        if (instance != null) return instance;
        return (instance = new VTData(container, feedbackListener));
    }
    
    public static VTData get() { return instance; }
    
/*------------------------------------------------------------------------------
 *
 * Set parameters of the VTData object
 * 
 *----------------------------------------------------------------------------*/
    
    public void setVehicle(Vehicle v) throws IOException {
        statsCollector = new StatsCollector(container);
        streamProducer = new StreamProducer(feedbackListener);
        stateProducer = new StateProducer(feedbackListener);
        statsStreamer = new StatsStreamer();
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
    
/*------------------------------------------------------------------------------
 *
 * Hide the constructor
 * 
 *----------------------------------------------------------------------------*/
    
    private VTData(File container, FeedbackListener feedbackListener) {
        this.container = container;
        this.lastChargeCycle = new TrackedObject<>(null);
        this.lastRestCycle = new TrackedObject<>(null);
        this.feedbackListener = feedbackListener;
    }
    
/*------------------------------------------------------------------------------
 *
 * Private Utility Methods
 * 
 *----------------------------------------------------------------------------*/

    private void initRestStore() throws FileNotFoundException {
        boolean needsInitialLoad = RestStore.requiresInitialLoad(container);
        restStore = new RestStore(container);
        RestMonitor r = new RestMonitor();
        if (needsInitialLoad) { restStore.doIntialLoad(); }
    }
    
    private void initChargeStore() throws FileNotFoundException {
        chargeStore = new ChargeStore(container);
        ChargeMonitor c = new ChargeMonitor();
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