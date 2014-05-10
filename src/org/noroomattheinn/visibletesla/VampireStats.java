/*
 * VampireStats.java - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Apr 05, 2014
 */
package org.noroomattheinn.visibletesla;

import com.google.common.collect.Range;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import org.noroomattheinn.tesla.Tesla;
import org.noroomattheinn.utils.Utils;
import org.noroomattheinn.visibletesla.dialogs.DateRangeDialog;
import org.noroomattheinn.visibletesla.dialogs.DialogUtils;
import org.noroomattheinn.visibletesla.stats.Stat;

/**
 * VampireStats: Collect and display statistics about vampire loss.
 * 
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class VampireStats {
    
/*------------------------------------------------------------------------------
 *
 * Constants and Enums
 * 
 *----------------------------------------------------------------------------*/

    private static final long MIN_REST_PERIOD = 60 * 60 * 1000; // 60 Minutes
    
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private AppContext appContext;
    private Rest restInProgress = null;
    private boolean useMiles;
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public VampireStats(AppContext ac) {
        this.appContext = ac;
    }
    
    public void showStats() {
        useMiles = appContext.lastKnownGUIState.get().distanceUnits.startsWith("mi");
        Range<Long> exportPeriod = getExportPeriod();
        if (exportPeriod == null)
            return;
        
        ArrayList<Rest> restPeriods = new ArrayList<>();

        // Process stats for the selected time period
        NavigableMap<Long, Map<String, Double>> allRows = appContext.statsStore.getData();
        NavigableMap<Long, Map<String, Double>> subMap = allRows.subMap(
                exportPeriod.lowerEndpoint(), true, exportPeriod.upperEndpoint(), true);
        for (Map.Entry<Long,Map<String,Double>> row : subMap.entrySet()) {
            handleStat(restPeriods, row.getKey(), row.getValue());
        }
        if (restInProgress != null &&
            restInProgress.endTime - restInProgress.startTime > MIN_REST_PERIOD) {
            addPeriod(restPeriods, restInProgress);
        }
        restInProgress = null;
        
        
        // Export results
        long totalRestTime = 0;
        double totalLoss = 0;
        String units = useMiles ? "mi" : "km";
        StringBuilder sb = new StringBuilder();
        for (Rest r : restPeriods) {
            sb.append("\t-------------------\n");
            sb.append(String.format(
                    "\tPeriod: [%1$tm/%1$td %1$tH:%1$tM, %2$tm/%2$td %2$tH:%2$tM]" +
                    ", %3$3.2f hours\n",
                    new Date(r.startTime), new Date(r.endTime), hours(r.endTime-r.startTime)));
            sb.append(String.format("\tRange: [%3.2f, %3.2f], %3.2f %s\n",
                    r.startRange, r.endRange, r.startRange - r.endRange, units));
            sb.append(String.format("\tAverage loss per hour: %3.2f %s/hr\n", r.avgLoss(), units));
            totalRestTime += r.endTime - r.startTime;
            totalLoss += r.startRange - r.endRange;
        }
        sb.append("===================\n");
        sb.append(String.format("Total Hours Resting: %3.2f\n", hours(totalRestTime)));
        sb.append(String.format("Total Loss: %3.2f %s\n", totalLoss, units));
        sb.append(String.format("Total Average Loss / Hour: %3.2f %s\n", totalLoss/hours(totalRestTime), units));
        
        displayResults(restPeriods, totalLoss/hours(totalRestTime), sb.toString());
    }
    
