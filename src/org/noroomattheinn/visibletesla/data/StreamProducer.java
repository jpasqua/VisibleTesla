/*
 * StreamProducer.java - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Apr 27, 2014
 */
package org.noroomattheinn.visibletesla.data;

import org.noroomattheinn.tesla.StreamState;
import org.noroomattheinn.tesla.Streamer;
import org.noroomattheinn.visibletesla.Executor;
import org.noroomattheinn.visibletesla.ThreadManager;
import org.noroomattheinn.visibletesla.VTVehicle;
import static org.noroomattheinn.tesla.Tesla.logger;

/**
 * StreamProducer: Generate a stream of locations on demand.
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class StreamProducer extends Executor<StreamProducer.Request>
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
    
    private final  Streamer streamer;
    private        long     lastSnapshotTime = 0;
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public StreamProducer() {
        super("StreamProducer");
        this.streamer = VTVehicle.get().getVehicle().getStreamer();
        ThreadManager.get().addStoppable((ThreadManager.Stoppable)this);
    }
    
    public void produce(boolean stream) {
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
            VTVehicle.get().streamState.set(snapshot);
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
        return VTVehicle.get().streamState.get().isInMotion();
    }

    @Override protected void addToHistogram(Executor.Request r) {
        if (!((Request)r).continuation) super.addToHistogram(r);
    }
    
    public static class Request extends Executor.Request {
        public final boolean stream;
        public final boolean continuation;
        
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