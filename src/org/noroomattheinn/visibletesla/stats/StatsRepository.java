/*
 * StatsRepository.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Aug 25, 2013
 */

package org.noroomattheinn.visibletesla.stats;

import com.google.common.collect.Range;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    private final File statsFile;
    private final List<Stat> entriesSinceLastFlush = new ArrayList<>();
    
    private PrintStream statsWriter;
    //private FileLock repoLock = null;
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/

    public StatsRepository(File statsFile) throws IOException {
        this.statsFile = statsFile;
        if (!prepRepository()) {
            throw new IOException("Unable to access repository: " + statsFile);
        }
    }
    
    public interface Recorder {
        void recordElement(long time, String type, double val);
    }
    
    public synchronized void loadExistingData(Recorder r) {
        BufferedReader rdr = getReaderForFile(statsFile);
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
    
    public synchronized void loadExistingData(final Recorder r, final Range<Long> period) {
        loadExistingData(new Recorder() {
            @Override public void recordElement(long time, String type, double val) {
                if (period.contains(time)) r.recordElement(time, type, val);
            }
        });
    }
    
    public synchronized void storeElement(String type, long time, double value) {
        if (statsWriter == null)  return;
        if (Double.isNaN(value) || Double.isInfinite(value)) value = 0.0;
        entriesSinceLastFlush.add(new Stat(time, type, value));
    }
    
    public synchronized void close() {
        if (statsWriter != null) statsWriter.close();
    }

    private final Map<String,Double> lastValForType = new HashMap<>();
    private Set<String> columnsInLastRow = new HashSet<>();
    
    private boolean sameColumnsAsLastTime(Map<String,Double> thisRow) {
        if (thisRow.size() != columnsInLastRow.size()) return false;
        for (String columnName : columnsInLastRow) {
            if (thisRow.get(columnName) == null) return false;
        }
        return true;
    }
    
    public synchronized void flushElements() {
        Map<Long,Map<String,Double>> rows = new TreeMap<>();
        
        // It's possible that there are entries for multiple points in time
        // Create a map with a key for each unique time where the value
        // is a list of Entries with that time
        for (Stat entry : entriesSinceLastFlush) {
            Map<String,Double> row = rows.get(entry.sample.timestamp);
            if (row == null) {
                row = new HashMap<>();
                rows.put(entry.sample.timestamp, row);
            }
            row.put(entry.type, entry.sample.value);
        }
        
        for (Map.Entry<Long,Map<String,Double>> row : rows.entrySet()) {
            long time = row.getKey();
            Map<String,Double> thisRow = row.getValue();
            if (!sameColumnsAsLastTime(thisRow)) { lastValForType.clear(); }
            
            StringBuilder newValues = new StringBuilder();
            boolean hasDupes = false;
            Set<String> columnsInThisRow = new HashSet<>();
            for (Map.Entry<String,Double> column : thisRow.entrySet()) {
                String type = column.getKey();
                double value = column.getValue();
                columnsInThisRow.add(type);
                Double lastValue = lastValForType.get(type);
                if (lastValue == null || lastValue != value) {
                    lastValForType.put(type, value);
                    newValues.append(" ").append(type).append(" ").append(value);
                } else {
                    hasDupes = true;
                }
            }
            statsWriter.print(time);
            if (hasDupes) statsWriter.print(" * *");
            statsWriter.println(newValues.toString());
            columnsInLastRow = columnsInThisRow;
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
        // If the newTokens contains a "*" column, then start by copying the last
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
    
/*------------------------------------------------------------------------------
 *
 * Private Utility Methods and Classes
 * 
 *----------------------------------------------------------------------------*/
    
    private boolean prepRepository() {
        releaseWriter();
        FileOutputStream fos = obtainStream();
        if (fos == null) return false;
        statsWriter = new PrintStream(fos);
        return (statsWriter != null);
    }
    
    private FileOutputStream obtainStream() {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(statsFile, true);
        } catch (IOException e) {
            Tesla.logger.warning("Unable to obtain lock on StatsRepository: " + e.toString());
        }
        return fos;
    }
    
    private void releaseWriter() {
        if (statsWriter != null)  statsWriter.close();
    }
    
    private BufferedReader getReaderForFile(File file) {
        try {
            return new BufferedReader(new FileReader(file));
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
        
}
