/*
 * SnapshotProducer.java - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Apr 27, 2014
 */
package org.noroomattheinn.visibletesla;

import java.util.TimerTask;
import java.util.concurrent.ArrayBlockingQueue;
import org.noroomattheinn.tesla.StreamState;
import org.noroomattheinn.tesla.Streamer;
import org.noroomattheinn.tesla.Tesla;
import org.noroomattheinn.utils.Utils;

/**
 * SnapshotProducer: Generate a stream of locations on demand.
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class SnapshotProducer implements Runnable {
    
/*------------------------------------------------------------------------------
 *
 * Constants and Enums
 * 
 *----------------------------------------------------------------------------*/
    
    private static final long StreamingThreshold = 400;     // 0.4 seconds
    private static final long RetryDelay = 20 * 1000;       // 20 Seconds

/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private static Thread       producer = null;
    private final  AppContext   appContext;
    private final  Streamer     streamer;
    private final  ArrayBlockingQueue<ProduceRequest> queue;
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public SnapshotProducer(AppContext ac) {
        this.appContext = ac;
        this.queue = new ArrayBlockingQueue<>(20);
        this.streamer = appContext.vehicle.getStreamer();
        ensureProducer();
    }
    
    public void produce(boolean stream) { produce(stream, true); }
    
    public void produce(boolean stream, boolean allowRetry) {
        try {
            queue.put(new ProduceRequest(stream));
        } catch (InterruptedException ex) {
            Tesla.logger.warning("SnapshotProducer interrupted adding to queue: " + ex.getMessage());
        }
    }
    
/*------------------------------------------------------------------------------
 *
 * Internal Methods - Some declared public since they implement interfaces
 * 
 *----------------------------------------------------------------------------*/
        
    private void retry(final boolean stream) {
        appContext.utils.addTimedTask(new TimerTask() {
            @Override public void run() { produce(stream, false); } }, RetryDelay);
    }
    
    private synchronized void ensureProducer() {
        if (producer == null) {
            producer = appContext.tm.launch(this, "SnapshotProducer");
            if (producer == null) return;   // We're shutting down!
            while (producer.getState() != Thread.State.WAITING) {
                Utils.yieldFor(10);
            }
        }
    }
    
    @Override public void run() {
        StreamState snapshot;
        long lastSnapshotTime = 0;
        
        try {
            while (!appContext.shuttingDown.get()) {
                ProduceRequest r;
                try {
                    r = queue.take();   // Wait for a request
                } catch (InterruptedException e) {
                    if (appContext.shuttingDown.get()) {
                        Tesla.logger.info("SnapshotProducer Interrupted during normal shutdown");
                    } else {
                        Tesla.logger.info("SnapshotProducer Interrupted unexpectedly: " + e.getMessage());
                    }
                    return;
                }

                if (r == null) break;   // The thread was interrupted.
                if (r.timeOfRequest < lastSnapshotTime) { continue; }

                if ((snapshot = streamer.beginNewStream()) == null) {
                    if (r.allowRetry) {
                        Tesla.logger.warning("Snapshot failed, retrying after 20 secs");
                        retry(r.stream);
                    }
                    continue;
                }
                
                lastSnapshotTime = snapshot.timestamp;
                appContext.lastKnownStreamState.set(snapshot);

                if (!r.stream) { continue; }

                //System.err.print("STRM: ");
                // Now, stream data as long as it comes...
                while ((snapshot = streamer.tryExistingStream()) != null) {
                    //System.err.print("|RF");
                    if (appContext.shuttingDown.get()) return;
                    if (appContext.inactivity.isSleeping()) {
                        //System.err.print("|SL");
                        break;
                    }
                    if (snapshot.timestamp - lastSnapshotTime > StreamingThreshold) {
                        //System.err.print("|GT");
                        appContext.lastKnownStreamState.set(snapshot);
                        lastSnapshotTime = snapshot.timestamp;
                        queue.poll();   // Drain a request if there is one
                    } else {
                        //System.err.print("|SK");
                    }
                }
                //System.err.println("!");
            }
        } catch (Exception e) {
            Tesla.logger.severe("Uncaught exception in SnapshotProducer: " + e.getMessage());
        }
    }
    
    private static class ProduceRequest {
        public long timeOfRequest;
        public boolean stream;
        public boolean allowRetry;
        
        ProduceRequest(boolean stream, boolean allowRetry) {
            timeOfRequest = System.currentTimeMillis();
            this.stream = stream;
            this.allowRetry = allowRetry;
        }
        
        ProduceRequest(boolean stream) { this(stream, false); }
    }
}