/*
 * ChargeStore.java - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Oct 23, 2014
 */
package org.noroomattheinn.visibletesla;

import com.google.common.collect.Range;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.Dialogs;
import javafx.stage.FileChooser;
import jxl.Workbook;
import jxl.write.WritableCellFormat;
import jxl.write.WritableSheet;
import jxl.write.WritableWorkbook;
import jxl.write.WriteException;
import org.noroomattheinn.tesla.Tesla;
import static org.noroomattheinn.visibletesla.DataStore.LastExportDirKey;
import org.noroomattheinn.visibletesla.dialogs.DateRangeDialog;
import us.monoid.json.JSONException;

/**
 * ChargeStore: Manage persistent storage for Charge Cycle information.
 * 
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class ChargeStore implements ThreadManager.Stoppable {
/*------------------------------------------------------------------------------
 *
 * Internal  State
 * 
 *----------------------------------------------------------------------------*/
    
    private final AppContext ac;
    private final File chargeFile;
    private final PrintStream chargeWriter;
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public ChargeStore(AppContext ac, File chargeFile) throws FileNotFoundException {
        this.ac = ac;
        this.chargeFile = chargeFile;
        
        FileOutputStream fos = new FileOutputStream(chargeFile, true);
        chargeWriter = new PrintStream(fos);
        
        ac.lastChargeCycle.addListener(new ChangeListener<ChargeMonitor.Cycle>() {
            @Override  public void changed(
                    ObservableValue<? extends ChargeMonitor.Cycle> ov,
                    ChargeMonitor.Cycle t, ChargeMonitor.Cycle cycle) {
                chargeWriter.println(cycle.toJSON());
            }
        });
        
        ac.tm.addStoppable((ThreadManager.Stoppable)this);
    }
   
    public List<ChargeMonitor.Cycle> loadCharges(Range<Long> period) {
        List<ChargeMonitor.Cycle> charges = new ArrayList<>();
        try {
            BufferedReader r = new BufferedReader(new FileReader(chargeFile));

            try {
                String entry;
                while ((entry = r.readLine()) != null) {
                    try {
                        ChargeMonitor.Cycle cycle = new ChargeMonitor.Cycle(entry);
                        if (period == null || period.contains(cycle.startTime)) {
                            charges.add(cycle);
                        }
                    } catch (JSONException ex) {
                        Tesla.logger.warning("Malformed Charge Cycle data: " + ex);
                    }
                }
            } catch (IOException ex) {
                Tesla.logger.warning("Problem reading Charge Cycle data: " + ex);
            }
        } catch (FileNotFoundException ex) {
            Tesla.logger.warning("Could not open Charge file: " + ex);
        }
        return charges;
    }
    
    public void exportCSV() {        
        String initialDir = ac.persistentState.get(
                LastExportDirKey, System.getProperty("user.home"));
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Charge Data");
        fileChooser.setInitialDirectory(new File(initialDir));

        File file = fileChooser.showSaveDialog(ac.stage);
        if (file != null) {
            String enclosingDirectory = file.getParent();
            if (enclosingDirectory != null)
                ac.persistentState.put(LastExportDirKey, enclosingDirectory);
            Range<Long> exportPeriod = DateRangeDialog.getExportPeriod(ac.stage);
            if (exportPeriod == null)
                return;
            export(file, exportPeriod);
        }
    }
    
    @Override public void stop() { chargeWriter.close(); }
    
