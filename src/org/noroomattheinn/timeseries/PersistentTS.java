/*
 * PersistentTS.java - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Nov 25, 2014
 */
package org.noroomattheinn.timeseries;

import com.google.common.collect.Range;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * PersistentTS: A persistent repository for time series data.
 *
 * A PersistentTS is represented by a header file and a data file.
 * 
 * The header file contains two lines:
 * VERSION:
 *      A number that corresponds to the implementation that wrote the repository
 * STRING[\tSTRING]*
 *      A tab-separated list of strings. Each String represents the name of 
 *      a column that is stored in the data file
 * 
 * The data file contains lines that are either comments or data rows:
 * COMMENT: Any line beginning with a # is an uninterpreted comment
 * DATA ROW: All data rows have the form:
 *      TIMESTAMP BITVECTOR VAL[\tVAL\]*
 * where
 *      TIMESTAMP is a long which indicating the time of the sample. This value
 *      is delta-encoded meaning you must accumulate values up to a row in
 *      order to know the timestamp of that row. If the stored value is negative
 *      then it represents an absolute (not delta-encoded) value given by abs();
 * 
 *      BITVECTOR is the hex representation of a 64-bit bit vector
 *      which indicates which samples were recorded at this timestamp
 * 
 *      VAL+ is a tab separated list of values. There must be as
 *      many values in this list as 1 bits in the bit vector.
 *      A value may be either a double value represented as a
 *      String OR the literal "*" which indicates that this value 
 *      is the same as the last recorded value of this column.
 * 
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class PersistentTS extends TSBase {
/*------------------------------------------------------------------------------
 *
 * Constants and Enums
 * 
 *----------------------------------------------------------------------------*/
    private static final int RepoVersion = 1;
    
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/

    private final PrintStream dataStream;   // Where we write new entries
    private final Repo repo;                // The underlying repository
    
    private int  nRowsEmitted;              // Used as part of auto-flush
    private Row  lastRowEmitted;            // Used to delta-encode values
    private Row  pending;                   // Row that is waiting to be emitted
    private long pendingTime;               // Adjusted time for pending Row
    private long timeOfFirstRow;            // The oldest data in the series
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public PersistentTS(File container, String baseName, RowDescriptor descriptor)
            throws IOException {
        super(descriptor);
        
        this.repo = Repo.getRepo(container, baseName, descriptor.columnNames);
        this.dataStream = repo.getPrintStream();
        
        this.nRowsEmitted = 0;
        this.lastRowEmitted = new Row(-1L, 0L, descriptor.nColumns);
        this.pending = null;
        
        timeOfFirstRow = Long.MAX_VALUE;    // If no rows...
        streamRows(Range.<Long>all(), new RowCollector() {
            @Override public boolean collect(Row r) {
                timeOfFirstRow = r.timestamp;
                return false;
            }
        });
    }
    
    public static boolean repoExistsFor(File container, String baseName) {
        return Repo.repoExistsFor(container, baseName);
    }
 
/*------------------------------------------------------------------------------
 *
 * Methods overriden from TimeSeries
 * 
 *----------------------------------------------------------------------------*/
    
    @Override public long firstTime() { return timeOfFirstRow; }
        
    @Override public synchronized Row storeRow(Row r) throws IllegalArgumentException {
        long t;
        if (pending == null) {
            if (lastRowEmitted.timestamp < 0) t = -r.timestamp;
            else t = r.timestamp - lastRowEmitted.timestamp;
        } else {
            t = r.timestamp - pending.timestamp;
        }
        t = deflate(t);

        if (pending == null) {
            pending = r;
            pendingTime = t;
        } else {
            if (t == 0) {
                pending.mergeWith(r);
            } else {
                emit(pendingTime, pending);
                pending = r;
                pendingTime = t;
            }
        }

        return r;
    }
            
    @Override public final synchronized void streamRows(
            Range<Long> period, RowCollector collector) {
        double accumulator[] = new double[schema.nColumns];
        Map<String,Double> lastValSeen = new HashMap<>();
        if (period == null) period = Range.all();
        long prevTime = 0;
        BufferedReader rdr = null;
        try {
            rdr = new BufferedReader(new FileReader(repo.dataFile));
            String line;
            while ((line = rdr.readLine()) != null) {
                if (line.startsWith("#")) { continue; }
                String[] tokens = line.split("\t");
                
                // The first entry on the line is the time in delta format
                Long time = longValue(tokens[0]);
                if (time == null) { continue; } // Invalid format, ignore this line
                time = time < 0 ? -time : time + prevTime;
                prevTime = time;    // Keep a running tally of the current time
                
                time = inflate(time);
                if (!period.contains(time)) continue;
                
                Row row = new Row(time, 0L, schema.nColumns);
                
                // The second element is a bitvector corresponding to which
                // columns have values on this line
                Long bitVector = longValue("0x" + tokens[1]);
                if (bitVector == null) { continue; }    // Invalid format, Ignore this line
                row.bitVector = bitVector;
                
                // The remaining entries are readings. There is one reading for
                // each 1 bit in the bitvector. The positions in the bitvector
                // correspond to the columns in the order initially specified
                long bit = 1;
                int tokenIndex = 2;
                for (int i = 0; i < schema.nColumns; i++) {
                    int index = Row.indexForBit(bit);
                    if ((bit & bitVector) != 0) {
                        String valString = tokens[tokenIndex++];
                        Double val = (valString.equals("*")) ?
                            lastValSeen.get(schema.columnNames[i]): doubleValue(valString);
                        if (val != null) {
                            accumulator[index] = row.values[index] = val.doubleValue();
                            lastValSeen.put(schema.columnNames[i], val);
                        }
                    } else {
                        row.values[index] = accumulator[index];
                    }
                    bit = bit << 1;
                }
                if (!collector.collect(row)) break;
            }
        } catch (IOException ex) {
            logger.severe("Error loading from repository" + ex);
        }
        if (rdr != null) try { rdr.close(); } catch (IOException e) { }
    }

    @Override public void flush() {
        if (this.dataStream != null) dataStream.flush();
    }
    
    @Override public void close() {
        if (this.dataStream != null) dataStream.close();
    }
    
/*------------------------------------------------------------------------------
 *
 * PRIVATE - Utility methods
 * 
 *----------------------------------------------------------------------------*/
    
    private Row emit(long adjustedTime, Row r) {
        dataStream.print(adjustedTime);
        dataStream.append("\t");
        dataStream.append(Long.toHexString(r.bitVector));
        dataStream.append("\t");
        long bitForColumn = 1;
        for (int i = 0; i < schema.nColumns; i++) {
            if (r.includes(bitForColumn)) {
                if (r.values[i] == lastRowEmitted.values[i]) {
                    dataStream.print("*");
                } else {
                    dataStream.print(r.values[i]);
                }
                dataStream.append("\t");
            }
            bitForColumn = bitForColumn << 1;
        }
        dataStream.println();

        lastRowEmitted = r;
        if (nRowsEmitted++ % 10 == 0) { dataStream.flush(); }
        return r;
    }

    private static Long longValue(String valString) {
        try {
            return Long.decode(valString);
        } catch (NumberFormatException e) {
            logger.warning("Invalid Long in TimeSeries: " + valString);
            return null;
        }
    }
    
    private static Double doubleValue(String valString) {
        try {
            return Double.valueOf(valString);
        } catch (NumberFormatException e) {
            logger.warning("Invalid Double in TimeSeries: " + valString);
            return null;
        }
    }
    
    private long deflate(long timestamp) { return timestamp/100; }
    private long inflate(long timestamp) { return timestamp*100; }
    
/*------------------------------------------------------------------------------
 *
 * PRIVATE - The class implementing the filed-based repository
 * 
 *----------------------------------------------------------------------------*/

    private static class Repo {
        private final String[] columns;
        private File dataFile;
        private File hdrFile;
        private PrintStream ps;
        
        private Repo(File container, String name, String[] columns) {
            this.columns = columns;
            dataFile = dataFile(container, name);
            hdrFile =  headerFile(container, name);
            this.ps = null;
        }
        
        static boolean repoExistsFor(File container, String baseName) {
            return headerFile(container, baseName).exists() &&
                   dataFile(container, baseName).exists();
        }

        static Repo getRepo(File container, String name, String[] columns)
                throws IOException {
            Repo repo = new Repo(container, name, columns);
            if (!repo.hdrFile.exists() && repo.dataFile.exists()) {
                // Danger! The data file has become "disconnected" from the
                // header file. Don't create a new data file - the data is valuable
                // Don't just create a new header file because you don't know
                // if the schemas match. It's safest to raise an exception.
                throw new FileNotFoundException("Data file without Header file");
            }
            
            repo.ensureValidHeader();
            if (!repo.dataFile.exists()) repo.createDataFile();
            repo.ps = new PrintStream(new FileOutputStream(repo.dataFile, true));
            return repo;
        }
        
        public PrintStream getPrintStream() { return ps; }

        private void ensureValidHeader() throws IOException {
            if (!hdrFile.exists()) {
                createHeaderFile();
                return;
            }
            
            // Read the existing header file and make sure it's valid
            String line;
            BufferedReader reader = new BufferedReader(new FileReader(hdrFile));
            
            line = reader.readLine();
            if (line == null)  throw new IOException("Empty Header File");
            
            int version = Integer.valueOf(line);
            if (version > RepoVersion)
                throw new IOException(
                        "Can't read newer repo version :" + version + " vs " + RepoVersion);

            line = reader.readLine();
            if (line == null) throw new IOException("Missing column name declarations");

            String[] declaredNames = line.split("\t");
            if (declaredNames.length > columns.length) {
                throw new IOException("Mismatched column names - too few supplied names");
            }
            
            for (int i = 0; i < declaredNames.length; i++) {
                if (!declaredNames[i].equals(columns[i])) {
                    throw new IOException("Mismatched column names");
                }
            }
            reader.close();
            
            if (columns.length > declaredNames.length) {
                logger.info("Adding new column(s)");
                createHeaderFile(); // We've got new columns! Overwrite the header file
            }
        }
        
        private void createHeaderFile() throws FileNotFoundException {
            PrintStream writer = new PrintStream(new FileOutputStream(hdrFile, false));
            writer.format("%d\n", RepoVersion);
            int lastIndex = columns.length-1;
            int index = 0;
            while (true) {
                writer.append(columns[index]);
                if (index++ != lastIndex) writer.append("\t");
                else break;
            }
            writer.close();
        }
        
        private void createDataFile() throws FileNotFoundException {
            PrintStream writer = new PrintStream(new FileOutputStream(dataFile), false);
            writer.format("# %s\n", (new Date().toString()));
            writer.close();
        }

        private static File headerFile(File container, String baseName) {
            return new File(container, baseName + ".stats.hdr");
        }
    
        private static File dataFile(File container, String baseName) {
            return new File(container, baseName + ".stats.data");
        }
    

    }
}
