/*
 * ProgressListener.java - Copyright(c) 2015 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Jan 02, 2015
 */
package org.noroomattheinn.visibletesla;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.prefs.Preferences;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.scene.control.ProgressIndicator;
import org.noroomattheinn.utils.Executor;
import org.noroomattheinn.utils.MailGun;

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
    
    private static final String LastReportKey = "APP_LastReportKey";
    private static final long ReportingInterval = 24 * 60 * 60 * 1000;
    private static final String ReportAddress = "data@visibletesla.com";

    /**
     * Keep track of how many 'requestStarted' calls are outstanding for a 
     * given ProgressIndicator. Each tab has it's own ProgressIndicator
     * instance so this map goes from the PI instance to the count.
     */
    private static Map<ProgressIndicator,Integer> refCount = new HashMap<>();
    
    private final BooleanProperty submitStats;
    private final String submissionID;
    private final Preferences persistentStore;
            
/*==============================================================================
 * -------                                                               -------
 * -------              External Interface To This Class                 ------- 
 * -------                                                               -------
 *============================================================================*/
    
    /**
     * Create a new ProgressListener. This is really a singleton. There should
     * only be one for the entire app.
     */
    ProgressListener(BooleanProperty submitStats, String submissionID) {
        persistentStore = Preferences.userNodeForPackage(this.getClass());
        this.submitStats = submitStats;
        this.submissionID = submissionID;
    }
    
    @Override public void requestStarted(Executor.Request r) {
        if (r.progressContext != null)
            showProgress((ProgressIndicator)(r.progressContext), true);
    }

    @Override public void requestCompleted(Executor.Request r) {
        if (r.progressContext != null)
            showProgress((ProgressIndicator)(r.progressContext), false);
    }

    @Override public void completionHistogram(String type, Map<Integer, Integer> histogram) {
        StringBuilder sb = new StringBuilder();
        // Format of the submission is JSON:
        // {"uuid":"XYZ123","type":"Charge","stats":[[-3,1],[0,1000],[1,4],[2,1]],
        //  "time":"Thu Mar 05 19:51:58 PST 2015"}
        sb.append("{");
        sb.append("\"type\":\""); sb.append(type); sb.append("\",");
        sb.append("\"stats\":[");
        boolean first = true;
        for (Map.Entry<Integer, Integer> entry : histogram.entrySet()) {
            int tries = entry.getKey();
            int count = entry.getValue();
            if (!first) { sb.append(","); }
            sb.append("["); sb.append(tries); sb.append(","); sb.append(count); sb.append("]");
            first = false;
        }
        sb.append("],");
        sb.append("\"date\":\""); sb.append(new Date()); sb.append("\",");
        sb.append("\"uuid\":\""); sb.append(submissionID); sb.append("\"");
        sb.append("}");
        
        logger.info(sb.toString());

        long lastReport = persistentStore.getLong(LastReportKey, 0);
        if (submitStats.get()) {
            if (timeSince(lastReport) >= ReportingInterval) {
                logger.info("Sending api stats report: " + sb.toString());
                MailGun.get().send(
                        ReportAddress,                      // To
                        "API Stats for " + submissionID,    // Subject
                        sb.toString());                     // Body
                persistentStore.putLong(LastReportKey, System.currentTimeMillis());
            }
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
