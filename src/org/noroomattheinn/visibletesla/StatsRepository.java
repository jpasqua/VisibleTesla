/*
 * StatsRepository.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Aug 25, 2013
 */

package org.noroomattheinn.visibletesla;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import org.noroomattheinn.tesla.Tesla;

/**
 * StatsRepository
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */

public class StatsRepository {
    
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/

    private String vin;
    private PrintStream statsWriter;
    private Map<String,Double> lastRowWritten = new HashMap<>();
    private List<Entry> entriesSinceLastFlush = new ArrayList<>();
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/

    StatsRepository(String forVIN) {
        this.vin = forVIN;
        prepRepository();
    }
    
    interface Recorder {
        void recordElement(long time, String type, double val);
    }
    
    void loadExistingData(Recorder r) {
        BufferedReader rdr = getReaderForFile(fileNameForVIN(vin));
        if (rdr == null) return;
        
        String line;
        Map<String,String>  lastEntries = new HashMap<>();
        
        while ((line = getLineFromReader(rdr)) != null) {
            String tokens[] = line.split("\\s");
            
            if ((tokens.length-1)%2 != 0) {   // Malformed line
                Tesla.logger.log(
                    Level.INFO, "Malformed stats entry: Improper number of tokens: {0}", line);                
                continue;
            }
            
            Map<String,String> merged = mergeEntries(tokens, lastEntries);
            try {
                long time = Long.valueOf(tokens[0]);
                for (Map.Entry<String,String> entry : merged.entrySet()) {
                    String type = entry.getKey();
                    double val = Double.valueOf(entry.getValue());
                    r.recordElement(time, type, val);
                }
                lastEntries = merged;
            } catch (NumberFormatException ex) {
                Tesla.logger.log(Level.INFO, "Malformed stats entry", ex);
            }
        }
    }
    
    void loadTimeRange(final long oldest, final long newest, final Recorder r) {
        loadExistingData(new Recorder() {
            @Override public void recordElement(long time, String type, double val) {
                if (time >= oldest && time <= newest) r.recordElement(time, type, val);
            }
            
        });
    }
    
    void storeElement(String type, long time, double value) {
        if (statsWriter == null)  return;
        entriesSinceLastFlush.add(new Entry(time, type, value));
    }
    
    void close() {
        if (statsWriter != null) statsWriter.close();
    }

    
    void flushElements() {
        Map<Long,Map<String,Double>> rows = new TreeMap<>();
        
        // It's possible that there are entries for multiple points in time
        // Create a map with a key for each unique time where the value
        // is a list of Entries with that time
        for (Entry entry : entriesSinceLastFlush) {
            Map<String,Double> row = rows.get(entry.time);
            if (row == null) {
                row = new HashMap<>();
                rows.put(entry.time, row);
            }
            row.put(entry.type, entry.value);
        }
        
        
        for (Map.Entry<Long,Map<String,Double>> row : rows.entrySet()) {
            long time = row.getKey();
            Map<String,Double> currentEntries = row.getValue();
            Map<String,Double> uniqueEntries = mergeOutput(lastRowWritten, currentEntries);
            boolean hasDupes = uniqueEntries.size() != currentEntries.size();
            
            statsWriter.print(time );
            if (hasDupes)
                statsWriter.print(" * *");
            for (Map.Entry<String,Double> entry:uniqueEntries.entrySet()) {
                statsWriter.print(" " + entry.getKey() + " " + entry.getValue());
            }
            statsWriter.println();
            lastRowWritten = currentEntries;
        }
        
        statsWriter.flush();
        entriesSinceLastFlush.clear();
    }

    
/*------------------------------------------------------------------------------
 *
 * PRIVATE - Utility methods that aid in the reading and writing of data
 * to the external file in a space efficient manner
 * 
 *----------------------------------------------------------------------------*/
    
    private Map<String,String> mergeEntries(String[] newTokens, Map<String,String>lastEntries) {
        // If the newTokens contains a "*" entry, then start by copying the last
        // set of values. If not, start with an empty Map.
        Map<String,String> vals = null;
        for (String token : newTokens) {
            if (token.equals("*")) {
                vals = new HashMap<>(lastEntries);
                break;
            }
        }
        if (vals == null) vals = new HashMap<>();
        
        // Go through the new tokens and overwrite any previous values for the same type
        for (int i = 1; i < newTokens.length; ) {
            String type = newTokens[i++];
            String value = newTokens[i++];
            if (!type.equals("*"))
                vals.put(type, value);
        }
        
        return vals;
    }
    
    private Map<String,Double> mergeOutput(Map<String,Double> old, Map<String,Double> current) {
        Map<String,Double> merged = new HashMap<>(current);
        for (Map.Entry<String,Double> entry : old.entrySet()) {
            String oldType = entry.getKey();
            Double oldVal = entry.getValue();
            Double curVal = current.get(oldType);
            if (curVal != null && curVal.equals(oldVal))
                merged.remove(oldType);
        }
        return merged;
    }
    
/*------------------------------------------------------------------------------
 *
 * Private Utility Methods and Classes
 * 
 *----------------------------------------------------------------------------*/
    
    private void prepRepository() {
        if (statsWriter != null) { statsWriter.close(); }
        String statsFileName = fileNameForVIN(vin);
        try {
            statsWriter = new PrintStream(new FileOutputStream(statsFileName, true));
        } catch (FileNotFoundException ex) {
            Tesla.logger.log(Level.WARNING, "Can't create stats file: " + statsFileName, ex);
            statsWriter = null;
        }
    }
    
    private BufferedReader getReaderForFile(String fileName) {
        try {
            return new BufferedReader(new FileReader(fileName));
        } catch (FileNotFoundException ex) {
            Tesla.logger.log(Level.INFO, "Could not open file", ex);
        }
        return null;
    }
    
    private String getLineFromReader(BufferedReader rdr) {
        try {
            return rdr.readLine();
        } catch (IOException ex) {
            Tesla.logger.log(Level.INFO, "Failed reading line", ex);
        }
        return null;
    }
    
    private String fileNameForVIN(String vin) { return vin + ".stats.log"; }
    
    private class Entry {
        public final long time;
        public final double value;
        public final String type;
        Entry(long time, String type, double value) {
            this.time = time;
            this.type = type;
            this.value = value;
        }
    }
    

}
