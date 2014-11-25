/*
 * VTExtras - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Nov 20, 2014
 */
package org.noroomattheinn.visibletesla;

import java.util.Map;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import org.noroomattheinn.tesla.ChargeState;
import org.noroomattheinn.tesla.GUIState;
import org.noroomattheinn.tesla.Options;
import static org.noroomattheinn.tesla.Tesla.logger;
import org.noroomattheinn.tesla.VehicleState;
import org.noroomattheinn.utils.Utils;

/**
 * VTExtras: These methods are logically an extension to the Vehicle object.
 * If only we could dynamically subclass!
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class VTExtras {

/*------------------------------------------------------------------------------
 *
 * Constants and Enums
 * 
 *----------------------------------------------------------------------------*/
   
    private static final Map<String,Options.RoofType> overrideRoof = Utils.newHashMap(
        "Body Color", Options.RoofType.RFBC,
        "Black", Options.RoofType.RFBK,
        "Pano", Options.RoofType.RFPO);
    private static final Map<String,Options.PaintColor> overrideColor = Utils.newHashMap(
        "White", Options.PaintColor.PBCW,
        "Black", Options.PaintColor.PBSB,
        "Brown", Options.PaintColor.PMAB,
        "Blue", Options.PaintColor.PMMB,
        "Green", Options.PaintColor.PMSG,
        "Silver", Options.PaintColor.PMSS,
        "Grey", Options.PaintColor.PMTG,
        "Steel Grey", Options.PaintColor.PMNG,
        "Red", Options.PaintColor.PPMR,
        "Sig. Red", Options.PaintColor.PPSR,
        "Pearl", Options.PaintColor.PPSW);
    private static final Map<String,Options.WheelType> overrideWheels = Utils.newHashMap(
        "19\" Silver", Options.WheelType.WT19,
        "19\" Aero", Options.WheelType.WTAE,
        "19\" Turbine", Options.WheelType.WTTB,
        "19\" Turbine Grey", Options.WheelType.WTTG,
        "21\" Silver", Options.WheelType.WT21,
        "21\" Grey", Options.WheelType.WTSP);
    private static final Map<String,Utils.UnitType> overrideUnits = Utils.newHashMap(
            "Imperial", Utils.UnitType.Imperial,
            "Metric", Utils.UnitType.Metric);
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public static Options.RoofType roofType(AppContext ac) {
        Options.RoofType roof = overrideRoof.get(ac.prefs.overideRoofTo.get());
        if (ac.prefs.overideRoofActive.get() && roof != null) return roof;
        return (ac.vehicle.getOptions().roofType());
    }
    
    public static Options.PaintColor paintColor(AppContext ac) {
        Options.PaintColor color = overrideColor.get(ac.prefs.overideColorTo.get());
        if (ac.prefs.overideColorActive.get() && color != null) return color;
        return (ac.vehicle.getOptions().paintColor());
    }
    
    
    public static boolean useDegreesF(AppContext ac) {
        Utils.UnitType units = overrideUnits.get(ac.prefs.overideUnitsTo.get());
        if (ac.prefs.overideUnitsActive.get() && units != null)
            return units == Utils.UnitType.Imperial;
        return ac.lastGUIState.get().temperatureUnits.equalsIgnoreCase("F");

    }
    
    public static Utils.UnitType unitType(AppContext ac) {
        Utils.UnitType units = overrideUnits.get(ac.prefs.overideUnitsTo.get());
        if (ac.prefs.overideUnitsActive.get() && units != null) return units;

        GUIState gs = ac.lastGUIState.get();
        if (gs != null) {
            return gs.distanceUnits.equalsIgnoreCase("mi/hr")
                    ? Utils.UnitType.Imperial : Utils.UnitType.Metric;
        }
        return Utils.UnitType.Imperial;
    }

    public static double inProperUnits(AppContext ac, double val) {
        if (unitType(ac) == Utils.UnitType.Imperial) return val;
        return Utils.mToK(val);
    }

    public static Options.WheelType computedWheelType(AppContext ac) {
        Options.WheelType wt = overrideWheels.get(ac.prefs.overideWheelsTo.get());
        if (ac.prefs.overideWheelsActive.get() && wt != null) return wt;

        wt = ac.vehicle.getOptions().wheelType();
        VehicleState vs = ac.lastVehicleState.get();
        if (vs.wheelType != null) {
            // Check for known override wheel types, right now that's just Aero19
            switch (vs.wheelType) {
                case "Aero19": wt = Options.WheelType.WTAE; break;
                case "Base19": wt = Options.WheelType.WT19; break;
                case "Super21Gray": wt = Options.WheelType.WTSG; break;
                default: logger.info("Unknown WheelType: " + vs.wheelType); break;
            }
        }
        return wt;
    }
    
    public static void waitForVehicleToWake(
            final AppContext ac, final Runnable r, final BooleanProperty forceWakeup) {
        final long TestSleepInterval = 5 * 60 * 1000;   // 5 Minutes
        
        final Utils.Predicate p = new Utils.Predicate() {
            @Override public boolean eval() {
                return ac.shuttingDown.get() || (forceWakeup != null && forceWakeup.get());
            }
        };

        Runnable poller = new Runnable() {
            @Override public void run() {
                while (ac.vehicle.isAsleep()) {
                    Utils.sleep(TestSleepInterval, p);
                    if (ac.shuttingDown.get()) return;
                    if (forceWakeup != null && forceWakeup.get()) {
                        forceWakeup.set(false);
                        if (r != null) Platform.runLater(r);
                    }
                }
            }
        };
        
        ac.tm.launch(poller, "Wait For Wakeup");
    }

    public static boolean forceWakeup(AppContext ac) {
        ChargeState charge;
        for (int i = 0; i < 15; i++) {
            if ((charge = ac.vehicle.queryCharge()).valid) {
                ac.noteUpdatedState(charge);
                return true;
            }
            ac.vehicle.wakeUp();
            sleep(ac, 5 * 1000);
        }
        return false;
    }
    
    public static void sleep(final AppContext ac, long timeInMillis) {
        Utils.sleep(timeInMillis,  new Utils.Predicate() {
            @Override public boolean eval() { return ac.shuttingDown.get(); } });
    }
}
