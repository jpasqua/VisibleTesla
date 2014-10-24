/*
 * StreamProducer.java - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Apr 27, 2014
 */
package org.noroomattheinn.visibletesla;

import org.noroomattheinn.tesla.StreamState;
import org.noroomattheinn.tesla.Streamer;

/**
 * StreamProducer: Generate a stream of locations on demand.
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class StreamProducer extends Executor<StreamProducer.Request> {
    
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
    
    public StreamProducer(AppContext ac) {
        super(ac, "SnapshotProducer");
        this.streamer = appContext.vehicle.getStreamer();
    }
    
    public void produce(boolean stream) {
        super.produce(new Request(stream, false));
    }
    
/*------------------------------------------------------------------------------
 *
 * Internal Methods - Some declared protected since they implement interfaces
 * 
 *----------------------------------------------------------------------------*/

    @Override protected boolean execRequest(Request r) throws Exception {
        StreamState snapshot = r.continuation ? streamer.tryExistingStream() :
                                                streamer.beginStreamIfNeeded();
        if (snapshot == null) { return r.continuation; }    // Null is OK on continuation
        
        if (!r.continuation || snapshot.timestamp - lastSnapshotTime > StreamingThreshold) {
            lastSnapshotTime = snapshot.timestamp;
            appContext.lastKnownStreamState.set(snapshot);
        }
        
        if (r.stream && !appContext.inactivity.isSleeping()) {  // Keep streaming
            super.produce(new Request(r.stream, true));
        }
        return true;
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
    }
}