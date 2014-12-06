/*
 * InMemoryTS.java - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Nov 25, 2014
 */
package org.noroomattheinn.timeseries;

import com.google.common.collect.Range;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * InMemoryTS: In-Memory Time Series
 * 
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class InMemoryTS extends TSBase implements IndexedTimeSeries {
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private final NavigableMap<Long,Row> index;
    private final List<Row> rows;
    private final boolean forceOrdering;
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    /**
     * Create an In-Memory Time Series store
     * 
     * @param descriptor    Describes the schema of the rows in the store
     * @param forceOrdering If true, then all data added to the time series
     *                      will be forced to have monotonically increasing
     *                      timestamps. If a row or value is added whose time-
     *                      stamp is less than a value that has already been
     *                      added, the newer timestamp will be used.
     *                      If false, an old timestamp will result in an
     *                      IllegalArgumentException
     */
    public InMemoryTS(RowDescriptor descriptor, boolean forceOrdering) {
        super(descriptor);
        this.index = new TreeMap<>();
        this.rows = new ArrayList<>();
        this.forceOrdering = forceOrdering;

        // The code assumes there is always a "previous" row, so create a
        // zero-th row that serves as a backstop. It's never presented as
        // part of the actual data
        rows.add(new Row(0, 0, descriptor.nColumns));
    }
/*------------------------------------------------------------------------------
 *
 * Methods overriden from TimeSeries
 * 
 *----------------------------------------------------------------------------*/
    
    @Override public Row storeRow(Row rowToStore)
            throws IllegalArgumentException {
        Row existingRow = rows.get(rows.size() - 1);
        int nColumns = rowToStore.values.length;
        long newTime = adjustTimeIfNeeded(rowToStore.timestamp, existingRow.timestamp);
        
        if (newTime == existingRow.timestamp) {
            // Merge this row into existingRow
            logger.info("Merging rows at time: " + newTime);
            long bit = 1;
            for (int i = 0; i < nColumns; i++) {
                if (rowToStore.includes(bit)) {
                    existingRow.values[i] = rowToStore.values[i];
                    existingRow.bitVector |= bit;
                }
                bit = bit << 1;
            }
            return existingRow;
        } else {
            // Create new row based on the existing values
            Row newRow = new Row(newTime, rowToStore.bitVector, existingRow.values);
            
            // Now set the values given by rowToStore
            long bit = 1;
            for (int i = 0; i < nColumns; i++) {
                if (rowToStore.includes(bit)) {
                    newRow.values[i] = rowToStore.values[i];
                }
                bit = bit << 1;
            }
            rows.add(newRow);
            index.put(rowToStore.timestamp, newRow);
            return newRow;
        }
    }
    
    @Override public void streamRows(Range<Long> period, RowCollector collector) {
        NavigableMap<Long,Row> subMap = getIndex(period);
        for (Row row : subMap.values()) {
            if (!collector.collect(row)) return;
        }
    }
    
    @Override public long firstTime() {
        return (rows.size() == 1) ? Long.MAX_VALUE : rows.get(1).timestamp; 
    }

    @Override public void close() { }
    
    @Override public void flush() { }
    
/*------------------------------------------------------------------------------
 *
 * Methods overriden from IndexedTimeSeries
 * 
 *----------------------------------------------------------------------------*/
    
    @Override public NavigableMap<Long,Row> getIndex() { return index; }
    
    @Override public NavigableMap<Long,Row> getIndex(Range<Long> period) {
        long from = period.hasLowerBound() ? period.lowerEndpoint() : 0;
        long to = period.hasUpperBound() ? period.upperEndpoint() : Long.MAX_VALUE;
        return index.subMap(from, true, to, true);
    }
    
/*------------------------------------------------------------------------------
 *
 * Private Utility Methods
 * 
 *----------------------------------------------------------------------------*/
    
    private long adjustTimeIfNeeded(long newTime, long oldTime) {
        if (newTime >= oldTime) return newTime;
        if (forceOrdering) {
            logger.fine("Forcing timestamp: " + oldTime + ", " + newTime);
            return oldTime;
        } else {
            throw new IllegalArgumentException(
                    "Timestamps out of sequence: " + oldTime + ", " + newTime);
        }
    }

}
