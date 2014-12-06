/*
 * IndexedTimeSeries.java - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Nov 25, 2014
 */
package org.noroomattheinn.timeseries;

import com.google.common.collect.Range;
import java.util.NavigableMap;

/**
 * IndexedTimeSeries: Operations on a TimeSeries based on indexes.
 * 
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public interface IndexedTimeSeries extends TimeSeries {

    /**
     * Return an index on the rows in the time series. This allows random access
     * to the rows without having to stream them. The returned index should not
     * be modified.
     * @return A NavigableMap providing a way to access any row based on timestamp
     */
    public NavigableMap<Long,Row> getIndex();
    
    /**
     * Return an index on a range of rows in the time series. Like getIndex(), 
     * but only returns the rows in the given time period.
     * 
     * @param period    The time period defining the range of the returned index
     * @return A NavigableMap providing a way to access any row based on timestamp
     */
    public NavigableMap<Long,Row> getIndex(Range<Long> period);

}
