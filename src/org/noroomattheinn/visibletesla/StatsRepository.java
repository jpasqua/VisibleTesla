/*
 * StatsRepository.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Aug 25, 2013
 */

package org.noroomattheinn.visibletesla;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import org.noroomattheinn.tesla.Tesla;
import org.noroomattheinn.utils.Utils;

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
    private Map<String,Entry> lastEntryMap = new HashMap<>();
    private double cumulativeChange = 0.0;
    private int valuesWritten = 0;
    
    
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
        processAndDeleteAuxFile();
        BufferedReader rdr = getReaderForFile(fileNameForVIN(vin));
        if (rdr == null) return;
        
        String line;
        while ((line = getLineFromReader(rdr)) != null) {
            String tokens[] = line.split("\\s");
            if (tokens.length != 3) {   // Malformed line
                Tesla.logger.log(Level.INFO, "Malformed stats entry: Improper number of tokens");                
                continue;
            }
            try {
                long time = Long.valueOf(tokens[0]);
                String type = tokens[1];
                double val = Double.valueOf(tokens[2]);
                r.recordElement(time, type, val);
            } catch (NumberFormatException ex) {
                Tesla.logger.log(Level.INFO, "Malformed stats entry", ex);
            }
        }
    }
    
    void storeElement(String type, long time, double value) {
        if (statsWriter == null)  return;
        
        Entry lastEntry = lastEntryMap.get(type);
        
        if (lastEntry == null) {    // First entry, write it out and remember it
            statsWriter.format("%d\t%s\t%3.1f\n", time, type, value);
            cumulativeChange += 1.0;
            valuesWritten++;
        } else {
            if (lastEntry.value != value) {
                // It's a different value, write out the old and save the new one
                statsWriter.format("%d\t%s\t%3.1f\n", lastEntry.time, type, lastEntry.value);
                // statsWriter.format("%d\t%s\t%3.1f\n", time, type, value);
                cumulativeChange += Utils.percentChange(lastEntry.value, value);
                valuesWritten++;
            }
        }
        lastEntryMap.put(type, new Entry(time, type, value));
    }
    
    void close() {
        if (statsWriter != null) statsWriter.close();
    }

    double flushElements() {
        statsWriter.flush();
        
        String auxName = auxNameForVIN(vin);
        try {
            PrintStream auxWriter = new PrintStream(new FileOutputStream(auxName, false));
            for (Entry entry : lastEntryMap.values()) {
                auxWriter.format("%d\t%s\t%3.1f\n", entry.time, entry.type, entry.value);
            }
            auxWriter.close();
        } catch (FileNotFoundException ex) {
            Tesla.logger.log(Level.WARNING, "Can't create aux file: " + auxName, ex);
        }
        double avgPctChange = valuesWritten == 0 ? 0 : cumulativeChange/valuesWritten;
        cumulativeChange = 0;
        valuesWritten = 0;
        return avgPctChange;
    }

/*------------------------------------------------------------------------------
 *
 * This section has the PRIVATE code pertaining to the storage repository
 * for the sampled data
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
    
    private void processAndDeleteAuxFile() {
        File auxFile = new File(auxNameForVIN(vin));
        File statsFile = new File(fileNameForVIN(vin));
        if (!auxFile.exists()) return;

        try {
            InputStream in = new FileInputStream(auxFile);
            OutputStream out = new FileOutputStream(statsFile,true);
 
            int len;
            byte[] buf = new byte[8192];
            while ((len = in.read(buf)) > 0) { out.write(buf, 0, len); }
            in.close(); out.close();
            auxFile.delete();
        } catch(FileNotFoundException ex) {
            Tesla.logger.log(Level.INFO, "Starting with partial/empty stats", ex);
        } catch (IOException ex) {
            Tesla.logger.log(Level.INFO, "Problem reading stats", ex);
        } 
    }
    
    
    
/*------------------------------------------------------------------------------
 *
 * Private Utility Methods and Classes
 * 
 *----------------------------------------------------------------------------*/
    
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
    private String auxNameForVIN(String vin) { return vin + ".stats.aux"; }
    
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
