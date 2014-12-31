/*
 * VTVehicle - Copyright(c) 2014 Joe Pasqua
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
 * VTVehicle: These methods are logically an extension to the Vehicle object.
 * If only we could dynamically subclass!
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class VTVehicle {

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
    
    public static Options.RoofType roofType() {
        Options.RoofType roof = overrideRoof.get(Prefs.get().overideRoofTo.get());
        if (Prefs.get().overideRoofActive.get() && roof != null) return roof;
        return (AppContext.get().vehicle.getOptions().roofType());
    }
    
    public static Options.PaintColor paintColor() {
        Options.PaintColor color = overrideColor.get(Prefs.get().overideColorTo.get());
        if (Prefs.get().overideColorActive.get() && color != null) return color;
        return (AppContext.get().vehicle.getOptions().paintColor());
    }
    
    
    public static boolean useDegreesF() {
        Utils.UnitType units = overrideUnits.get(Prefs.get().overideUnitsTo.get());
        if (Prefs.get().overideUnitsActive.get() && units != null)
            return units == Utils.UnitType.Imperial;
        return AppContext.get().lastGUIState.get().temperatureUnits.equalsIgnoreCase("F");

    }
    
    public static Utils.UnitType unitType() {
        Utils.UnitType units = overrideUnits.get(Prefs.get().overideUnitsTo.get());
        if (Prefs.get().overideUnitsActive.get() && units != null) return units;

        GUIState gs = AppContext.get().lastGUIState.get();
        if (gs != null) {
            return gs.distanceUnits.equalsIgnoreCase("mi/hr")
                    ? Utils.UnitType.Imperial : Utils.UnitType.Metric;
        }
        return Utils.UnitType.Imperial;
    }

    public static double inProperUnits(double val) {
        if (unitType() == Utils.UnitType.Imperial) return val;
        return Utils.milesToKm(val);
    }

    public static Options.WheelType computedWheelType() {
        Options.WheelType wt = overrideWheels.get(Prefs.get().overideWheelsTo.get());
        if (Prefs.get().overideWheelsActive.get() && wt != null) return wt;

        wt = AppContext.get().vehicle.getOptions().wheelType();
        VehicleState vs = AppContext.get().lastVehicleState.get();
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
    
    public static void waitForWakeup(final Runnable r, final BooleanProperty forceWakeup) {
        final long TestSleepInterval = 5 * 60 * 1000;   // 5 Minutes
        
        final Utils.Predicate p = new Utils.Predicate() {
            @Override public boolean eval() {
                return ThreadManager.get().shuttingDown() || (forceWakeup != null && forceWakeup.get());
            }
        };

        Runnable poller = new Runnable() {
            @Override public void run() {
                while (AppContext.get().vehicle.isAsleep()) {
                    Utils.sleep(TestSleepInterval, p);
                    if (ThreadManager.get().shuttingDown()) return;
                    if (forceWakeup != null && forceWakeup.get()) {
                        forceWakeup.set(false);
                        if (r != null) Platform.runLater(r);
                    }
                }
            }
        };
        
        ThreadManager.get().launch(poller, "Wait For Wakeup");
    }

    public static boolean forceWakeup() {
        ChargeState charge;
        for (int i = 0; i < 15; i++) {
            if ((charge = AppContext.get().vehicle.queryCharge()).valid) {
                AppContext.get().noteUpdatedState(charge);
                return true;
            }
            AppContext.get().vehicle.wakeUp();
            ThreadManager.get().sleep(5 * 1000);
        }
        return false;
    }
    
}
