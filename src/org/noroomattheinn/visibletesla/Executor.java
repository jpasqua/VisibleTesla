/*
 * Executor.java - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Oct 11, 2014
 */
package org.noroomattheinn.visibletesla;

import java.util.TimerTask;
import java.util.concurrent.ArrayBlockingQueue;
import javafx.scene.control.ProgressIndicator;
import org.noroomattheinn.tesla.Tesla;

/**
 * StateProducer: Produce state updates on demand.
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public abstract class Executor<R extends Executor.Request> implements Runnable {
    
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private final   ArrayBlockingQueue<R>   queue;
    protected final AppContext              appContext;
    protected final String                  name;
                
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public Executor(AppContext ac, String name) {
        this.appContext = ac;
        this.queue = new ArrayBlockingQueue<>(20);
        this.name = name;
        appContext.tm.launch((Runnable)this, name);
    }
    
    public void produce(R r) {
        try {
            queue.put(r);
        } catch (InterruptedException ex) {
            Tesla.logger.warning(name + " interrupted adding  to queue: " + ex.getMessage());
        }
    }
        
/*------------------------------------------------------------------------------
 *
 * Methods that must or may be implemented by subclasses
 * 
 *----------------------------------------------------------------------------*/
    
    protected boolean requestSuperseded(R r) { return false; }
    
    protected abstract R newRequest(R basis, boolean allowRetry);
    
    protected abstract boolean execRequest(R r) throws Exception;

/*------------------------------------------------------------------------------
 *
 * The core itnernal implementation
 * 
 *----------------------------------------------------------------------------*/
    
    private void retry(final R r) {
        appContext.utils.addTimedTask(new TimerTask() {
            @Override public void run() { produce(newRequest(r, false)); } },
            r.retryDelay);
    }
    

    
    @Override public void run() {
        while (!appContext.shuttingDown.get()) {
            R r = null;
            try {
                r = queue.take();
                if (!requestSuperseded(r)) {
                    if (r.pi != null) { appContext.showProgress(r.pi, true); }
                    boolean success = execRequest(r);
                    if (r.pi != null) { appContext.showProgress(r.pi, false); }
                    if (!success && r.allowRetry) {
                        System.err.println("Request failed, retrying after 20 secs");
                        retry(r);
                    }
                }
            } catch (Exception e) {
                if (r != null && r.pi != null) { appContext.showProgress(r.pi, false); }
                Tesla.logger.info("Exception in " + name + ": " + e.getMessage());
                if (e instanceof InterruptedException) { return; }
            }
        }
    }
    
    public static abstract class Request {
        public final boolean    allowRetry;
        public final long       retryDelay;
        public final long       timeOfRequest;
        public final ProgressIndicator pi;

        Request(boolean allowRetry, long retryDelay, ProgressIndicator pi) {
            this.allowRetry = allowRetry;
            this.retryDelay = retryDelay;
            this.timeOfRequest = System.currentTimeMillis();
            this.pi = pi;
        }
    }
}