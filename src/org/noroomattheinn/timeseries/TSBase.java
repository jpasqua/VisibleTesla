/*
 * TSBase.java - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Nov 25, 2014
 */
package org.noroomattheinn.timeseries;

import com.google.common.collect.Range;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;
import jxl.Workbook;
import jxl.format.Colour;
import jxl.write.DateFormat;
import jxl.write.Label;
import jxl.write.NumberFormats;
import jxl.write.WritableCellFormat;
import jxl.write.WritableSheet;
import jxl.write.WritableWorkbook;
import jxl.write.WriteException;

/**
 * TSBase: Simple base class for all TimeSeries implementations.
 * 
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public abstract class TSBase implements TimeSeries {
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
    
    protected final RowDescriptor schema;
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    TSBase(RowDescriptor rd) {
        this.schema = rd;
    }
    
/*------------------------------------------------------------------------------
 *
 * Methods overriden from TimeSeries
 * 
 *----------------------------------------------------------------------------*/
    
    @Override public synchronized void loadInto(final TimeSeries ts, Range<Long> period) {
        streamRows(period, new RowCollector() {
            @Override public boolean collect(Row r) {
                ts.storeRow(r);
                return true;
            }
        });
    }
    
    @Override public synchronized void streamValues(
            Range<Long> period, final ValueCollector collector) {
        streamRows(period, new RowCollector() {
            @Override public boolean collect(Row r) {
                long timestamp = r.timestamp;
                for (String c:schema.columnNames) {
                    long bit = schema.bitForColumn(c);
                    if (r.includes(bit)) {
                        collector.collect(timestamp, c, r.values[Row.indexForBit(bit)]);
                    }
                }
                return true;
            }
        });
    }

    @Override public boolean export(
            File toFile, Range<Long> exportPeriod,
            List<String> columns, boolean includeDerived) {
        if (columns == null) columns = new ArrayList<>(Arrays.asList(schema.columnNames));
        
        try {
            final WritableWorkbook workbook = Workbook.createWorkbook(toFile);
            final WritableSheet sheet = workbook.createSheet("Sheet1", 0);
            addTableHeader(sheet, columns);

            // Run through the table and add each row...
            streamRows(exportPeriod, new RowHandler(sheet, 1, columns, includeDerived));
            
            workbook.write(); workbook.close();
            return true;
        } catch (IOException | WriteException ex) {
            logger.warning("Failure exporting repo: " + ex);
            return false;
        } 
    }
    
    @Override public RowDescriptor getSchema() { return schema; }
    
/*------------------------------------------------------------------------------
 *
 * PRIVATE - Support code for exporting to Excel format
 * 
 *----------------------------------------------------------------------------*/

    
    private class RowHandler implements RowCollector {
        // Don't make these static!
        final WritableCellFormat dateFormat = new WritableCellFormat(new DateFormat("m/d/yy hh:mm:ss"));
        final WritableCellFormat integerFormat = new WritableCellFormat(NumberFormats.INTEGER); 
        final WritableCellFormat lightGrayCell = new WritableCellFormat();

        private final WritableSheet sheet;
        private final boolean includeDerived;
        private final List<String> columns;
        private final int nColumnsToInclude;
        private final long columnsIncluded;
        private int rNum;
        
        RowHandler(
                WritableSheet sheet, int initialRow,
                List<String>columns, boolean includeDerived) {
            this.sheet = sheet;
            this.rNum = initialRow;
            this.columns = columns;
            this.nColumnsToInclude = columns.size();
            this.includeDerived = includeDerived;
            this.columnsIncluded = bitVectorForColumns();
            
            try { lightGrayCell.setBackground(Colour.GREY_25_PERCENT); }
            catch (WriteException ex) { logger.warning("Can't Happen: " + ex); }
        }

        @Override public boolean collect(Row row) {
            // Don't bother with rows that have only derived values
            if ((row.bitVector & columnsIncluded) == 0L) return true;
            
            try {
                jxl.write.Number timeCell = new jxl.write.Number(
                        0, rNum, row.timestamp, integerFormat);
                sheet.addCell(timeCell);

                int cNum = 1;
                for (String c:schema.columnNames) {
                    if (columns.contains(c)) {
                        long bit = schema.bitForColumn(c);
                        int valueIndex = Row.indexForBit(bit);
                        boolean derived = row.excludes(bit);
                        double val = (!derived || includeDerived) ? row.values[valueIndex] : 0;
                        jxl.write.Number cell = new jxl.write.Number(cNum, rNum, val);
                        sheet.addCell(cell);
                        if (derived) { cell.setCellFormat(lightGrayCell); }
                        cNum++;
                    }
                }
                sheet.addCell(new jxl.write.DateTime(
                        nColumnsToInclude+1, rNum,
                        new Date(row.timestamp), dateFormat));

                rNum++;
                return true;
            } catch (WriteException e) {
                logger.warning("Export failed with: " + e);
                return false;
            }
        }
        
        private long bitVectorForColumns() {
            long bitVector = 0;
            for (String c:columns) {
                bitVector |= schema.bitForColumn(c);
            }
            return bitVector;
        }
    }
    
    
    
    private void addTableHeader(WritableSheet sheet, List<String> columns) throws WriteException {
        // Start with the timestamp column
        sheet.addCell(new Label(0, 0, "Timestamp"));
        sheet.setColumnView(0, 14); // Big enough for a timestamp;

        // Now handle the data columns
        int columnNumber = 1;
        for (int i = 0; i < schema.nColumns; i++) {
            String c = schema.columnNames[i];
            if (columns.contains(c)) {
                sheet.addCell(new Label(columnNumber++, 0, c));
            }
        }

        // Now add the Date column
        int dateColumn = columns.size() + 1;
        sheet.addCell(new Label(dateColumn, 0, "Date"));
        sheet.setColumnView(dateColumn, 16); // Big enough for a Date string;

        // Make the header row stationary
        sheet.getSettings().setVerticalFreeze(1);
    }

}
