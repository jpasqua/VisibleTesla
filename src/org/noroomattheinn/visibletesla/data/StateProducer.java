/*
 * StateProducer.java - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Aug 8, 2014
 */
package org.noroomattheinn.visibletesla.data;

import java.util.Map;
import org.noroomattheinn.tesla.BaseState;
import org.noroomattheinn.tesla.Vehicle;
import org.noroomattheinn.utils.Utils;
import org.noroomattheinn.utils.Executor;
import org.noroomattheinn.visibletesla.vehicle.VTVehicle;


/**
 * StateProducer: Produce state updates on demand.
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
class StateProducer extends Executor<StateProducer.Request> {
    
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private Map<Vehicle.StateType,Long> lastProduced = Utils.newHashMap(
        Vehicle.StateType.Charge,   0L,
        Vehicle.StateType.Drive,    0L,
        Vehicle.StateType.GUI,      0L,
        Vehicle.StateType.HVAC,     0L,
        Vehicle.StateType.Vehicle,  0L);
    private final VTVehicle vtVehicle;
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    StateProducer(VTVehicle v, FeedbackListener feedbackListener) {
        super("StateProducer",  feedbackListener);
        this.vtVehicle = v;
    }
    
    void produce(Vehicle.StateType whichState, Object progressContext) {
        super.produce(new Request(whichState, progressContext));
    }
        
/*------------------------------------------------------------------------------
 *
 * Internal Methods - Some declared protected since they implement interfaces
 * 
 *----------------------------------------------------------------------------*/
    
    @Override  protected boolean requestSuperseded(Request r) {
        return r.timeOfRequest < lastProduced.get(r.stateType);
    }
    
    @Override protected boolean execRequest(Request r) {
        final BaseState state = vtVehicle.getVehicle().query(r.stateType);
        if (state.valid) {
            lastProduced.put(r.stateType, System.currentTimeMillis());
            vtVehicle.noteUpdatedState(state);
            return true;
        }
        return false;
    }
    
    static class Request extends Executor.Request {
        Vehicle.StateType stateType;

        Request(Vehicle.StateType stateType, Object progressContext) {
            super(progressContext);
            this.stateType = stateType;
        }
        
        @Override protected String getRequestName() { return stateType.name(); }
    }
}