/*------------------------------------------------------------------------------
 *
 * PRIVATE Methods for writing to an excel file
 * 
 *----------------------------------------------------------------------------*/
    
    
    private void export(File file, Range<Long> exportPeriod) {
        List<ChargeMonitor.Cycle> charges = loadCharges(exportPeriod);

        try {
            WritableWorkbook workbook = Workbook.createWorkbook(file);
            WritableSheet sheet = workbook.createSheet("Sheet1", 0);
            
            int row = 1;
            addTableHeader(sheet, row++);
            for (ChargeMonitor.Cycle cycle : charges) {
                emitRow(sheet, row++, cycle);
            }
            workbook.write();
            workbook.close();

            Dialogs.showInformationDialog(
                    ac.stage, "Your data has been exported",
                    "Data Export Process" , "Export Complete");
        } catch (IOException | WriteException ex) {
            Dialogs.showErrorDialog(
                    ac.stage, "Unable to save to: " + file,
                    "Data Export Process" , "Export Failed");
        }
        
    }
    
    private static final WritableCellFormat dateFormat =
            new jxl.write.WritableCellFormat(new jxl.write.DateFormat("M/d/yy H:mm:ss"));

    private void emitRow(WritableSheet sheet, int row, ChargeMonitor.Cycle cycle)
            throws WriteException {
        int column = 0;
        sheet.addCell(new jxl.write.DateTime(column++, row, new Date(cycle.startTime), dateFormat));
        sheet.addCell(new jxl.write.DateTime(column++, row, new Date(cycle.endTime), dateFormat));
        sheet.addCell(new jxl.write.Boolean(column++, row, cycle.superCharger));
        sheet.addCell(new jxl.write.Number(column++, row, cycle.phases));
        sheet.addCell(new jxl.write.Number(column++, row, cycle.startRange));
        sheet.addCell(new jxl.write.Number(column++, row, cycle.endRange));
        sheet.addCell(new jxl.write.Number(column++, row, cycle.startSOC));
        sheet.addCell(new jxl.write.Number(column++, row, cycle.endSOC));
        sheet.addCell(new jxl.write.Number(column++, row, cycle.lat));
        sheet.addCell(new jxl.write.Number(column++, row, cycle.lng));
        sheet.addCell(new jxl.write.Number(column++, row, cycle.odo));
        sheet.addCell(new jxl.write.Number(column++, row, cycle.peakVoltage));
        sheet.addCell(new jxl.write.Number(column++, row, cycle.avgVoltage));
        sheet.addCell(new jxl.write.Number(column++, row, cycle.peakCurrent));
        sheet.addCell(new jxl.write.Number(column++, row, cycle.avgCurrent));
        sheet.addCell(new jxl.write.Number(column++, row, cycle.energyAdded));
    }
    
    private void addTableHeader(WritableSheet sheet, int row) throws WriteException {
        int column = 0;
        sheet.addCell(new jxl.write.Label(column++, row, "Start Time"));
        sheet.addCell(new jxl.write.Label(column++, row, "End Time"));
        sheet.addCell(new jxl.write.Label(column++, row, "Supercharger?"));
        sheet.addCell(new jxl.write.Label(column++, row, "Phases"));
        sheet.addCell(new jxl.write.Label(column++, row, "Start Range"));
        sheet.addCell(new jxl.write.Label(column++, row, "End Range"));
        sheet.addCell(new jxl.write.Label(column++, row, "Start SOC"));
        sheet.addCell(new jxl.write.Label(column++, row, "End SOC"));
        sheet.addCell(new jxl.write.Label(column++, row, "Lat"));
        sheet.addCell(new jxl.write.Label(column++, row, "Lng"));
        sheet.addCell(new jxl.write.Label(column++, row, "Odometer"));
        sheet.addCell(new jxl.write.Label(column++, row, "Peak V"));
        sheet.addCell(new jxl.write.Label(column++, row, "Avg V"));
        sheet.addCell(new jxl.write.Label(column++, row, "Peak I"));
        sheet.addCell(new jxl.write.Label(column++, row, "Avg I"));
        sheet.addCell(new jxl.write.Label(column++, row, "Energy"));
        //sheet.setColumnView(0, 12); // Big enough for a timestamp;
        
        // Make the header row stationary
        sheet.getSettings().setVerticalFreeze(1);
    }
}
