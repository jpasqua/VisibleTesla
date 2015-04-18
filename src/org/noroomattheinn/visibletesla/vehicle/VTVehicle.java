/*
 * VTVehicle - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Nov 20, 2014
 */
package org.noroomattheinn.visibletesla.vehicle;

import java.util.Map;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.noroomattheinn.utils.TrackedObject;
import org.noroomattheinn.tesla.BaseState;
import org.noroomattheinn.tesla.ChargeState;
import org.noroomattheinn.tesla.DriveState;
import org.noroomattheinn.tesla.GUIState;
import org.noroomattheinn.tesla.HVACState;
import org.noroomattheinn.tesla.Options;
import org.noroomattheinn.tesla.StreamState;
import org.noroomattheinn.tesla.Vehicle;
import org.noroomattheinn.tesla.VehicleState;
import org.noroomattheinn.utils.ThreadManager;
import org.noroomattheinn.utils.Utils;

import static org.noroomattheinn.tesla.Tesla.logger;

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
    private static final Map<String,Options.Model> overrideModel = Utils.newHashMap(
        "P85+", Options.Model.S60,
        "P85D", Options.Model.S85,
        "P85D", Options.Model.P85,
        "P85+", Options.Model.P85Plus,
        "P85D", Options.Model.P85D,
        "S85D", Options.Model.S85D);
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
        "Pearl", Options.PaintColor.PPSW,
        "Titanium", Options.PaintColor.PPTI,
        "Obsidian Black", Options.PaintColor.PMBL,
        "Deep Blue", Options.PaintColor.PPSB
        );
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
   
    private final Overrides overrides;
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public final TrackedObject<ChargeState> chargeState;
    public final TrackedObject<DriveState> driveState;
    public final TrackedObject<VehicleState> vehicleState;
    public final TrackedObject<HVACState> hvacState;
    public final TrackedObject<GUIState> guiState;
    public final TrackedObject<StreamState> streamState;
    public final TrackedObject<Vehicle> vehicle;
    
    public VTVehicle(Overrides overrides) {
        this.overrides = overrides;
        this.chargeState = new TrackedObject<>(new ChargeState());
        this.driveState = new TrackedObject<>(null);
        this.guiState = new TrackedObject<>(null);
        this.hvacState = new TrackedObject<>(null);
        this.streamState = new TrackedObject<>(new StreamState());
        this.vehicleState = new TrackedObject<>(null);
        this.vehicle = new TrackedObject<>(null);
    }

    public static class Overrides {
        public final StringProperty   wheels, color, units, model, roof;
        public final BooleanProperty  doWheels, doColor, doUnits, doModel, doRoof;
        
        public Overrides() {
            this.wheels    = new SimpleStringProperty();
            this.doWheels  = new SimpleBooleanProperty();
            this.color     = new SimpleStringProperty();
            this.doColor   = new SimpleBooleanProperty();
            this.units     = new SimpleStringProperty();
            this.doUnits   = new SimpleBooleanProperty();
            this.model     = new SimpleStringProperty();
            this.doModel   = new SimpleBooleanProperty();
            this.roof      = new SimpleStringProperty();
            this.doRoof    = new SimpleBooleanProperty();
        }
        
        public Overrides(
                StringProperty overideWheelsTo, BooleanProperty overideWheelsActive,
                StringProperty overideColorTo, BooleanProperty overideColorActive,
                StringProperty overideUnitsTo, BooleanProperty overideUnitsActive, 
                StringProperty overideModelTo, BooleanProperty overideModelActive, 
                StringProperty overideRoofTo, BooleanProperty overideRoofActive) {
            this.wheels = overideWheelsTo;
            this.doWheels = overideWheelsActive;
            this.color = overideColorTo;
            this.doColor = overideColorActive;
            this.units = overideUnitsTo;
            this.doUnits = overideUnitsActive;
            this.model = overideModelTo;
            this.doModel = overideModelActive;
            this.roof = overideRoofTo;
            this.doRoof = overideRoofActive;
        }
    }
    
    public void setVehicle(Vehicle v) {
        vehicle.set(v);
    }
    
    public Vehicle getVehicle() { return vehicle.get(); }
    
    public Options.Model model() {
        Options.Model model = overrideModel.get(overrides.model.get());
        if (overrides.doModel.get() && model != null) return model;
        return (getVehicle().getOptions().model());
    }
    
    public Options.RoofType roofType() {
        Options.RoofType roof = overrideRoof.get(overrides.roof.get());
        if (overrides.doRoof.get() && roof != null) return roof;
        return (getVehicle().getOptions().roofType());
    }
    
    public Options.PaintColor paintColor() {
        Options.PaintColor color = overrideColor.get(overrides.color.get());
        if (overrides.doColor.get() && color != null) return color;
        return (getVehicle().getOptions().paintColor());
    }
    
    
    public boolean useDegreesF() {
        Utils.UnitType units = overrideUnits.get(overrides.units.get());
        if (overrides.doUnits.get() && units != null)
            return units == Utils.UnitType.Imperial;
        return guiState.get().temperatureUnits.equalsIgnoreCase("F");

    }
    
    public Utils.UnitType unitType() {
        Utils.UnitType units = overrideUnits.get(overrides.units.get());
        if (overrides.doUnits.get() && units != null) return units;

        return guiState.get().distanceUnits.equalsIgnoreCase("mi/hr")
                ? Utils.UnitType.Imperial : Utils.UnitType.Metric;
    }

    public double inProperUnits(double val) {
        if (unitType() == Utils.UnitType.Imperial) return val;
        return Utils.milesToKm(val);
    }

    public Options.WheelType wheelType() {
        Options.WheelType wt = overrideWheels.get(overrides.wheels.get());
        if (overrides.doWheels.get() && wt != null) return wt;

        wt = getVehicle().getOptions().wheelType();
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
                while (getVehicle().isAsleep()) {
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
            if ((charge = getVehicle().queryCharge()).valid) {
                noteUpdatedState(charge);
                return true;
            }
            getVehicle().wakeUp();
            ThreadManager.get().sleep(5 * 1000);
        }
        return false;
    }
        
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
    
    public String carDetailsAsJSON() {
        return CarInfo.carDetailsAsJSON(this);
    }
    
    public String carStateAsJSON() {
        return CarInfo.carStateAsJSON(this);
    }
    
/*------------------------------------------------------------------------------
 *
 * Private utility methods
 * 
 *----------------------------------------------------------------------------*/
    
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
