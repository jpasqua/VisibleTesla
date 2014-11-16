/*
 * VTUtils - Copyright(c) 2013, 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Aug 16, 2014
 */

package org.noroomattheinn.visibletesla;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.noroomattheinn.tesla.ChargeState;
import org.noroomattheinn.tesla.GUIState;
import org.noroomattheinn.tesla.Options;
import org.noroomattheinn.tesla.Result;
import org.noroomattheinn.tesla.Tesla;
import org.noroomattheinn.tesla.Vehicle;
import org.noroomattheinn.tesla.VehicleState;
import org.noroomattheinn.utils.SimpleTemplate;
import org.noroomattheinn.utils.Utils;
import static org.noroomattheinn.utils.Utils.sleep;

/**
 * VTUtils: Basic utilities for use by the fxApp
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
    private static final String FirmwareVersionsFile = "FirmwareVersions.properties";
        // This data is collected from:
        // http://www.teslamotorsclub.com/showwiki.php?title=Model+S+software+firmware+changelog
    private static final String FirmwareVersionsURL =
        "https://dl.dropboxusercontent.com/u/7045813/VisibleTesla/FirmwareVersions.properties";
    private static final int SubjectLength = 30;
    private static final int MaxTriesToStart = 10;

/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private final AppContext ac;
    private final Map<String,String> firmwareVersions;
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public VTUtils(AppContext ac) {
        this.ac = ac;
        firmwareVersions = new HashMap<>();
        loadFirmwareVersion(getClass().getResourceAsStream(FirmwareVersionsFile));
    }
    
    public String getVersion(String firmwareVersion) {
        String v = firmwareVersions.get(firmwareVersion);
        if (v != null) return v;
        
        try {
            InputStream is = new URL(FirmwareVersionsURL).openStream();
            loadFirmwareVersion(is);
            v = firmwareVersions.get(firmwareVersion);
        } catch (IOException ex) {
            Tesla.logger.warning("Couldn't download firmware versions property file: " + ex);
        }
        if (v == null) {
            v = firmwareVersion;
            // Avoid testing for new versions of the firmware mapping file every
            // time around. We'll check again next time the fxApp starts
            firmwareVersions.put(firmwareVersion, v);
        }
        return v;
    }
    
    public void showSimpleMap(double lat, double lng, String title, int zoom) {
        SimpleTemplate template = new SimpleTemplate(getClass().getResourceAsStream(SimpleMapTemplate));
        String map = template.fillIn(
                "LAT", String.valueOf(lat), "LONG", String.valueOf(lng),
                "TITLE", title, "ZOOM", String.valueOf(zoom));
        try {
            File tempFile = File.createTempFile("VTTrip", ".html");
            FileUtils.write(tempFile, map);
            ac.fxApp.getHostServices().showDocument(tempFile.toURI().toString());
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
        "Grey", Options.PaintColor.PMTG,
        "Steel Grey", Options.PaintColor.PMNG,
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

        GUIState gs = ac.lastKnownGUIState.get();
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

    private static final Map<String,Options.WheelType> overrideWheels = Utils.newHashMap(
        "19\" Silver", Options.WheelType.WT19,
        "19\" Aero", Options.WheelType.WTAE,
        "19\" Turbine", Options.WheelType.WTTB,
        "19\" Turbine Grey", Options.WheelType.WTTG,
        "21\" Silver", Options.WheelType.WT21,
        "21\" Grey", Options.WheelType.WTSP);
    
    public Options.WheelType computedWheelType() {
        Options.WheelType wt = overrideWheels.get(ac.prefs.overideWheelsTo.get());
        if (ac.prefs.overideWheelsActive.get() && wt != null) return wt;

        wt = ac.vehicle.getOptions().wheelType();
        VehicleState vs = ac.lastKnownVehicleState.get();
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
    /**
     * Make contact with the car for the first time. It may need to be woken up
     * in the process. Since we need to do some command as part of this process,
     * we grab the GUIState and store it away.
     * @return 
     */
    private Result establishContact() {
        Vehicle v = ac.vehicle;
        
        long MaxWaitTime = 70 * 1000;
        long now = System.currentTimeMillis();
        while (System.currentTimeMillis() - now < MaxWaitTime) {
            if (ac.shuttingDown.get()) { return new Result(false, "shutting down"); }
            GUIState gs = v.queryGUI();
            if (gs.valid) {
                if (gs.rawState.optString("reason").equals("mobile_access_disabled")) {
                    return new Result(false, "mobile_access_disabled");
                }
                ac.lastKnownGUIState.set(gs);
                return Result.Succeeded;
            } else {
                String error = gs.rawState.optString("error");
                if (error.equals("vehicle unavailable")) { v.wakeUp(); sleep(10 * 1000); }
                else { sleep(5 * 1000); } // It's a timeout or some other error
            }
        }
        return Result.Failed;
    }
    
    public Result cacheBasics() {
        Result madeContact = establishContact();
        if (!madeContact.success) return madeContact;
        
        // As part of establishing contact with the car we cached the GUIState
        Vehicle         v = ac.vehicle;
        VehicleState    vs = v.queryVehicle();
        ChargeState     cs = v.queryCharge();
        
        int tries = 0;
        while (!(vs.valid && cs.valid)) {
            if (tries++ > MaxTriesToStart) { return Result.Failed; }
            sleep(5 * 1000);
            if (ac.shuttingDown.get()) return Result.Failed;
            if (!vs.valid) vs = v.queryVehicle();
            if (!cs.valid) cs = v.queryCharge();
        }
        
        ac.lastKnownVehicleState.set(vs);
        ac.lastKnownChargeState.set(cs);
        return Result.Succeeded;
    }    
    
    public void miscAction(MiscAction actionType) {
        final Vehicle v = ac.vehicle;
        Callable<Result> miscCommand = null;
        switch (actionType) {
            case Flash:
                miscCommand = new Callable<Result>() {
                    @Override public Result call() { return v.flashLights(); } };
                break;
            case Honk:
                miscCommand = new Callable<Result>() {
                    @Override public Result call() { return v.honk(); } };
                break;
            case Wakeup:
                miscCommand = new Callable<Result>() {
                    @Override public Result call() { return v.wakeUp(); } };
                break;
        }
        if (miscCommand != null) ac.issuer.issueCommand(miscCommand, true, null, actionType.name());
    }
    
    public void remoteStart(final String password) {
        ac.issuer.issueCommand(new Callable<Result>() {
            @Override public Result call() { 
                return ac.vehicle.remoteStart(password); 
            } },
            true, null, "Remote Start");
    }

    
    public void waitForVehicleToWake(final Runnable r, final BooleanProperty forceWakeup) {
        final long TestSleepInterval = 5 * 60 * 1000;
        final Utils.Predicate p = new Utils.Predicate() {
            @Override public boolean eval() { return ac.shuttingDown.get() || forceWakeup.get(); } };

        Runnable poller = new Runnable() {
            @Override public void run() {
                while (ac.vehicle.isAsleep()) {
                    Utils.sleep(TestSleepInterval, p);
                    if (ac.shuttingDown.get()) return;
                    if (forceWakeup.get()) {
                        forceWakeup.set(false);
                        Platform.runLater(r);
                    }
                }
            }
        };
        
        ac.tm.launch(poller, "Wait For Wakeup");
    }

    public void sleep(long timeInMillis) {
        Utils.sleep(timeInMillis,  new Utils.Predicate() {
            @Override public boolean eval() { return ac.shuttingDown.get(); } });
    }
    
    public final String vinBased(String key) { return ac.vehicle.getVIN() + "_" + key; }

    private void loadFirmwareVersion(InputStream is) {
        Properties p = new Properties();
        try {
            p.load(is);
            for (String key : p.stringPropertyNames()) {
                firmwareVersions.put(key, p.get(key).toString());
            }
        } catch (IOException ex) {
            Tesla.logger.warning("Couldn't load firmware versions property file: " + ex);
        }
    }
}
