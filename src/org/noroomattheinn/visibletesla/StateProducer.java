/*
 * StateProducer.java - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Aug 8, 2014
 */
package org.noroomattheinn.visibletesla;

import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import javafx.application.Platform;
import javafx.scene.control.ProgressIndicator;
import org.noroomattheinn.tesla.APICall;
import org.noroomattheinn.tesla.ChargeState;
import org.noroomattheinn.tesla.DrivingState;
import org.noroomattheinn.tesla.GUIState;
import org.noroomattheinn.tesla.HVACState;
import org.noroomattheinn.tesla.Tesla;
import org.noroomattheinn.tesla.VehicleState;
import org.noroomattheinn.utils.Utils;

/**
 * StateProducer: Produce state updates on demand.
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class StateProducer implements Runnable {
    
/*------------------------------------------------------------------------------
 *
 * Constants and Enums
 * 
 *----------------------------------------------------------------------------*/
    
    public static enum StateType {Charge, Driving, GUI, HVAC, Vehicle};
    
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private final AppContext appContext;
    private Thread producer = null;
    private final ArrayBlockingQueue<ProduceRequest> queue = new ArrayBlockingQueue<>(20);
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public StateProducer(AppContext ac) {
        this.appContext = ac;
        ensureProducer();
    }
    
    public void produce(StateType whichState, ProgressIndicator pi) {
        try {
            queue.put(new ProduceRequest(whichState, pi));
        } catch (InterruptedException ex) {
            Tesla.logger.warning("Interrupted while adding request to queue: " + ex.getMessage());
        }
    }
    
/*------------------------------------------------------------------------------
 *
 * Internal Methods - Some declared public since they implement interfaces
 * 
 *----------------------------------------------------------------------------*/

    private void ensureProducer() {
        if (producer == null) {
            producer = appContext.tm.launch(this, "00 StateProducer");
            if (producer == null) return;   // We're shutting down!
            while (producer.getState() != Thread.State.WAITING) {
                Utils.yieldFor(10);
            }
        }
    }
    
    @Override public void run() {
        Map<StateType,Long> lastProduced = Utils.newHashMap(
            StateType.Charge, 0L, StateType.Driving, 0L, StateType.GUI, 0L,
            StateType.HVAC, 0L, StateType.Vehicle, 0L);
        Map<StateType,APICall> states = Utils.newHashMap(
            StateType.Charge, new ChargeState(appContext.vehicle),
            StateType.Driving, new DrivingState(appContext.vehicle),
            StateType.GUI, new GUIState(appContext.vehicle),
            StateType.HVAC, new HVACState(appContext.vehicle),
            StateType.Vehicle, new VehicleState(appContext.vehicle));
        
        ProduceRequest r = null;    // Initialize for the finally clause
        try {
            while (!appContext.shuttingDown.get()) {
                r = null;   // Reset to null every time around the loop
                try {
                    r = queue.take();
                } catch (InterruptedException e) {
                    Tesla.logger.info("StateProducer Interrupted: " + e.getMessage());
                    return;
                }

                if (r.timeOfRequest > lastProduced.get(r.stateType)) {
                    final APICall a = states.get(r.stateType);
                    appContext.showProgress(r.pi, true);
                    if (a.refresh()) {
                        lastProduced.put(r.stateType, System.currentTimeMillis());
                        Platform.runLater(new Runnable() {
                            @Override public void run() { appContext.noteUpdatedState(a); }
                        });
                    }
                    appContext.showProgress(r.pi, false);
                }
            }
        } catch (Exception e) {
            Tesla.logger.severe("Uncaught exception in StateProducer: " + e.getMessage());
        } finally {
            if (r != null) { appContext.showProgress(r.pi, false); }
        }
    }
    
    private static class ProduceRequest {
        public long timeOfRequest;
        public StateType stateType;
        public ProgressIndicator pi;
        
        ProduceRequest(StateType stateType, ProgressIndicator pi) {
            timeOfRequest = System.currentTimeMillis();
            this.stateType = stateType;
            this.pi = pi;
        }
    }
}