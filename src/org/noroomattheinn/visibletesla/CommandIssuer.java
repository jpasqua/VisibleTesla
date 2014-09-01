/*
 * CommandIssuer.java - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Aug 8, 2014
 */
package org.noroomattheinn.visibletesla;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import javafx.scene.control.ProgressIndicator;
import org.noroomattheinn.tesla.Result;
import org.noroomattheinn.tesla.Tesla;
import org.noroomattheinn.utils.Utils;

/**
 * CommandIssuer: Execute commands in the background.
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class CommandIssuer implements Runnable {
    
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private final AppContext appContext;
    private Thread issuer = null;
    private final ArrayBlockingQueue<Request> queue = new ArrayBlockingQueue<>(20);
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public CommandIssuer(AppContext ac) {
        this.appContext = ac;
        ensureIssuer();
    }
    
    public void issueCommand(Callable<Result> request, ProgressIndicator pi) {
        try {
            queue.put(new Request(request, pi));
        } catch (InterruptedException ex) {
            Tesla.logger.warning("Interrupted while adding request to queue: " + ex.getMessage());
        }
    }
    
/*------------------------------------------------------------------------------
 *
 * Internal Methods - Some declared public since they implement interfaces
 * 
 *----------------------------------------------------------------------------*/

    private void ensureIssuer() {
        if (issuer == null) {
            issuer = appContext.tm.launch(this, "CommandIssuer");
            while (issuer.getState() != Thread.State.WAITING) {
                Utils.yieldFor(10);
            }
        }
    }
    
    @Override public void run() {
        Request r = null;   // Initialize for the finally clause
        try {
            while (!appContext.shuttingDown.get()) {
                r = null;   // Reset to null every time around the loop
                try {
                    r = queue.take();
                    appContext.showProgress(r.pi, true);
                    Result result = r.request.call();
                    appContext.showProgress(r.pi, false);
                    if (!result.success)
                        Tesla.logger.warning("Failed command (" + r.request + "): " + result.explanation);
                } catch (InterruptedException e) {
                    Tesla.logger.info("CommandIssuer Interrupted: " + e.getMessage());
                    return;
                }
            }
        } catch (Exception e) {
            Tesla.logger.severe("Uncaught exception in CommandIssuer: " + e.getMessage());
        } finally {
            if (r != null) { appContext.showProgress(r.pi, false); }
        }
    }
    
    private static class Request {
        public Callable<Result> request;
        public ProgressIndicator pi;
        
        Request(Callable<Result> request, ProgressIndicator pi) {
            this.request = request;
            this.pi = pi;
        }
    }
}