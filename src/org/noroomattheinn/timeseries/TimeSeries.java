/*
 * TimeSeries.java - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Nov 25, 2014
 */
package org.noroomattheinn.timeseries;

import com.google.common.collect.Range;
import java.io.File;
import java.util.List;


/**
 * TimeSeries: Interface to Time Series repositories of various types
 * 
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public interface TimeSeries {

    /**
     * Store an entire row of values. The timestamp of this row must be >= all
     * existing rows or an IllegalArgumentException will result.
     *
     * @param r The row to be stored
     * @throws IllegalArgumentException If the timestamp of the row is less than
     * the timestamp of the newest value we've seen
     */
    public Row storeRow(Row r) throws IllegalArgumentException;
    
    /**
     * Load a range of data from this TimeSeries into another TimeSeries
     * @param ts        The TimeSeries to be loaded
     * @param period    The time period of interest. Null means all.
     */
    public void loadInto(final TimeSeries ts, Range<Long> period);

    /**
     * Stream a selected period of rows to a collector.
     * @param period    The time period of interest. Null means all.
     * @param collector The object collecting the rows.
     */
    public void streamRows(Range<Long> period, RowCollector collector);

    /**
     * Stream a selected period of values (individually) to a collector.
     * @param period    The time period of interest. Null means all.
     * @param collector The object collecting the individual values.
     */
    public void streamValues(Range<Long> period, ValueCollector collector);
    
    /**
     * Create an Excel file covering the specified range of times.
     * 
     * @param toFile            The output file (will be overwritten if it exists)
     * @param exportPeriod      The range of times to include in the export
     * @param columns           Which columns to include. Null means all.
     * @param includeDerived    If true, use the last known value for each column
     *                          in each row even if there is no reading for that
     *                          column at that time.
     * @return                  true if the export was successful, false otherwise
     */
    public boolean export(
            File toFile, Range<Long> exportPeriod,
            List<String> columns, boolean includeDerived);
    
    /**
     * Get the schema for this time series
     * @return RowDescriptor associated with this TimeSeries
     */
    public RowDescriptor getSchema();
    
    /**
     * The TimeSeries is no longer in use. Do any necessary finalization.
     */
    public void close();
    
    /**
     * Flush any outstanding data as needed.
     */
    public void flush();
    
    /**
     * Provides the time of the first row stored in the series.
     * @return  The timestamp of the first row in the series. If there are no
     *          rows yet, Long.MAX_VALUE will be returned.
     */
    public long firstTime();

    /**
     * Interface to an object that collects a stream of values
     */
    public interface ValueCollector {
        public boolean collect(long timestamp, String name, double value);
    }
    
    /**
     * Interface to an object that collects a stream of rows
     */
    public interface RowCollector {
        public boolean collect(Row r);
    }
}


