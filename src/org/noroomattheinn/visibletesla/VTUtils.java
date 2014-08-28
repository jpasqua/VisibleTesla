/*
 * VTUtils - Copyright(c) 2013, 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Aug 16, 2014
 */

package org.noroomattheinn.visibletesla;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.noroomattheinn.tesla.ActionController;
import org.noroomattheinn.tesla.ChargeState;
import org.noroomattheinn.tesla.GUIState;
import org.noroomattheinn.tesla.Options;
import org.noroomattheinn.tesla.Result;
import org.noroomattheinn.tesla.Tesla;
import org.noroomattheinn.tesla.Vehicle;
import org.noroomattheinn.tesla.VehicleState;
import org.noroomattheinn.utils.SimpleTemplate;
import org.noroomattheinn.utils.Utils;
import us.monoid.json.JSONObject;

/**
 * VTUtils: Basic utilities for use by the app
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class VTUtils {
    
/*------------------------------------------------------------------------------
 *
 * Constants and Enums
 * 
 *----------------------------------------------------------------------------*/
    
    public enum MiscAction {Honk, Flash, Wakeup};
    private static final String SimpleMapTemplate = "SimpleMap.html";
    private static final int SubjectLength = 30;
    private static final int MaxTriesToStart = 10;

/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private final AppContext ac;
    private ActionController actions;
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public VTUtils(AppContext ac) {
        this.ac = ac;
        this.actions = null;
    }
    
    public void showSimpleMap(double lat, double lng, String title, int zoom) {
        SimpleTemplate template = new SimpleTemplate(getClass().getResourceAsStream(SimpleMapTemplate));
        String map = template.fillIn(
                "LAT", String.valueOf(lat), "LONG", String.valueOf(lng),
                "TITLE", title, "ZOOM", String.valueOf(zoom));
        try {
            File tempFile = File.createTempFile("VTTrip", ".html");
            FileUtils.write(tempFile, map);
            ac.app.getHostServices().showDocument(tempFile.toURI().toString());
        } catch (IOException ex) {
            Tesla.logger.warning("Unable to create temp file");
            // TO DO: Pop up a dialog!
        }
    }

    public boolean sendNotification(String addr, String msg) {
        String subject = StringUtils.left(msg, SubjectLength);
        if (msg.length() > SubjectLength) {
            subject = subject + "...";
        }
        return sendNotification(addr, subject, msg);
    }

    public boolean sendNotification(String addr, String subject, String msg) {
        if (msg == null) {
            return true;
        }
        Tesla.logger.fine("Notification: " + msg);
        if (addr == null || addr.length() == 0) {
            Tesla.logger.warning(
                    "Unable to send a notification because no address was specified: " + msg);
            return false;
        }
        if (!ac.mailer.send(addr, subject, msg)) {
            Tesla.logger.warning("Failed sending message to: " + addr + ": " + msg);
            return false;
        }
        return true;
    }

    private static final Map<String,Options.RoofType> overrideRoof = Utils.newHashMap(
            "Body Color", Options.RoofType.RFBC,
            "Black", Options.RoofType.RFBK,
            "Pano", Options.RoofType.RFPO
            );
    
    public Options.RoofType roofType() {
        Options.RoofType roof = overrideRoof.get(ac.prefs.overideRoofTo.get());
        if (ac.prefs.overideRoofActive.get() && roof != null) return roof;
        return (ac.vehicle.getOptions().roofType());
    }
    
    private static final Map<String,Options.PaintColor> overrideColor = Utils.newHashMap(
        "White", Options.PaintColor.PBCW,
        "Black", Options.PaintColor.PBSB,
        "Brown", Options.PaintColor.PMAB,
        "Blue", Options.PaintColor.PMMB,
        "Green", Options.PaintColor.PMSG,
        "Silver", Options.PaintColor.PMSS,
        "Gray", Options.PaintColor.PMTG,
        "Red", Options.PaintColor.PPMR,
        "Sig. Red", Options.PaintColor.PPSR,
        "Pearl", Options.PaintColor.PPSW);
    
    public Options.PaintColor paintColor() {
        Options.PaintColor color = overrideColor.get(ac.prefs.overideColorTo.get());
        if (ac.prefs.overideColorActive.get() && color != null) return color;
        return (ac.vehicle.getOptions().paintColor());
    }
    
    private static final Map<String,Utils.UnitType> overrideUnits = Utils.newHashMap(
            "Imperial", Utils.UnitType.Imperial,
            "Metric", Utils.UnitType.Metric
            );
    
    public boolean useDegreesF() {
        Utils.UnitType units = overrideUnits.get(ac.prefs.overideUnitsTo.get());
        if (ac.prefs.overideUnitsActive.get() && units != null)
            return units == Utils.UnitType.Imperial;
        return ac.lastKnownGUIState.get().temperatureUnits.equalsIgnoreCase("F");

    }
    
    public Utils.UnitType unitType() {
        Utils.UnitType units = overrideUnits.get(ac.prefs.overideUnitsTo.get());
        if (ac.prefs.overideUnitsActive.get() && units != null) return units;

        GUIState.State gs = ac.lastKnownGUIState.get();
        if (gs != null) {
            return gs.distanceUnits.equalsIgnoreCase("mi/hr")
                    ? Utils.UnitType.Imperial : Utils.UnitType.Metric;
        }
        return Utils.UnitType.Imperial;
    }

    public double inProperUnits(double val) {
        if (unitType() == Utils.UnitType.Imperial) {
            return val;
        }
        return Utils.mToK(val);
    }

    private static final Map<String,Options.WheelType> overrideWheels= Utils.newHashMap(
        "19\" Silver", Options.WheelType.WT19,
        "19\" Aero", Options.WheelType.WTAE,
        "19\" Cyclone", Options.WheelType.WTTB,
        "21\" Silver", Options.WheelType.WT21,
        "21\" Gray", Options.WheelType.WTSP);
    
    public Options.WheelType computedWheelType() {
        Options.WheelType wt = overrideWheels.get(ac.prefs.overideWheelsTo.get());
        if (ac.prefs.overideWheelsActive.get() && wt != null) return wt;

        wt = ac.vehicle.getOptions().wheelType();
        VehicleState.State vs = ac.lastKnownVehicleState.get();
        if (vs.wheelType != null) {
            // Check for known override wheel types, right now that's just Aero19
            switch (vs.wheelType) {
                case "Aero19":
                    wt = Options.WheelType.WTAE;
                    break;
                case "Base19":
                    wt = Options.WheelType.WT19;
                    break;
                case "Super21Gray":
                    wt = Options.WheelType.WTSG;
                    break;
                default:
                    Tesla.logger.info("WheelType from VehicleState: " + vs.wheelType);
                    break;
            }
        }
        return wt;
    }
    
    public void logAppInfo() {
        Tesla.logger.info(AppContext.ProductName + ": " + AppContext.ProductVersion);
        
        Tesla.logger.info(
                String.format("Max memory: %4dmb", Runtime.getRuntime().maxMemory()/(1024*1024)));
        List<String> jvmArgs = Utils.getJVMArgs();
        Tesla.logger.info("JVM Arguments");
        if (jvmArgs != null) {
            for (String arg : jvmArgs) {
                Tesla.logger.info("Arg: " + arg);
            }
        }
    }
    
    public Result cacheBasics() {
        Vehicle v = ac.vehicle;
        GUIState     gs = new GUIState(v);
        VehicleState vs = new VehicleState(v);
        ChargeState  cs = new ChargeState(v);
        ActionController action = new ActionController(v);
        
        int tries = 0;
        if (gs.refresh()) {
            JSONObject result = gs.getRawResult();
            if (result.optString("reason").equals("mobile_access_disabled")) {
                return new Result(false, "mobile_access_disabled");
            }
        }
        vs.refresh();
        cs.refresh();
        while (gs.state == null ||  vs.state == null || cs.state == null) {
            if (tries++ > MaxTriesToStart) { return Result.Failed; }
            
            action.wakeUp();
            Utils.sleep(10000);
            if (ac.shuttingDown.get()) return Result.Failed;
            
            if (gs.state == null) gs.refresh();
            if (vs.state == null) vs.refresh();
            if (cs.state == null) cs.refresh();
        }
        
        ac.lastKnownGUIState.set(gs.state);
        ac.lastKnownVehicleState.set(vs.state);
        ac.lastKnownChargeState.set(cs.state);
        return Result.Succeeded;
    }    
    
    public void miscAction(MiscAction actionType) {
        if (actions == null) actions = new ActionController(ac.vehicle);
        Callable<Result> miscCommand = null;
        switch (actionType) {
            case Flash:
                miscCommand = new Callable<Result>() {
                    @Override public Result call() { return actions.flashLights(); } };
                break;
            case Honk:
                miscCommand = new Callable<Result>() {
                    @Override public Result call() { return actions.honk(); } };
                break;
            case Wakeup:
                miscCommand = new Callable<Result>() {
                    @Override public Result call() { return actions.wakeUp(); } };
                break;
        }
        if (miscCommand != null) ac.issuer.issueCommand(miscCommand, null);
    }
    
    public void waitForVehicleToWake(final Runnable r, final BooleanProperty forceWakeup) {
        final long TestSleepInterval = 5 * 60 * 1000;
               
        Runnable poller = new Runnable() {
            @Override public void run() {
                while (ac.vehicle.isAsleep()) {
                    if (ac.shuttingDown.get()) break;
                    Utils.sleep(TestSleepInterval);
                    if (ac.shuttingDown.get()) return;
                }
                forceWakeup.set(false);
                Platform.runLater(r);
            }
        };
        final Thread pollThread = ac.tm.launch(poller, "00 - Wait For Wakeup");
        
        forceWakeup.addListener(new ChangeListener<Boolean>() {
            @Override public void changed(
                    ObservableValue<? extends Boolean> ov, Boolean t, Boolean t1) {
                if (t1) {
                    pollThread.interrupt();
                }
            }
        });
    }
}
