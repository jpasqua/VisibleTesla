/*
 * ChargeStore.java - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Oct 23, 2014
 */
package org.noroomattheinn.visibletesla.data;

import com.google.common.collect.Range;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Date;
import jxl.write.WritableSheet;
import jxl.write.WriteException;
import org.apache.commons.lang3.StringUtils;
import org.noroomattheinn.tesla.Options;
import org.noroomattheinn.utils.TrackedObject;
import org.noroomattheinn.visibletesla.prefs.Prefs;
import org.noroomattheinn.visibletesla.vehicle.VTVehicle;


/**
 * ChargeStore: Manage persistent storage for Charge Cycle information.
 * 
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
class ChargeStore extends CycleStore<ChargeCycle> {
/*------------------------------------------------------------------------------
 *
 * Internal  State
 * 
 *----------------------------------------------------------------------------*/
    
    private final ChargeCycleExporter exporter;
    private final TrackedObject<ChargeCycle> lastChargeCycle;
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public ChargeStore(File container, VTVehicle v, TrackedObject<ChargeCycle> lastCycle)
            throws FileNotFoundException {
        super("charge", ChargeCycle.class, container, v);
        this.lastChargeCycle = lastCycle;
        this.exporter = new ChargeCycleExporter(
                v.getVehicle().getOptions().batteryType(),
                v.getVehicle().getUUID());
        
        lastChargeCycle.addTracker(new Runnable() {
            @Override public void run() {
                cycleWriter.println(lastChargeCycle.get().toJSONString());
                exporter.submitData(lastChargeCycle.get());
            }
        });
    }
    
    public boolean export(File toFile, Range<Long> exportPeriod) {
        return exporter.export(this, toFile, exportPeriod);
    }
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

    private final Options.BatteryType batteryType;
    private final String uuid;
    
    ChargeCycleExporter(Options.BatteryType batteryType, String uuid) { 
        super("Charge", labels, Prefs.get().submitAnonCharge);
        this.batteryType = batteryType;
        this.uuid = uuid;
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
                jsonRep, batteryType, uuid);
    }
    
    @Override protected void ditherLocation(ChargeCycle cycle) {
        if (cycle.superCharger && Prefs.get().includeLocData.get()) return;
        super.ditherLocation(cycle);
    }
}
