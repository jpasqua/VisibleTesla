/*
 * Executor.java - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Oct 11, 2014
 */
package org.noroomattheinn.visibletesla;

import java.util.HashMap;
import java.util.Map;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.concurrent.ArrayBlockingQueue;
import javafx.application.Platform;
import javafx.scene.control.ProgressIndicator;
import static org.noroomattheinn.tesla.Tesla.logger;
import static org.noroomattheinn.utils.Utils.timeSince;

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
    
    protected final ArrayBlockingQueue<R>   queue;
    protected final AppContext              ac;
    protected final String                  name;
    protected final TreeMap<Integer,Integer> histogram;
    protected       int                     nRequestsExecuted;
    private         long                    lastReport;
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public Executor(String name) {
        this.ac = AppContext.get();
        this.queue = new ArrayBlockingQueue<>(20);
        this.name = name;
        this.histogram = new TreeMap<>();
        this.nRequestsExecuted = 0;
        this.lastReport = System.currentTimeMillis();
        ThreadManager.get().launch((Runnable)this, name);
    }
    
    public synchronized void produce(R r) {
        try {
            R filtered = filter(r);
            if (filtered != null) queue.put(filtered);
        } catch (InterruptedException ex) {
            logger.warning(name + " interrupted adding  to queue: " + ex.getMessage());
        }
    }
    
    public Map<Integer,Integer> getHistogram() { return histogram; }
        
/*------------------------------------------------------------------------------
 *
 * Methods that must or may be implemented by subclasses
 * 
 *----------------------------------------------------------------------------*/
    
    protected boolean requestSuperseded(R r) { return false; }
    
    protected R filter(R r) { return r; }
    
    protected abstract boolean execRequest(R r) throws Exception;

/*------------------------------------------------------------------------------
 *
 * The core itnernal implementation
 * 
 *----------------------------------------------------------------------------*/
    
    private void retry(final R r) {
        ThreadManager.get().addTimedTask(new TimerTask() {
            @Override public void run() { produce(r); } },
            r.retryDelay());
    }
    

    
    @Override public void run() {
        while (!ThreadManager.get().shuttingDown()) {
            R r = null;
            try {
                r = queue.take();
                if (requestSuperseded(r)) continue;
                if (r.pi != null) { showProgress(r.pi, true); }
                boolean success = execRequest(r);
                if (r.pi != null) { showProgress(r.pi, false); }
                if (!success) {
                    if (ThreadManager.get().shuttingDown()) return;
                    if (r.moreRetries()) {
                        logger.finest(r.getRequestName() + ": failed, retrying...");
                        retry(r);
                    }
                    else {
                        addToHistogram(r);
                        logger.finest(
                                r.getRequestName() + ": failed, giving up after " +
                                r.maxRetries() + " attempt(s)");
                    }
                } else {
                    addToHistogram(r);
                    logger.finest(
                            r.getRequestName() + ": Succeeded after " +
                            r.retriesPerformed()+ " attempt(s)");
                }
            } catch (Exception e) {
                if (r != null && r.pi != null) { showProgress(r.pi, false); }
                logger.warning("Exception in " + name + ": " + e.getMessage());
                if (e instanceof InterruptedException) { return; }
            }
        }
    }
    
    private Map<ProgressIndicator,Integer> refCount = new HashMap<>();
    public void showProgress(final ProgressIndicator pi, final boolean spinning) {
        Platform.runLater(new Runnable() {
            @Override public void run() {
                if (pi == null) return;
                Integer count = refCount.get(pi);
                if (count == null) count = 0;
                count = count + (spinning ? 1 : -1);
                refCount.put(pi, count);
                pi.setVisible(count > 0);
                pi.setProgress(count > 0 ? -1 : 0);
            }
        });
    }
    
    public static abstract class Request {
        public final long       timeOfRequest;
        public final ProgressIndicator pi;
        private int             nRetries;

        Request(ProgressIndicator pi) {
            this.timeOfRequest = System.currentTimeMillis();
            this.pi = pi;
            this.nRetries = 0;
        }
        
        int retriesPerformed() { return nRetries; }
        protected boolean moreRetries() { return nRetries++ < maxRetries(); }
        protected int maxRetries() { return 2; }
        protected long retryDelay() { return 5 * 1000; }
        protected String getRequestName() { return "Unknown"; }
    }
    
    protected void addToHistogram(Request r) {
        nRequestsExecuted++;
        int tries = r.retriesPerformed();
        if (tries > r.maxRetries()) tries = -tries;
        Integer count = histogram.get(tries);
        if (count == null) count = new Integer(0);
        histogram.put(tries, count+1);
        if (nRequestsExecuted % 10 == 0) { dumpHistogram(r); }
    }
    
    private void dumpHistogram(Request r) {
        long ReportingInterval = 24 * 60 * 60 * 1000;
        String ReportAddress = "data@visibletesla.com";

        StringBuilder sb = new StringBuilder();
        sb.append(r.getRequestName()); sb.append(" stats: ");
        for (Map.Entry<Integer,Integer> entry : histogram.entrySet()) {
            int tries = entry.getKey();
            int count = entry.getValue();
            sb.append("("); sb.append(tries); sb.append(", "); sb.append(count); sb.append(") ");
        }
        logger.info(sb.toString());
        if (Prefs.get().submitAnonFailure.get() && timeSince(lastReport) > ReportingInterval) {
            logger.info("Sending api stats report: " + sb.toString());
            ac.mailer.send(
                ReportAddress,
                "API Stats for " + ac.vehicle.getUUID(),
                sb.toString());
            lastReport = System.currentTimeMillis();
        }
    }
}