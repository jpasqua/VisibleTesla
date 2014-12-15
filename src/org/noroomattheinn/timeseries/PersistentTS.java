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
import java.util.Timer;
import java.util.TimerTask;

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
 *      A value may be:<ul>
 *      <li>A double value represented as a String</li>
 *      <li>The literal "*" which indicates that this value 
 *      is the same as the last recorded value of this column.</li>
 *      <li>The literal "!" which indicates that this value 
 *      should be ignored and removed from the bit vector. This
 *      can be used to take the place of NaN or INF values.</li>
 *      </ul>
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
    private static final long FlushInterval = 20 * 1000L;
    
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/

    private final Repo repo;        // The underlying repository
    private final Emitter emitter;  // Used to write rows
    private final Timer timer;      // To manage flushing
    private Row pendingRow;         // Used to merge rows if needed
    private long timeOfFirstRow;    // The oldest data in the series
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public PersistentTS(File container, String baseName, RowDescriptor schema)
            throws IOException {
        super(schema);
        
        this.repo = Repo.getRepo(container, baseName, schema);
        this.emitter = new Emitter();
        this.pendingRow = null;
        this.timer = new Timer();
        
        timer.schedule(
                new TimerTask() { @Override public void run() { flush(); } },
                FlushInterval);
        
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
        if (pendingRow == null) {
            pendingRow = r;
        } else {
            if (deflate(r.timestamp) == deflate(pendingRow.timestamp)) {
                pendingRow.mergeWith(r);
                logger.info("Merging");
            } else {
                emitter.emit(pendingRow);
                pendingRow = r;
            }
        }
        
        return r;
    }
    
    @Override public final synchronized void streamRows(
            Range<Long> period, RowCollector collector) {
        double accumulator[] = new double[schema.nColumns];
        if (period == null) period = Range.all();
        long fromTime = period.hasLowerBound() ? period.lowerEndpoint() : 0L;
        long toTime = period.hasUpperBound() ? period.upperEndpoint() : Long.MAX_VALUE;
        long prevTime = 0;
        BufferedReader rdr = null;
        try {
            rdr = repo.getReader();
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
                if (time < fromTime) continue;  // Out of range, ignore & move on
                if (time > toTime) break;       // Out of range, ignore & stop
                
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
                    row.values[i] = accumulator[i]; // Start off with the previous value
                    if (row.includes(bit)) {
                        String valString = tokens[tokenIndex++];
                        switch (valString) {
                            case "*": break;
                            case "!": row.clear(bit); break;
                            default: 
                                Double val = doubleValue(valString);
                                if (val == null) { row.clear(bit); }
                                else { accumulator[i] = row.values[i] = val.doubleValue(); }
                                break;
                        }
                    } else {
                        row.values[i] = accumulator[i];
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

    @Override public synchronized void flush() {
        if (pendingRow != null) {
            emitter.emit(pendingRow);
            pendingRow = null;
        }
        repo.flush();
    }
    
    @Override public synchronized void close() {
        flush();
        repo.close();
        timer.cancel();
    }
    
/*------------------------------------------------------------------------------
 *
 * PRIVATE - Utility methods
 * 
 *----------------------------------------------------------------------------*/
    
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
    
    private static long deflate(long timestamp) { return timestamp/100; }
    private static long inflate(long timestamp) { return timestamp*100; }
    
    private class Emitter {
        private Row lastRowEmitted;
        private final PrintStream ps;
        
        Emitter() {
            this.lastRowEmitted = null;
            this.ps = repo.getPrintStream();
        }
        
        Row emit(Row r) {
            // Emit the timestamp for the row
            long time = lastRowEmitted == null ?  -deflate(r.timestamp) :
                    deflate(r.timestamp) - deflate(lastRowEmitted.timestamp);
            ps.print(time);

            // Emit the bit vector describing which columns are included
            ps.append("\t");
            ps.append(Long.toHexString(r.bitVector));

            // Emit the column values
            long bitForColumn = 1;
            for (int i = 0; i < schema.nColumns; i++) {
                if (r.includes(bitForColumn)) {
                    ps.append("\t");
                    double val = r.values[i];
                    if (Double.isInfinite(val) || Double.isNaN(val)) {
                        ps.print("!");
                    } else if (lastRowEmitted != null && val == lastRowEmitted.values[i]) {
                        ps.print("*");
                    } else {
                        ps.print(val);
                    }
                }
                bitForColumn = bitForColumn << 1;
            }
            ps.println();

            lastRowEmitted = r;
            return r;
        }
    }
    
/*------------------------------------------------------------------------------
 *
 * PRIVATE - The class implementing the filed-based repository
 * 
 *----------------------------------------------------------------------------*/

    private static class Repo {
        private final RowDescriptor schema;
        private final File dataFile;
        private final File hdrFile;
        private PrintStream ps;
        
        private Repo(File container, String name, RowDescriptor schema) {
            this.schema = schema;
            this.dataFile = dataFile(container, name);
            this.hdrFile =  headerFile(container, name);
            this.ps = null;
        }
        
        static boolean repoExistsFor(File container, String baseName) {
            return headerFile(container, baseName).exists() &&
                   dataFile(container, baseName).exists();
        }
        
        public void flush() { if (ps != null) ps.flush(); }

        public void close() { if (ps != null) ps.close(); }
        
        
        static Repo getRepo(File container, String name, RowDescriptor schema)
                throws IOException {
            Repo repo = new Repo(container, name, schema);
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
        public BufferedReader getReader() throws FileNotFoundException {
            return new BufferedReader(new FileReader(dataFile));
        };

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
            if (declaredNames.length > schema.nColumns) {
                throw new IOException("Mismatched column names - too few supplied names");
            }
            
            for (int i = 0; i < declaredNames.length; i++) {
                if (!declaredNames[i].equals(schema.columnNames[i])) {
                    throw new IOException("Mismatched column names");
                }
            }
            reader.close();
            
            if (schema.nColumns > declaredNames.length) {
                logger.info("Adding new column(s)");
                createHeaderFile(); // We've got new columns! Overwrite the header file
            }
        }
        
        private void createHeaderFile() throws FileNotFoundException {
            PrintStream writer = new PrintStream(new FileOutputStream(hdrFile, false));
            writer.format("%d\n", RepoVersion);
            int lastIndex = schema.nColumns-1;
            int index = 0;
            while (true) {
                writer.append(schema.columnNames[index]);
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
            return new File(container, baseName + ".pts.hdr");
        }
    
        private static File dataFile(File container, String baseName) {
            return new File(container, baseName + ".pts.data");
        }

    }
}
