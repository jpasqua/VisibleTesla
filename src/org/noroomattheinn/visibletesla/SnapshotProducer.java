/*
 * SnapshotProducer.java - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Apr 27, 2014
 */
package org.noroomattheinn.visibletesla;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ArrayBlockingQueue;
import org.noroomattheinn.tesla.SnapshotState;
import org.noroomattheinn.tesla.Tesla;
import org.noroomattheinn.utils.Utils;
import org.noroomattheinn.visibletesla.ThreadManager.Stoppable;

/**
 * SnapshotProducer: Generate a stream of locations on demand.
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class SnapshotProducer implements Runnable, Stoppable {
    
/*------------------------------------------------------------------------------
 *
 * Constants and Enums
 * 
 *----------------------------------------------------------------------------*/
    
    private static final long StreamingThreshold = 400;
    private long RetryDelay = 20 * 1000;

    /*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private final AppContext appContext;
    private SnapshotState snapshot;
    private Thread producer = null;
    private ArrayBlockingQueue<ProduceRequest> queue = new ArrayBlockingQueue<>(20);
    private Timer timer = new Timer();
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public SnapshotProducer(AppContext ac) {
        this.appContext = ac;
        this.snapshot = new SnapshotState(appContext.vehicle);
        ensureProducer();
        ac.tm.addStoppable((Stoppable)this);
    }
    
    public void produce(boolean stream) { produce(stream, true); }
    
    public void produce(boolean stream, boolean allowRetry) {
        try {
            queue.add(new ProduceRequest(stream));
        } catch (java.lang.IllegalStateException qfe) { // Queue Full
            pollForRequest();   // Drain an element from the queue
            produce(stream, allowRetry);    // Wth space freed up, try again
        }
    }
    
    public void produceLater(final boolean stream) { produceLater(stream, false);  }
    
    public void produceLater(final boolean stream, final boolean allowRetry) {
        timer.schedule(new TimerTask() {
            @Override public void run() { produce(stream, allowRetry); } },
            RetryDelay);
    }
    
    @Override public void stop() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }
    
/*------------------------------------------------------------------------------
 *
 * Internal Methods - Some declared public since they implement interfaces
 * 
 *----------------------------------------------------------------------------*/

    private void ensureProducer() {
        if (producer == null) {
            producer = appContext.tm.launch(this, "SnapshotProducer");
            if (producer == null) return;   // We're shutting down!
            while (producer.getState() != Thread.State.WAITING) {
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
                        Tesla.logger.info("SnapshotProducer Interrupted during normal shutdown");
                    } else {
                        Tesla.logger.info("SnapshotProducer Interrupted unexpectedly: " + e.getMessage());
                    }
                    return;
                }

                if (r == null) break;   // The thread was interrupted.
                if (r.timeOfRequest < lastSnapshot) { continue; }

                if (!snapshot.refresh()) {
                    if (r.allowRetry()) {
                        Tesla.logger.warning("Refresh failed, retrying after 20 secs");
                        produceLater(r.stream, false);
                    }
                    continue;
                }
                
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
            Tesla.logger.severe("Uncaught exception in SnapshotProducer: " + e.getMessage());
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
        private boolean allowRetry;
        
        ProduceRequest(boolean stream, boolean allowRetry) {
            timeOfRequest = System.currentTimeMillis();
            this.stream = stream;
            this.allowRetry = allowRetry;
        }
        
        ProduceRequest(boolean stream) { this(stream, false); }
        
        boolean allowRetry() { return allowRetry; }
    }
}