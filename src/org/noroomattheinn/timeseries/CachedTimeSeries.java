/*
 * CachedTimeSeries.java - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Nov 25, 2014
 */
package org.noroomattheinn.timeseries;

import com.google.common.collect.BoundType;
import com.google.common.collect.Range;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.NavigableMap;
import java.util.logging.Logger;

/**
 * CachedTimeSeries: A TimeSeries that is persistent but also has an in-memory cache.
 * 
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
    
public class CachedTimeSeries implements TimeSeries, IndexedTimeSeries {
/*------------------------------------------------------------------------------
 *
 * Constants and Enums
 * 
 *----------------------------------------------------------------------------*/
    protected static final Logger logger = Logger.getLogger("org.noroomattheinn.timeseries");
    
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    private final RowDescriptor schema;
    private final PersistentTS persistent;
    private final InMemoryTS inMemory;
    
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    /**
     * Create a CachedTimeSeries with an empty cache.
     * 
     * @param container     The folder containing the PersistentTimeSeries data
     * @param baseName      The name of the PersistentTimeSeries
     * @param descriptor    A Descriptor giving the schema of the rows
     * @throws IOException  If the PersistentTimeSeries is unavailable
     */
    public CachedTimeSeries(
            File container, String baseName, RowDescriptor descriptor)
            throws IOException {
        this(container, baseName, descriptor, 0);
    }
    
    /**
     * Create a CachedTimeSeries and initialize the cache to a given period.
     * 
     * @param container     The folder containing the PersistentTimeSeries data
     * @param baseName      The name of the PersistentTimeSeries
     * @param descriptor    A Descriptor giving the schema of the rows
     * @param amtToCache    Cache the range (now-amtToCache, now)
     * @throws IOException  If the PersistentTimeSeries is unavailable
     */
    public CachedTimeSeries(
            File container, String baseName, RowDescriptor descriptor, long amtToCache)
            throws IOException {
        this(container, baseName, descriptor, Range.<Long>downTo(
                System.currentTimeMillis() - amtToCache, BoundType.OPEN));
    }
    
    /**
     * Create a CachedTimeSeries and initialize the cache to a given period.
     * 
     * @param container     The folder containing the PersistentTimeSeries data
     * @param baseName      The name of the PersistentTimeSeries
     * @param descriptor    A Descriptor giving the schema of the rows
     * @param cacheRange    The range of data to cache
     * @throws IOException  If the PersistentTimeSeries is unavailable
     */
    public CachedTimeSeries(
            File container, String baseName, RowDescriptor descriptor, Range<Long> cacheRange)
            throws IOException {
        this.schema = descriptor;
        this.inMemory = new InMemoryTS(descriptor, true);
        this.persistent = new PersistentTS(container, baseName, descriptor, true);
        persistent.loadInto(inMemory, cacheRange);
    }
    
    /**
     * Get the underlying CachedTimeSeries. Use this method if you want to be
     * sure you're operating on only the in-memory component of this TimeSeries
     * @return  An IndexedTimeSeries representing the in-memory component of
     *          this TimeSeries 
     */
    public IndexedTimeSeries getCachedSeries() { return inMemory; }
    
/*------------------------------------------------------------------------------
 *
 * Methods overriden from TimeSeries
 * 
 *----------------------------------------------------------------------------*/
    
    @Override public Row storeRow(Row r) throws IllegalArgumentException {
        Row storedRow = inMemory.storeRow(r);
        return persistent.storeRow(storedRow);
    }

    @Override public void streamRows(Range<Long> period, RowCollector collector) {
        if (period == null) period = Range.<Long>all();
        tsForPeriod(period).streamRows(period, collector);
    }

    @Override public void loadInto(TimeSeries ts, Range<Long> period) {
        tsForPeriod(period).loadInto(ts, period);
    }

    @Override public void streamValues(Range<Long> period, ValueCollector collector) {
        tsForPeriod(period).streamValues(period, collector);
    }
    
    @Override public boolean export(
        File toFile, Range<Long> period,
        List<String> columns, boolean includeDerived) {
        return tsForPeriod(period).export(toFile, period, columns, includeDerived);
    }
    
    @Override public void flush() {
        persistent.flush();
    }
    
    @Override public void close() {
        flush();
        persistent.close();
    }
    
    @Override public long firstTime() {
        return Math.min(persistent.firstTime(), inMemory.firstTime());
    }
    
    @Override public RowDescriptor getSchema() { return schema; }
    
/*------------------------------------------------------------------------------
 *
 * Methods overriden from IndexedTimeSeries
 * 
 *----------------------------------------------------------------------------*/
    
    @Override public NavigableMap<Long, Row> getIndex() {
        return getIndex(Range.<Long>all());
    }

    @Override public NavigableMap<Long, Row> getIndex(Range<Long> period) {
        if (useInMemory(period)) {
            return inMemory.getIndex(period);
        } else {
            InMemoryTS tempTS = new InMemoryTS(schema, false);
            persistent.loadInto(tempTS, period);
            return tempTS.getIndex();
        }
    }

/*------------------------------------------------------------------------------
 *
 * Private Utility Methods
 * 
 *----------------------------------------------------------------------------*/

    private TimeSeries tsForPeriod(Range<Long> period) {
        return useInMemory(period) ? inMemory : persistent;
    }
    
    private boolean useInMemory(Range<Long> period) {
        boolean im = useInMemoryInternal(period);
        logger.finest("Use InMemory: " + im);
        return im;
    }
    
    private boolean useInMemoryInternal(Range<Long> period) {
        long firstInMemory = inMemory.firstTime();
        long firstPersistent = persistent.firstTime();
        
        if (firstInMemory <= firstPersistent) return true;
        if (period.hasLowerBound() && firstInMemory <= period.lowerEndpoint()) return true;
        return false;
    }
    

}
