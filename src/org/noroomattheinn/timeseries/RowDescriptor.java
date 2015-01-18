/*
 * RowDescriptor.java - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Nov 25, 2014
 */
package org.noroomattheinn.timeseries;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * RowDescriptor: An immutable description of a row.
 * 
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class RowDescriptor {
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    private final Map<String,Long> bitForColumn;
    private final Map<String,Integer> indexOfColumn;

/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/

    /**
     * The array of column names that were presented when the RowDescriptor was
     * created. DO NOT modify any of the values of this array.
     */
    public final String[] columnNames;
    
    /**
     * The number of columns in a row.
     */
    public final int nColumns;
    
    /**
     * Create a RowDescriptor based based on an ordered array of column names.
     * 
     * @param columnNames   An ordered list of columns in a row.
     */
    public RowDescriptor(String[] columnNames) {
        this.nColumns = columnNames.length;
        this.columnNames = new String[this.nColumns];
        System.arraycopy(columnNames, 0, this.columnNames, 0, nColumns);
        this.bitForColumn = new HashMap<>();
        this.indexOfColumn = new HashMap<>();
        
        long bit = 1;
        for (int i = 0; i < nColumns; i++) {
            bitForColumn.put(columnNames[i], bit);
            bit = bit << 1;
            indexOfColumn.put(columnNames[i], i);
        }
    }
    
    /**
     * Return the bit that represents the given column.
     * @param column    The column of interest
     * @return          A long containing a single set bit. If this is the Nth
     *                  column, then the return value will be 1 << N
     */
    public final long bitForColumn(String column) { return bitForColumn.get(column); }
    
    /**
     * Return the index of the named column in the row.
     * @param column    The column of interest
     * @return          The index of this column as it was given in the
     *                  columnNames array when the RowDescriptor was created.
     */
    public final int indexOfColumn(String column) { return indexOfColumn.get(column); }
 }
