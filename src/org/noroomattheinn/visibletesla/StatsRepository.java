/*
 * StatsRepository.java - Copyright(c) 2013  All Rights Reserved, Joe Pasqua
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
import java.util.Scanner;
import java.util.logging.Level;
import org.noroomattheinn.tesla.Tesla;

/**
 * StatsRepository
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */

public class StatsRepository {
    private String vin;
    private PrintStream statsWriter;
    private Map<String,Entry> lastEntryMap = new HashMap<>();

    StatsRepository(String forVIN, Recorder recorder) {
        this.vin = forVIN;
        prepRepository();
        loadExistingData(recorder);
    }
    
    interface Recorder {
        void recordElement(long time, String type, double val);
    }
    
    
    void storeElement(String type, long time, double value) {
        if (statsWriter == null)  return;
        
        Entry lastEntry = lastEntryMap.get(type);
        
        if (lastEntry == null) {    // First entry, write it out and remember it
            statsWriter.format("%d\t%s\t%3.1f\n", time, type, value);
        } else {
            if (lastEntry.value != value) {
                // It's a different value, write out the old and the new
                statsWriter.format("%d\t%s\t%3.1f\n", lastEntry.time, type, lastEntry.value);
                statsWriter.format("%d\t%s\t%3.1f\n", time, type, value);
            } else {
            }
        }
        lastEntryMap.put(type, new Entry(time, type, value));
    }
    
    void close() {
        if (statsWriter != null) statsWriter.close();
    }

    void flushElements() {
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
    }

    //
    // PRIVATE
    // This section has the code pertaining to the storage repository
    // for the sampled data
    //
    
    private String fileNameForVIN(String vin) { return vin + ".stats.log"; }
    private String auxNameForVIN(String vin) { return vin + ".stats.aux"; }
    
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
    
    private void loadExistingData(Recorder r) {
        processAndDeleteAuxFile();
        try {
            Scanner s = new Scanner(new BufferedReader(new FileReader(fileNameForVIN(vin))));
            // <long>time       <String>type    <double>value
            // 1377316202051    C_EST           126.6
            while (s.hasNext()) {
                long time = s.nextLong();
                String type = s.next();
                double val = s.nextDouble();
                r.recordElement(time, type, val);
            }
        } catch (FileNotFoundException ex) {
            Tesla.logger.log(Level.INFO, "Starting with partial/empty stats", ex);
        }
    }
    
    
    //
    // Private Utility Classes
    //

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
