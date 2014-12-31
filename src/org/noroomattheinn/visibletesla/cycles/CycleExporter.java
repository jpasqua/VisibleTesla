/*
 * CycleExporter.java - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Oct 23, 2014
 */
package org.noroomattheinn.visibletesla.cycles;

import com.google.common.collect.Range;
import java.io.File;
import java.io.IOException;
import java.util.List;
import javafx.beans.property.BooleanProperty;
import javafx.scene.control.Dialogs;
import javafx.stage.FileChooser;
import jxl.Workbook;
import jxl.write.WritableCellFormat;
import jxl.write.WritableFont;
import jxl.write.WritableSheet;
import jxl.write.WritableWorkbook;
import jxl.write.WriteException;
import org.noroomattheinn.visibletesla.AppContext;
import org.noroomattheinn.visibletesla.StatsCollector;
import static org.noroomattheinn.tesla.Tesla.logger;
import org.noroomattheinn.visibletesla.Prefs;
import org.noroomattheinn.visibletesla.dialogs.DateRangeDialog;

/**
 * CycleExporter: Does most of the heavy lifting of exporting Cycles. Subclasses
 * implement a few methods to make it all work.
 * 
 * Notes:
 * + jxl.WritableCellFormat can't be static. Once they are used they are bound
 *   to a sheet and can't be used in other sheets. It's tempting to want to make
 *   these static or even instance variables, but new instances need to be 
 *   created at each doExport() call.
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public abstract class CycleExporter<C extends BaseCycle> {
/*------------------------------------------------------------------------------
 *
 * Constants, Enums, and Types
 * 
 *----------------------------------------------------------------------------*/
    
    protected static class StandardFormats {
        final WritableCellFormat headerFormat;
        final WritableCellFormat standardFormat;
        final WritableCellFormat dateFormat;
        
        StandardFormats() {
            WritableFont stdFont = new WritableFont(WritableFont.ARIAL, 12);
            standardFormat = new WritableCellFormat(stdFont);
            headerFormat = new WritableCellFormat(
                new WritableFont(WritableFont.ARIAL, 12, WritableFont.BOLD));
            dateFormat = new jxl.write.WritableCellFormat(
                new jxl.write.DateFormat("M/d/yy H:mm:ss"));
            dateFormat.setFont(stdFont);
        }

    }
    
    private static final String VTDataAddress = "data@visibletesla.com";

/*------------------------------------------------------------------------------
 *
 * Internal  State
 * 
 *----------------------------------------------------------------------------*/
    
    protected final AppContext ac;
    protected final String cycleType;
    protected final String[] columns;
    protected final BooleanProperty submitData;
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    /**
     * Create a new CycleExporter
     * @param appContext    The application context
     * @param cycleType     The name of this type of Cycle (eg Charge or Rest)
     * @param columns       The names of the columns of data being exported
     * @param submitData    A property that indicates whether to submit anonymous
     *                      data for this cycle type
     */
    public CycleExporter(AppContext appContext, String cycleType, String[] columns,
                         BooleanProperty submitData) {
        this.ac = appContext;
        this.cycleType = cycleType;
        this.columns = columns;
        this.submitData = submitData;
    }
    
    /**
     * Export a set of Cycle data to an Excel file.
     * @param provider  An object that can provide a list of Cycles for a 
     *                  specified range of times.
     */
    public void export(CycleStore<C> provider) {        
        String initialDir = Prefs.store().get(
                StatsCollector.LastExportDirKey, System.getProperty("user.home"));
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export " + cycleType + " Data");
        fileChooser.setInitialDirectory(new File(initialDir));

        File file = fileChooser.showSaveDialog(ac.stage);
        if (file != null) {
            String enclosingDirectory = file.getParent();
            if (enclosingDirectory != null)
                Prefs.store().put(StatsCollector.LastExportDirKey, enclosingDirectory);
            Range<Long> exportPeriod = DateRangeDialog.getExportPeriod(ac.stage);
            if (exportPeriod == null)
                return;
            List<C> cycles = provider.getCycles(exportPeriod);
            if (doExport(file, cycles)) {
                Dialogs.showInformationDialog(
                        ac.stage, "Your data has been exported",
                        "Data Export Process" , "Export Complete");
            } else {
                Dialogs.showErrorDialog(
                        ac.stage, "Unable to save to: " + file,
                        "Data Export Process" , "Export Failed");
            }
        }
    }
    
    /**
     * Submit a Cycle anonymously to a central repository
     * @param cycle The Cycle to be submitted
     */
    public void submitData(C cycle) {
        if (!submitData.get()) return;
        ditherLocation(cycle);
        String jsonRep = cycle.toJSONString();
        
        jsonRep = filterSubmissionData(jsonRep);
        
        // Send the notification and log the body
        String subject = cycleType + " Data Submission";
        ac.mailer.send(VTDataAddress, subject, jsonRep);
        logger.info(subject + ": " + jsonRep);
    }
    
