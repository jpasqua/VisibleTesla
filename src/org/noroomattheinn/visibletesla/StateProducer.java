/*
 * StateProducer.java - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Aug 8, 2014
 */
package org.noroomattheinn.visibletesla;

import java.util.Map;
import java.util.TimerTask;
import java.util.concurrent.ArrayBlockingQueue;
import javafx.application.Platform;
import javafx.scene.control.ProgressIndicator;
import org.noroomattheinn.tesla.BaseState;
import org.noroomattheinn.tesla.Tesla;
import org.noroomattheinn.tesla.Vehicle;
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
    
    private long RetryDelay = 20 * 1000;
    
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private static Thread       producer = null;
    private final  AppContext   appContext;
    private final  ArrayBlockingQueue<ProduceRequest> queue;
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public StateProducer(AppContext ac) {
        this.appContext = ac;
        this.queue = new ArrayBlockingQueue<>(20);
        ensureProducer();
    }
    
    public void produce(Vehicle.StateType whichState, ProgressIndicator pi) {
        produce(whichState, true, pi);
    }
    
    public void produce(Vehicle.StateType whichState, boolean allowRetry, ProgressIndicator pi) {
        try {
            queue.put(new ProduceRequest(whichState, allowRetry, pi));
        } catch (InterruptedException ex) {
            Tesla.logger.warning("StateProducer interrupted adding  to queue: " + ex.getMessage());
        }
    }
        
/*------------------------------------------------------------------------------
 *
 * Internal Methods - Some declared public since they implement interfaces
 * 
 *----------------------------------------------------------------------------*/
    
    private void retry(
            final Vehicle.StateType whichState,
            final ProgressIndicator pi) {
        appContext.utils.addTimedTask(new TimerTask() {
            @Override public void run() { produce(whichState, false, pi); } },
            RetryDelay);
    }
    

    private void ensureProducer() {
        if (producer == null) {
            producer = appContext.tm.launch(this, "StateProducer");
            if (producer == null) return;   // We're shutting down!
            while (producer.getState() != Thread.State.WAITING) {
                Utils.yieldFor(10);
            }
        }
    }
    
    @Override public void run() {
        Map<Vehicle.StateType,Long> lastProduced = Utils.newHashMap(
            Vehicle.StateType.Charge, 0L, Vehicle.StateType.Drive, 0L,
            Vehicle.StateType.GUI, 0L, Vehicle.StateType.HVAC, 0L,
            Vehicle.StateType.Vehicle, 0L);
        
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
                    appContext.showProgress(r.pi, true);
                    final BaseState state = appContext.vehicle.query(r.stateType);
                    if (state.valid) {
                        lastProduced.put(r.stateType, System.currentTimeMillis());
                        Platform.runLater(new Runnable() {
                            @Override public void run() { appContext.noteUpdatedState(state); }
                        });
                    } else if (r.allowRetry) {
                        //Tesla.logger.warning("Query failed, retrying after 20 secs");
                        System.err.println("Query failed, retrying after 20 secs");
                        retry(r.stateType, r.pi);
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
        public Vehicle.StateType stateType;
        public ProgressIndicator pi;
        public boolean allowRetry;
        
        ProduceRequest(Vehicle.StateType stateType, boolean allowRetry, ProgressIndicator pi) {
            timeOfRequest = System.currentTimeMillis();
            this.stateType = stateType;
            this.pi = pi;
            this.allowRetry = allowRetry;
        }
        
        ProduceRequest(Vehicle.StateType stateType, ProgressIndicator pi) {
            this(stateType, false, pi);
        }
    }
}