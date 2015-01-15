/*
 * StreamProducer.java - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Apr 27, 2014
 */
package org.noroomattheinn.visibletesla.data;

import org.noroomattheinn.tesla.StreamState;
import org.noroomattheinn.tesla.Streamer;
import org.noroomattheinn.utils.Executor;
import org.noroomattheinn.utils.ThreadManager;
import org.noroomattheinn.visibletesla.vehicle.VTVehicle;
import static org.noroomattheinn.tesla.Tesla.logger;

/**
 * StreamProducer: Generate a stream of locations on demand.
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
class StreamProducer extends Executor<StreamProducer.Request>
                     implements ThreadManager.Stoppable {
    
/*------------------------------------------------------------------------------
 *
 * Constants and Enums
 * 
 *----------------------------------------------------------------------------*/
    
    private static final long StreamingThreshold = 400;     // 0.4 seconds

/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private final  Streamer  streamer;
    private final  VTVehicle vtVehicle;
    private        long      lastSnapshotTime = 0;
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    StreamProducer(VTVehicle v, FeedbackListener feedbackListener) {
        super("StreamProducer", feedbackListener);
        this.vtVehicle = v;
        this.streamer = v.getVehicle().getStreamer();
        ThreadManager.get().addStoppable((ThreadManager.Stoppable)this);
    }
    
    void produce(boolean stream) {
        super.produce(new Request(stream, false));
    }
    
    @Override public void stop() { if (streamer != null) { streamer.forceClose(); } }
    
/*------------------------------------------------------------------------------
 *
 * Internal Methods - Some declared protected since they implement interfaces
 * 
 *----------------------------------------------------------------------------*/

    @Override protected boolean execRequest(Request r) throws Exception {
        StreamState snapshot = r.continuation ? streamer.tryExistingStream() :
                                                streamer.beginStreamIfNeeded();
        if (snapshot == null) {
            if (r.continuation && isInMotion()) { produce(true); }
            return r.continuation;  // Null is ok on a continuation, not otherwise
        }
        
        if (!r.continuation || snapshot.timestamp - lastSnapshotTime > StreamingThreshold) {
            lastSnapshotTime = snapshot.timestamp;
            vtVehicle.noteUpdatedState(snapshot);
        }
        
        if (r.stream) super.produce(new Request(true, true));
        return true;
    }

    @Override protected Request filter(Request r) {
        Request  filtered = r;
        Request  pending = queue.peek();
        
        if (r.equals(pending) || queue.remainingCapacity() == 0) {
            filtered = null;
            logger.finest("Filtering (s: " + r.stream + ", " + r.continuation +"), rqs = " + queue.remainingCapacity());
        }

        return filtered;
    }
            
    private boolean isInMotion() {
        return vtVehicle.streamState.get().isInMotion();
    }

    @Override protected void addToHistogram(Executor.Request r) {
        if (!((Request)r).continuation) super.addToHistogram(r);
    }
    
    static class Request extends Executor.Request {
        final boolean stream;
        final boolean continuation;
        
        Request(boolean stream, boolean continuation) {
            super(null);
            this.stream = stream;
            this.continuation = continuation;
        }
        
        @Override protected String getRequestName() { return "Stream"; }
        @Override protected int maxRetries() { return 2; }
        
        @Override public boolean equals(Object o) {
            if (o == null) return false;
            if (!(o instanceof Request)) return false;
            Request r2 = (Request)o;
            return (stream == r2.stream && continuation == r2.continuation);
        }

        @Override public int hashCode() {
            return (this.stream ? 2 : 0) + (this.continuation ? 1 : 0);
        }
        
    }
}