/*------------------------------------------------------------------------------
 *
 * Protected methods that may (or must) be overridden by subclasses
 * 
 *----------------------------------------------------------------------------*/
    
    /**
     * Add a row of header information for an exported Excel spreadsheet. This
     * is normally not overridden.
     * @param sheet     The sheet in question
     * @param row       The row number of where to put the header row
     * @param sf        Standard Excel formats that can be shared across the export
     * @throws WriteException 
     */
    protected void addTableHeader(WritableSheet sheet, int row, StandardFormats sf)
            throws WriteException {
        for (int column = 0; column < columns.length; column++) {
            String label = columns[column];
            sheet.setColumnView(column, label.length()+3);
            sheet.addCell(new jxl.write.Label(column, row, label, sf.headerFormat));
        }
        
        // Make the header row stationary
        sheet.getSettings().setVerticalFreeze(1);

    }
    
    /**
     * Emit a row of data, appropriately formatted, given the input Cycle
     * @param sheet     The sheet that will contain the row
     * @param row       The row number
     * @param cycle     The cycle to be written to the row
     * @param sf        Standard Excel formats that can be shared across the export
     * @throws WriteException 
     */
    protected abstract void emitRow(WritableSheet sheet, int row, C cycle, StandardFormats sf)
            throws WriteException;
    
    /**
     * This method provides an opportunity to filter (or add to) the data being
     * submitted anonymously about a Cycle. If this method is not overridden,
     * then no filtering will be performed.
     * @param data  The data to be submitted
     * @return      A filtered version of the data to be submitted
     */
    protected String filterSubmissionData(String data) { return data; }
    
    /**
     * Randomize (dither) the location of this cycle for privacy purposes.
     * @param cycle     The cycle whose location should be dithered.
     */
    protected void ditherLocation(C cycle) {
        if (!Prefs.get().includeLocData.get()) { cycle.lat = cycle.lng = 0; return; }
        if (cycle.lat == 0 && cycle.lng == 0) return;
        
        double random, offset;
        double ditherAmt = Prefs.get().ditherLocAmt.get();
        double pow = Math.pow(10, ditherAmt);       // 10^ditherAmt
        
        random = 0.5 + (Math.random()/2);           // value in [0.5, 1]
        offset = (random/pow) * (Math.random() > 0.5 ? -1 : 1);
        cycle.lat += offset;

        random = 0.5 + (Math.random()/2);           // value in [0.5, 1]
        offset = (random/pow) * (Math.random() > 0.5 ? -1 : 1);
        cycle.lng += offset;
    }
    

/*------------------------------------------------------------------------------
 *
 * Private Utility Methods
 * 
 *----------------------------------------------------------------------------*/
    
    /**
     * The standard process of exporting Cycles to a file
     * @param file      The file to export to
     * @param cycles    The list of cycles to export
     * @return 
     */
    protected boolean doExport(File file, List<C> cycles) {
        StandardFormats sf = new StandardFormats();
        try {
            WritableWorkbook workbook = Workbook.createWorkbook(file);
            WritableSheet sheet = workbook.createSheet("Sheet1", 0);
            
            int row = 0;
            addTableHeader(sheet, row++, sf);
            for (C cycle : cycles) {
                emitRow(sheet, row++, cycle, sf);
            }
            workbook.write();
            workbook.close();
            return true;
        } catch (IOException | WriteException ex) {
            return false;
        }
    }
    
}
