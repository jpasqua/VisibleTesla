/*
 * ProgressListener.java - Copyright(c) 2015 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Jan 02, 2015
 */
package org.noroomattheinn.visibletesla;

import org.noroomattheinn.utils.Executor;
import java.util.HashMap;
import java.util.Map;
import javafx.application.Platform;
import javafx.scene.control.ProgressIndicator;
import org.noroomattheinn.utils.MailGun;
import org.noroomattheinn.visibletesla.prefs.Prefs;
import org.noroomattheinn.visibletesla.vehicle.VTVehicle;

import static org.noroomattheinn.tesla.Tesla.logger;
import static org.noroomattheinn.utils.Utils.timeSince;

/**
 * ProgressListener: Implement an Executor.FeedbackListener for visibletesla.
 * The progressContext for this listener is of type ProgressIndicator
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
class ProgressListener implements Executor.FeedbackListener {
    
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    /**
     * Keep track of how many 'requestStarted' calls are outstanding for a 
     * given ProgressIndicator. Each tab has it's own ProgressIndicator
     * instance so this map goes from the PI instance to the count.
     */
    private static Map<ProgressIndicator,Integer> refCount = new HashMap<>();
    
    /**
     * The last time we sent a report to the user. Only do that once a day
     */
    private long lastReport;
    
/*==============================================================================
 * -------                                                               -------
 * -------              External Interface To This Class                 ------- 
 * -------                                                               -------
 *============================================================================*/
    
    /**
     * Create a new ProgressListener. This is really a singleton. There should
     * only be one for the entire app.
     */
    ProgressListener() {
        this.lastReport = System.currentTimeMillis();
    }
    
    @Override public void requestStarted(Executor.Request r) {
        if (r.progressContext != null)
            showProgress((ProgressIndicator)(r.progressContext), true);
    }

    @Override public void requestCompleted(Executor.Request r) {
        if (r.progressContext != null)
            showProgress((ProgressIndicator)(r.progressContext), false);
    }

    @Override public void completionHistogram(String type, Map<Integer,Integer> histogram) {
        long ReportingInterval = 24 * 60 * 60 * 1000;
        String ReportAddress = "data@visibletesla.com";

        StringBuilder sb = new StringBuilder();
        sb.append(type); sb.append(" stats: ");
        for (Map.Entry<Integer,Integer> entry : histogram.entrySet()) {
            int tries = entry.getKey();
            int count = entry.getValue();
            sb.append("("); sb.append(tries); sb.append(", "); sb.append(count); sb.append(") ");
        }
        logger.info(sb.toString());
        if (Prefs.get().submitAnonFailure.get() && timeSince(lastReport) > ReportingInterval) {
            logger.info("Sending api stats report: " + sb.toString());
            MailGun.get().send(
                ReportAddress,
                "API Stats for " + VTVehicle.get().getVehicle().getUUID(),
                sb.toString());
            lastReport = System.currentTimeMillis();
        }
    }
    
    private void showProgress(final ProgressIndicator progressContext, final boolean spinning) {
        Platform.runLater(new Runnable() {
            @Override public void run() {
                if (progressContext == null) return;
                Integer count = refCount.get(progressContext);
                if (count == null) count = 0;
                count = count + (spinning ? 1 : -1);
                refCount.put(progressContext, count);
                progressContext.setVisible(count > 0);
                progressContext.setProgress(count > 0 ? -1 : 0);
            }
        });
    }

}
