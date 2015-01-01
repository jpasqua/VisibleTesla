/*
 * VTVehicle - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Nov 20, 2014
 */
package org.noroomattheinn.visibletesla;

import java.util.Map;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.noroomattheinn.tesla.BaseState;
import org.noroomattheinn.tesla.ChargeState;
import org.noroomattheinn.tesla.DriveState;
import org.noroomattheinn.tesla.GUIState;
import org.noroomattheinn.tesla.HVACState;
import org.noroomattheinn.tesla.Options;
import org.noroomattheinn.tesla.StreamState;
import static org.noroomattheinn.tesla.Tesla.logger;
import org.noroomattheinn.tesla.Vehicle;
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

/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
   
    private static VTVehicle instance = null;
    private Vehicle vehicle;

/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public final ObjectProperty<ChargeState> chargeState;
    public final ObjectProperty<DriveState> driveState;
    public final ObjectProperty<VehicleState> vehicleState;
    public final ObjectProperty<HVACState> hvacState;
    public final ObjectProperty<GUIState> guiState;
    public final ObjectProperty<StreamState> streamState;
    
    public static VTVehicle create() {
        if (instance != null) return instance;
        return (instance = new VTVehicle());
    }
    
    public static VTVehicle get() { return instance; }
    
    public void setVehicle(Vehicle v) {
        vehicle = v;
        streamState.get().odometer = Prefs.store().getDouble(vinKey("odometer"), 0);
    }
    
    public Vehicle getVehicle() { return vehicle; }
    
    public Options.RoofType roofType() {
        Options.RoofType roof = overrideRoof.get(Prefs.get().overideRoofTo.get());
        if (Prefs.get().overideRoofActive.get() && roof != null) return roof;
        return (vehicle.getOptions().roofType());
    }
    
    public Options.PaintColor paintColor() {
        Options.PaintColor color = overrideColor.get(Prefs.get().overideColorTo.get());
        if (Prefs.get().overideColorActive.get() && color != null) return color;
        return (vehicle.getOptions().paintColor());
    }
    
    
    public boolean useDegreesF() {
        Utils.UnitType units = overrideUnits.get(Prefs.get().overideUnitsTo.get());
        if (Prefs.get().overideUnitsActive.get() && units != null)
            return units == Utils.UnitType.Imperial;
        return guiState.get().temperatureUnits.equalsIgnoreCase("F");

    }
    
    public Utils.UnitType unitType() {
        Utils.UnitType units = overrideUnits.get(Prefs.get().overideUnitsTo.get());
        if (Prefs.get().overideUnitsActive.get() && units != null) return units;

        GUIState gs = guiState.get();
        if (gs != null) {
            return gs.distanceUnits.equalsIgnoreCase("mi/hr")
                    ? Utils.UnitType.Imperial : Utils.UnitType.Metric;
        }
        return Utils.UnitType.Imperial;
    }

    public double inProperUnits(double val) {
        if (unitType() == Utils.UnitType.Imperial) return val;
        return Utils.milesToKm(val);
    }

    public Options.WheelType computedWheelType() {
        Options.WheelType wt = overrideWheels.get(Prefs.get().overideWheelsTo.get());
        if (Prefs.get().overideWheelsActive.get() && wt != null) return wt;

        wt = vehicle.getOptions().wheelType();
        VehicleState vs = vehicleState.get();
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
    
    public void waitForWakeup(final Runnable r, final BooleanProperty forceWakeup) {
        final long TestSleepInterval = 5 * 60 * 1000;   // 5 Minutes
        
        final Utils.Predicate p = new Utils.Predicate() {
            @Override public boolean eval() {
                return ThreadManager.get().shuttingDown() || (forceWakeup != null && forceWakeup.get());
            }
        };

        Runnable poller = new Runnable() {
            @Override public void run() {
                while (vehicle.isAsleep()) {
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

    public boolean forceWakeup() {
        ChargeState charge;
        for (int i = 0; i < 15; i++) {
            if ((charge = vehicle.queryCharge()).valid) {
                noteUpdatedState(charge);
                return true;
            }
            vehicle.wakeUp();
            ThreadManager.get().sleep(5 * 1000);
        }
        return false;
    }
    
    public final String vinKey(String key) { return vehicle.getVIN() + "_" + key; }
    
    public void noteUpdatedState(final BaseState state) {
        if (Platform.isFxApplicationThread()) {
            noteUpdatedStateInternal(state);
        } else {
            Platform.runLater(new Runnable() {
                @Override
                public void run() {
                    noteUpdatedStateInternal(state);
                }
            });
        }
    }
    
/*------------------------------------------------------------------------------
 *
 * Hide the Constructor
 * 
 *----------------------------------------------------------------------------*/
    
    private VTVehicle() {
        this.chargeState = new SimpleObjectProperty<>(new ChargeState());
        this.driveState = new SimpleObjectProperty<>();
        this.guiState = new SimpleObjectProperty<>();
        this.hvacState = new SimpleObjectProperty<>();
        this.streamState = new SimpleObjectProperty<>(new StreamState());
        this.vehicleState = new SimpleObjectProperty<>();
    }

    private void noteUpdatedStateInternal(BaseState state) {
        if (state instanceof ChargeState) {
            chargeState.set((ChargeState) state);
        } else if (state instanceof DriveState) {
            driveState.set((DriveState) state);
        } else if (state instanceof GUIState) {
            guiState.set((GUIState) state);
        } else if (state instanceof HVACState) {
            hvacState.set((HVACState) state);
        } else if (state instanceof VehicleState) {
            vehicleState.set((VehicleState) state);
        } else if (state instanceof StreamState) {
            streamState.set((StreamState) state);
        }
    }

}
