/*
 * SnapshotStreamer.java - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Apr 27, 2014
 */
package org.noroomattheinn.visibletesla;

import java.util.concurrent.ArrayBlockingQueue;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import org.noroomattheinn.tesla.SnapshotState;
import org.noroomattheinn.tesla.Tesla;
import org.noroomattheinn.utils.Utils;

/**
 * SnapshotStreamer: Generate a stream of locations on demand.
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class SnapshotStreamer implements Runnable, ChangeListener<AppContext.InactivityType> {
    
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
    private AppContext.InactivityType inactivityState = AppContext.InactivityType.Awake;
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
            getRequest(false);  // Drain an element from the queue
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
            streamer = appContext.launchThread(this, "00 SnapshotStreamer");
            while (streamer.getState() != Thread.State.WAITING) {
                Utils.yieldFor(10);
            }
        }
    }
    
    @Override public void changed(
            ObservableValue<? extends AppContext.InactivityType> o,
            AppContext.InactivityType ov, AppContext.InactivityType nv) {
        inactivityState = nv;
    }

    @Override public void run() {
        long lastSnapshot = 0;
        appContext.inactivityState.addListener(this);
        inactivityState = appContext.inactivityState.get();
        
        while (!appContext.shuttingDown.get()) {
            ProduceRequest r = getRequest(true);    // Wait for a request
            if (r == null) break;   // The thread was interrupted.
            if (r.timeOfRequest < lastSnapshot) { continue; }
            
            if (!snapshot.refresh()) { continue; }
            lastSnapshot = snapshot.state.timestamp;
            appContext.lastKnownSnapshotState.set(snapshot.state);
            
            if (!r.stream) { continue; }
            
            System.err.print("STRM: ");
            // Now, stream data as long as it comes...
            while (snapshot.refreshFromStream()) {
                System.err.print("|RF");
                if (appContext.shuttingDown.get()) return;
                if (inactivityState == AppContext.InactivityType.Sleep) {
                    System.err.print("|SL");
                    break;
                }
                if (snapshot.state.timestamp - lastSnapshot > StreamingThreshold) {
                    System.err.print("|GT");
                    appContext.lastKnownSnapshotState.set(snapshot.state);
                    lastSnapshot = snapshot.state.timestamp;
                    getRequest(false);   // Consume a request if any - don't block
                } else {
                    System.err.print("|SK");
                }
            }
            System.err.println("!");
        }
    }
    
    private ProduceRequest getRequest(boolean block) {
        try {
            return block ? queue.take() : queue.poll();
        } catch (InterruptedException ex) {
            Tesla.logger.info("SnapshotStreamer Interrupted");
            return null;
        }
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