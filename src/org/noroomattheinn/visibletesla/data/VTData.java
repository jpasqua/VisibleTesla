/*
 * VTData - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Dec 31, 2014
 */
package org.noroomattheinn.visibletesla.data;

import java.io.FileNotFoundException;
import java.io.IOException;
import org.noroomattheinn.tesla.Vehicle;
import org.noroomattheinn.visibletesla.cycles.ChargeCycle;
import org.noroomattheinn.visibletesla.cycles.ChargeMonitor;
import org.noroomattheinn.visibletesla.cycles.ChargeStore;
import org.noroomattheinn.visibletesla.cycles.RestCycle;
import org.noroomattheinn.visibletesla.cycles.RestMonitor;
import org.noroomattheinn.visibletesla.cycles.RestStore;
import org.noroomattheinn.visibletesla.fxextensions.TrackedObject;


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
    
    public StatsCollector statsCollector;
    public StreamProducer streamProducer;
    public StateProducer stateProducer;
    public StatsStreamer statsStreamer;
    private ChargeMonitor chargeMonitor;
    private RestMonitor restMonitor;

/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public ChargeStore chargeStore;
    public RestStore restStore;
    public final TrackedObject<ChargeCycle> lastChargeCycle;
    public final TrackedObject<RestCycle> lastRestCycle;

    public static VTData create() {
        if (instance != null) return instance;
        return (instance = new VTData());
    }
    
    public static VTData get() { return instance; }
    
    public void setVehicle(Vehicle v) throws IOException {
        statsCollector = new StatsCollector();
        streamProducer = new StreamProducer();
        stateProducer = new StateProducer();
        statsStreamer = new StatsStreamer();
        initChargeStore();
        initRestStore();
    }

    private VTData() {
        lastChargeCycle = new TrackedObject<>(null);
        lastRestCycle = new TrackedObject<>(null);
    }
    
/*------------------------------------------------------------------------------
 *
 * Private Utility Methods
 * 
 *----------------------------------------------------------------------------*/

    private void initRestStore() throws FileNotFoundException {
        boolean needsInitialLoad = RestStore.requiresInitialLoad();
        restStore = new RestStore();
        restMonitor = new RestMonitor();
        if (needsInitialLoad) { restStore.doIntialLoad(); }
    }
    
    private void initChargeStore() throws FileNotFoundException {
        chargeStore = new ChargeStore();
        chargeMonitor = new ChargeMonitor();
    }
    
}