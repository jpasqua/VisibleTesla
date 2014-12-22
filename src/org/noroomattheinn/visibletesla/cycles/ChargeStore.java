/*
 * ChargeStore.java - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Oct 23, 2014
 */
package org.noroomattheinn.visibletesla.cycles;

import java.io.FileNotFoundException;
import java.util.Date;
import jxl.write.WritableSheet;
import jxl.write.WriteException;
import org.apache.commons.lang3.StringUtils;
import org.noroomattheinn.visibletesla.AppContext;
import org.noroomattheinn.visibletesla.Prefs;



/**
 * ChargeStore: Manage persistent storage for Charge Cycle information.
 * 
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class ChargeStore extends CycleStore<ChargeCycle> {
/*------------------------------------------------------------------------------
 *
 * Internal  State
 * 
 *----------------------------------------------------------------------------*/
    
    private final ChargeCycleExporter exporter;
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public ChargeStore(AppContext appContext) throws FileNotFoundException {
        super(appContext, "charge", ChargeCycle.class);
        this.exporter = new ChargeCycleExporter(ac);
        
        ac.lastChargeCycle.addTracker(false, new Runnable() {
            @Override public void run() {
                cycleWriter.println(ac.lastChargeCycle.get().toJSONString());
                exporter.submitData(ac.lastChargeCycle.get());
            }
        });
    }
    
    public void export() { exporter.export(this); }
}

/*------------------------------------------------------------------------------
 *
 * The CycleExporter for Charge Cycles
 * 
 *----------------------------------------------------------------------------*/

class ChargeCycleExporter extends CycleExporter<ChargeCycle> {
    private static final String[] labels = {
            "Start Date/Time", "Ending Date/Time", "Supercharger?", "Phases", "Start Range",
            "End Range", "Start SOC", "End SOC", "(Latitude, ", " Longitude)", "Odometer",
            "Peak V", "Avg V", "Peak I", "Avg I", "Energy"};

    ChargeCycleExporter(AppContext appContext) { 
        super(appContext, "Charge", labels, Prefs.get().submitAnonCharge);
    }
    
    @Override protected void emitRow(
            WritableSheet sheet, int row, ChargeCycle cycle, StandardFormats sf)
            throws WriteException {
        int column = 0;
        sheet.addCell(new jxl.write.DateTime(column++, row, new Date(cycle.startTime), sf.dateFormat));
        sheet.addCell(new jxl.write.DateTime(column++, row, new Date(cycle.endTime), sf.dateFormat));
        sheet.addCell(new jxl.write.Boolean(column++, row, cycle.superCharger, sf.standardFormat));
        sheet.addCell(new jxl.write.Number(column++, row, cycle.phases, sf.standardFormat));
        sheet.addCell(new jxl.write.Number(column++, row, cycle.startRange, sf.standardFormat));
        sheet.addCell(new jxl.write.Number(column++, row, cycle.endRange, sf.standardFormat));
        sheet.addCell(new jxl.write.Number(column++, row, cycle.startSOC, sf.standardFormat));
        sheet.addCell(new jxl.write.Number(column++, row, cycle.endSOC, sf.standardFormat));
        sheet.addCell(new jxl.write.Number(column++, row, cycle.lat, sf.standardFormat));
        sheet.addCell(new jxl.write.Number(column++, row, cycle.lng, sf.standardFormat));
        sheet.addCell(new jxl.write.Number(column++, row, cycle.odometer, sf.standardFormat));
        sheet.addCell(new jxl.write.Number(column++, row, cycle.peakVoltage, sf.standardFormat));
        sheet.addCell(new jxl.write.Number(column++, row, cycle.avgVoltage, sf.standardFormat));
        sheet.addCell(new jxl.write.Number(column++, row, cycle.peakCurrent, sf.standardFormat));
        sheet.addCell(new jxl.write.Number(column++, row, cycle.avgCurrent, sf.standardFormat));
        sheet.addCell(new jxl.write.Number(column++, row, cycle.energyAdded, sf.standardFormat));
    }
    
    @Override protected String filterSubmissionData(String jsonRep) {
        // Strip the closing curly to prepare to add more fields
        jsonRep = StringUtils.substringBefore(jsonRep, "}");
        // Concatenate the extra fields and put back the closing curly
        return String.format("%s, \"battery\": \"%s\", \"uuid\": \"%s\" }", 
                jsonRep, ac.vehicle.getOptions().batteryType(), ac.vehicle.getUUID());
    }
    
    @Override protected void ditherLocation(ChargeCycle cycle) {
        if (cycle.superCharger && Prefs.get().includeLocData.get()) return;
        super.ditherLocation(cycle);
    }
}