/*------------------------------------------------------------------------------
 *
 * PRIVATE - Utility Methods
 * 
 *----------------------------------------------------------------------------*/
    
    private double hours(long millis) {return ((double)(millis))/(60 * 60 * 1000); }
    
    private void addPeriod(List<Rest> periods, Rest r) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(r.startTime);
        int startDay = c.get(Calendar.DAY_OF_MONTH);
        c.setTimeInMillis(r.endTime);
        int endDay = c.get(Calendar.DAY_OF_MONTH);
        if (startDay != endDay) {   // Split into multiple periods
            setToEndOfDay(c, r.startTime);
            double ratio = ((double)(c.getTimeInMillis()-r.startTime))/
                           ((double)(r.endTime - r.startTime));
            double newEndRange = r.startRange - ((r.startRange - r.endRange) * ratio);
            Rest firstPeriod = new Rest(
                    r.startTime, c.getTimeInMillis(), r.startRange, newEndRange);
            periods.add(firstPeriod);
            
            c.add(Calendar.SECOND, 1);
            Rest remainder = new Rest(c.getTimeInMillis(), r.endTime, newEndRange, r.endRange);
            addPeriod(periods, remainder);
        } else {
            periods.add(r);
        }
    }
    
    private void setToEndOfDay(Calendar c, long timeDuringDay) {
        c.setTimeInMillis(timeDuringDay);
        c.set(Calendar.HOUR_OF_DAY, 23);
        c.set(Calendar.MINUTE, 59);
        c.set(Calendar.SECOND, 59);
    }
    
    private Map<String,Stat.Sample> curVals = new HashMap<>();
    private Double updateVal(String key, long timestamp, Map<String, Double> stat) {
        Double val = stat.get(key);
        if (val == null) {  // Return the last known value if there is one
            Stat.Sample sample = curVals.get(key);
            return sample == null ? null : sample.value;
        } else {            // Update the stored value and return it
            curVals.put(key, new Stat.Sample(timestamp, val));
            return val;
        }
    }
    
    private void handleStat(List<Rest> restPeriods, long timestamp, Map<String, Double> stat) {      
        Double speed = updateVal(StatsStore.SpeedKey, timestamp, stat);
        Double range = updateVal(StatsStore.EstRangeKey, timestamp, stat);
        Double voltage = updateVal(StatsStore.VoltageKey, timestamp, stat);
        if (voltage == null) voltage = 0.0;
        if (speed == null || range == null) { return; }

        // Use 100V as a cutoff to ignore spurious spurious readings
        if (speed == 0 && voltage < 100) {   
            if (!useMiles) range = Utils.mToK(range);
            if (restInProgress == null) {
                restInProgress = new Rest(timestamp, timestamp, range, range);
            } else {
                restInProgress.endTime = timestamp;
                restInProgress.endRange = range;
            }
        } else {    // End the current rest period if there is one
            if (restInProgress != null) {   // Rest is over!
                if (restInProgress.endTime - restInProgress.startTime > MIN_REST_PERIOD) {
                    // OK, there's another odd situation to handle. If we start
                    // a rest period and then stop getting data, we may miss a
                    // charge. In that case the rest may look like we gained
                    // power instead of losing power. In that case just toss
                    // the rest period. It will skew the data.
                    if (restInProgress.endRange <= restInProgress.startRange) {
                        addPeriod(restPeriods, restInProgress);
                    }
                }
                restInProgress = null;
            }
        }
    }
    
    private void displayResults(List<Rest> restPeriods, double overallAvg, String rawResults) {
        Map<Object, Object> props = new HashMap<>();
        props.put("REST_PERIODS", restPeriods);
        props.put("RAW_RESULTS", rawResults);
        props.put("OVERALL_AVG", overallAvg);
        props.put("UNITS", useMiles ? "mi" : "km");
        
        DialogUtils.DialogController dc = DialogUtils.displayDialog(
            getClass().getResource("dialogs/VampireLossResults.fxml"),
            "Vampire Loss", appContext.stage, props);
        if (dc == null) {
            Tesla.logger.warning("Unable to display Vampire Loss Dialog");
        }
    }
    
    private Map<String,Object> genProps() {
        TreeMap<Long,Map<String,Double>> rows = appContext.statsStore.getData();
        Map<String,Object> props = new HashMap<>();
        long timestamp = rows.firstKey(); 
        Calendar start = Calendar.getInstance();
        start.setTimeInMillis(timestamp);
        props.put("HIGHLIGHT_START", start);
        
        timestamp = rows.lastKey(); 
        Calendar end = Calendar.getInstance();
        end.setTimeInMillis(timestamp);
        props.put("HIGHLIGHT_END", end);
        return props;
    }
    
    private Range<Long> getExportPeriod() {
        Map<String,Object> props = genProps();
        DialogUtils.DialogController dc = DialogUtils.displayDialog(
            getClass().getResource("dialogs/DateRangeDialog.fxml"),
            "Select a Date Range", appContext.stage, props);
        if (dc == null) return null;
        DateRangeDialog drd = Utils.cast(dc);
        if (drd.selectedAll()) {
            return Range.closed(0L, Long.MAX_VALUE);
        }
        Calendar start = drd.getStartCalendar();
        Calendar end = drd.getEndCalendar();
        if (start == null) {
            return null;
        }
        return Range.closed(start.getTimeInMillis(), end.getTimeInMillis());
    }
    
    
    public class Rest {
        public long startTime, endTime;
        public double startRange, endRange;
        
        public Rest() {
            this(0, 0, 0, 0);
        }
        
        public Rest(long startTime, long endTime, double startRange, double endRange) {
            this.startTime = startTime;
            this.endTime = endTime;
            this.startRange = startRange;
            this.endRange = endRange;
        }
        
        public double loss() { return startRange - endRange; }
        public double avgLoss() { return loss() / hours(endTime - startTime); }
       
    }
}
