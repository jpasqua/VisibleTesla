/*
 * DBConverter.java - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Nov 30, 2014
 */
package org.noroomattheinn.visibletesla.data;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import org.noroomattheinn.timeseries.PersistentTS;
import org.noroomattheinn.timeseries.Row;
import org.noroomattheinn.timeseries.RowDescriptor;
import org.noroomattheinn.timeseries.TimeSeries;
import static org.noroomattheinn.tesla.Tesla.logger;
import org.noroomattheinn.visibletesla.stats.StatsRepository;

/**
 * DBConverter: Convert from an older version of the underlying DB.
 * Operation is as follows:<ul>
 * <ol>Create a DBConverter instance</ol>
 * <ol>Call conversionRequired to see if there's any work to do</ol>
 * <ol>If there is, call convert()</ol>
 * </ul>
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
class DBConverter {
/*------------------------------------------------------------------------------
 *
 * Internal State & types
 * 
 *----------------------------------------------------------------------------*/
    private final File container;
    private final String baseName;
    
    private static class MapTable extends TreeMap<Long,Map<String,Double>> {
        MapTable() { super(); }
        MapTable(MapTable src) { super(src); }
    }

/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/

    public DBConverter(File container, String baseName) {
        this.container = container;
        this.baseName = baseName;
    }
    
    public boolean conversionRequired() {
        if (PersistentTS.repoExistsFor(container, baseName)) {
            logger.fine("TimeSeries already exists");
            return false;
        }
        File oldRepo = new File(container, baseName + ".stats.log");
        if (oldRepo.exists()) {
            logger.info("No TimeSeries exists, but an old repo does");
            return true;
        }
        return false;
    }
    
    public void convert() throws IOException {
        Map<String,String> keyConversions = new HashMap<>();
        keyConversions.put("S_PWR", "L_PWR");
        keyConversions.put("S_SPD", "L_SPD");
        
        List<StatsRepository> repos = new ArrayList<>(2);
        repos.add(new StatsRepository(new File(container, baseName+".locs.log")));
        repos.add(new StatsRepository(new File(container, baseName+".stats.log")));
        
        TimeSeries ts = new PersistentTS(container, baseName, VTData.schema, true);
        
        // Load each Repo into a separate MapTable
        MapTable[] tableForRepo = new MapTable[repos.size()];
        for (int i = 0; i < repos.size(); i++) {
            logger.info("Loading data from repo " + i);
            tableForRepo[i] = new MapTable();
            repos.get(i).loadExistingData(new DoRecord(tableForRepo[i], keyConversions));
        }

        // Merge the repos together
        MapTable mergedData = new MapTable(tableForRepo[0]);
        for (int i = 1; i < repos.size(); i++) {
            logger.info("Merging data from repo " + i);
            MapTable table = tableForRepo[i];
            for (Map.Entry<Long,Map<String,Double>> entry : table.entrySet()) {
                long timestamp = entry.getKey();
                Map<String,Double> row = mergedData.get(timestamp);
                if (row == null) {
                    mergedData.put(timestamp, entry.getValue());
                } else {
                    for (Map.Entry<String,Double> val : entry.getValue().entrySet()) {
                        row.put(val.getKey(), val.getValue());
                    }
                }
            }
        }
        repos = null; tableForRepo = null;  // Allow the GC to do it's thing

        // Write the merged repos out to the new TimeSeries
        RowDescriptor schema = ts.getSchema();
        int nRowsStored = 0;
        logger.info("Number of rows to store: " + mergedData.size());
        for (Map.Entry<Long,Map<String,Double>> tableRow : mergedData.entrySet()) {
            long timestamp = tableRow.getKey();
            Map<String,Double> row = tableRow.getValue();
            Row r = new Row(timestamp, 0L, schema.nColumns);
            for (Map.Entry<String,Double> entry:row.entrySet()) {
                r.set(schema, entry.getKey(), entry.getValue());
            }
            ts.storeRow(r);
            nRowsStored++;
            if (nRowsStored % 10000 == 0) logger.info("Number of rows stored: " + nRowsStored);
        }
        logger.info("Total of " + nRowsStored + " stored");
        ts.close();
    }
    
    private static class DoRecord implements StatsRepository.Recorder {
        private final NavigableMap<Long,Map<String,Double>> rows;
        private final Map<String,String> conversions;
        private int rowsRecorded = 0;
        
        DoRecord(NavigableMap<Long,Map<String,Double>> rows,
                 Map<String,String> conversions) {
            this.rows = rows;
            this.conversions = conversions;
        }

        @Override public void recordElement(long time, String type, double val) {
            String convertedType = conversions.get(type);
            if (convertedType != null) type = convertedType;
            Map<String,Double> readings = rows.get(time);
            if (readings == null) { 
                readings = new HashMap<>();
                rows.put(time, readings);
                rowsRecorded++;
                if (rowsRecorded % 10000 == 0)
                    logger.info("Rows recorded: " + rowsRecorded);
            }
            readings.put(type, val);
        }
    }

}