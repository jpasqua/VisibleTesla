/*
 * SnapshotStreamer.java - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Apr 27, 2014
 */
package org.noroomattheinn.visibletesla;

import java.util.concurrent.ArrayBlockingQueue;
import org.noroomattheinn.tesla.SnapshotState;
import org.noroomattheinn.tesla.Tesla;
import org.noroomattheinn.utils.Utils;

/**
 * SnapshotStreamer: Generate a stream of locations on demand.
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class SnapshotStreamer implements Runnable {
    
/*------------------------------------------------------------------------------
 *
 * Constants and Enums
 * 
 *----------------------------------------------------------------------------*/
    
    private static final long StreamingThreshold = 400;
    
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private final AppContext appContext;
    private SnapshotState snapshot;
    private Thread streamer = null;
    private ArrayBlockingQueue<ProduceRequest> queue = new ArrayBlockingQueue<>(20);
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public SnapshotStreamer(AppContext ac) {
        this.appContext = ac;
        this.snapshot = new SnapshotState(appContext.vehicle);
        ensureStreamer();
    }
    
    public void produce(boolean stream) {
        try {
            queue.add(new ProduceRequest(stream));
        } catch (java.lang.IllegalStateException qfe) { // Queue Full
            pollForRequest();   // Drain an element from the queue
            produce(stream);    // Wth space freed up, try again
        }
    }
    
/*------------------------------------------------------------------------------
 *
 * Internal Methods - Some declared public since they implement interfaces
 * 
 *----------------------------------------------------------------------------*/

    private void ensureStreamer() {
        if (streamer == null) {
            streamer = appContext.tm.launch(this, "SnapshotStreamer");
            if (streamer == null) return;   // We're shutting down!
            while (streamer.getState() != Thread.State.WAITING) {
                Utils.yieldFor(10);
            }
        }
    }
    
    @Override public void run() {
        long lastSnapshot = 0;
        
        try {
            while (!appContext.shuttingDown.get()) {
                ProduceRequest r;
                try {
                    r = waitForRequest();
                } catch (InterruptedException e) {
                    if (appContext.shuttingDown.get()) {
                        Tesla.logger.info("SnapshotStreamer Interrupted during normal shutdown");
                    } else {
                        Tesla.logger.info("SnapshotStreamer Interrupted unexpectedly: " + e.getMessage());
                    }
                    return;
                }

                if (r == null) break;   // The thread was interrupted.
                if (r.timeOfRequest < lastSnapshot) { continue; }

                if (!snapshot.refresh()) { continue; }
                lastSnapshot = snapshot.state.timestamp;
                appContext.lastKnownSnapshotState.set(snapshot.state);

                if (!r.stream) { continue; }

                // System.err.print("STRM: ");
                // Now, stream data as long as it comes...
                while (snapshot.refreshFromStream()) {
                    //System.err.print("|RF");
                    if (appContext.shuttingDown.get()) return;
                    if (appContext.inactivity.isSleeping()) {
                        //System.err.print("|SL");
                        break;
                    }
                    if (snapshot.state.timestamp - lastSnapshot > StreamingThreshold) {
                        //System.err.print("|GT");
                        appContext.lastKnownSnapshotState.set(snapshot.state);
                        lastSnapshot = snapshot.state.timestamp;
                        pollForRequest();   // Consume a request if any - don't block
                    } else {
                        //System.err.print("|SK");
                    }
                }
                //System.err.println("!");
            }
        } catch (Exception e) {
            Tesla.logger.severe("Uncaught exception in SnapshotStreamer: " + e.getMessage());
        }
    }
    
    private ProduceRequest waitForRequest() throws InterruptedException {
        return queue.take();
    }
    
    private ProduceRequest pollForRequest() {
        return queue.poll();
    }
    
    private static class ProduceRequest {
        public long timeOfRequest;
        public boolean stream;
        
        ProduceRequest(boolean stream) {
            timeOfRequest = System.currentTimeMillis();
            this.stream = stream;
        }
    }
}