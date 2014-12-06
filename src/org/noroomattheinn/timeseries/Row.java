/*
 * Row.java - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Nov 25, 2014
 */
package org.noroomattheinn.timeseries;

import java.util.Arrays;

/**
 * Row: Represents a row of data collected at a given timestamp.
 * 
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class Row {
/*------------------------------------------------------------------------------
 *
 * Public State
 * 
 *----------------------------------------------------------------------------*/
    public long timestamp;
    public long bitVector;
    public double[] values;
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    /**
     * Create an empty Row. The timestamp is set to 0 and no columns are set. 
     * @param schema    A description of the Row 
     */
    public Row(RowDescriptor schema) { this(0L, 0L, schema.nColumns); }
    
    /**
     * Create an empty Row. The timestamp is set to 0 and no columns are set. 
     * @param schema    A description of the Row 
     * @param timestamp The timestamp for the row
     */
    public Row(RowDescriptor schema, long timestamp) { this(timestamp, 0L, schema.nColumns); }
    
    /**
     * Create a Row that's ready to have it's values set.
     * @param timestamp The Row's timestamp
     * @param bitVector The bit vector representing the columns that are to be set
     * @param nColumns  How many columns in the row
     */
    public Row(long timestamp, long bitVector, int nColumns) {
        this.timestamp = timestamp;
        this.bitVector = bitVector;
        this.values = new double[nColumns];
    }
    
    /**
     * Create a Row with initial values set.
     * @param timestamp The Row's timestamp
     * @param bitVector The bit vector representing the columns that are set
     * @param values    The initial values of the Row. These are copied.
     */
    public Row(long timestamp, long bitVector, double[] values) {
        this.timestamp = timestamp;
        this.bitVector = bitVector;
        this.values = Arrays.copyOf(values, values.length);
    }
    
    /**
     * Does this row include the column corresponding to the given bit
     * @param bit   Represents the column of interest
     * @return      true if the Row had a value set for the given column
     *              false otherwise
     */
    public boolean includes(long bit) { return (bitVector & bit) != 0; }
    
    /**
     * Does this row not include the column corresponding to the given bit
     * @param bit   Represents the column of interest
     * @return      true if the Row does not have a value set for the given column
     *              false otherwise
     */
    public boolean excludes(long bit) { return (bitVector & bit) == 0; }
    
    /**
     * Get the value for the named column
     * @param schema    A description of the row        
     * @param column    The name of the column in question
     * @return          The value of the named column
     */
    public double get(RowDescriptor schema, String column) {
        return values[schema.indexOfColumn(column)];
    }

    /**
     * Set the value of the named column. If the specified value isNaN or
     * isInfinite, the value is not set.
     * @param schema    A description of the row        
     * @param column    The name of the column in question
     * @param value     The value to be set for the named column
     */
    public void set(RowDescriptor schema, String column, double value) {
        if (Double.isInfinite(value) || Double.isNaN(value)) return;
        values[schema.indexOfColumn(column)] = value;
        bitVector |= schema.bitForColumn(column);
    }
    
    /**
     * Ensure that the column associated with the given bit is not marked as
     * included in this row.
     */
    public void clear(long bitForColumn) {
        bitVector = bitVector & ~bitForColumn;
    }

    /**
     * Merge the values from a Row into this Row. The timestamp of this
     * row is not changed. This row's bit vector is updated.
     * @param r     The Row whose values will be merged into this Row
     */
    public void mergeWith(Row r) {
        long bit = 1;
        for (int i = 0; i < r.values.length; i++) {
            if (r.includes(bit)) {
                values[i] = r.values[i];
                bitVector |= bit;
            }
            bit = bit << 1;
        }
    }
    
    /**
     * Given a bit specifying a column, return the associated column index
     * @param bit   Represents the column of interest
     * @return      The index of the column associated with that bit
     */
    public static int indexForBit(long bit) { return Long.numberOfTrailingZeros(bit); }

    @Override public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{ts: "); sb.append(timestamp);
        sb.append(", bv: 0x"); sb.append(Long.toHexString(bitVector));
        sb.append(", [");
        long bit = 1;
        for (int i = 0; i < values.length; i++) {
            if (i != 0) sb.append(", ");
            if ((bit & bitVector) != 0) {
                sb.append(values[i]);
            } else {
                sb.append("("); sb.append(values[i]); sb.append(")");
            }
            bit = bit << 1;
        }
        sb.append("]");
        return sb.toString();
    }
}
