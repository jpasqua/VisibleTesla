/*
 * AppContext.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Aug 30, 2013
 */
package org.noroomattheinn.visibletesla;

import org.noroomattheinn.visibletesla.rest.RESTServer;
import java.io.File;
import java.io.IOException;
import java.util.prefs.Preferences;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Dialogs;
import javafx.stage.Stage;
import org.apache.commons.lang3.StringUtils;
import org.noroomattheinn.tesla.BaseState;
import org.noroomattheinn.tesla.ChargeState;
import org.noroomattheinn.tesla.DriveState;
import org.noroomattheinn.tesla.GUIState;
import org.noroomattheinn.tesla.HVACState;
import org.noroomattheinn.tesla.StreamState;
import org.noroomattheinn.tesla.Tesla;
import static org.noroomattheinn.tesla.Tesla.logger;
import org.noroomattheinn.tesla.Vehicle;
import org.noroomattheinn.tesla.VehicleState;
import org.noroomattheinn.utils.MailGun;
import org.noroomattheinn.utils.Utils;
import org.noroomattheinn.visibletesla.fxextensions.TrackedObject;

/**
 * AppContext - Stores application-wide state for use by all of the individual
 * Tabs and provides a number of utility methods.
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class AppContext {

/*------------------------------------------------------------------------------
 *
 * Constants and Enums
 * 
 *----------------------------------------------------------------------------*/
        
    public static final String ProductName = "VisibleTesla";
    public static final String ProductVersion = "0.30.00";
    public static final String GoogleMapsAPIKey =
            "AIzaSyAZDh-9z3wgvLFnhTu72O5h2Qn9_4Omyj4";
    public static final String MailGunKey =
            "key-2x6kwt4t-f4qcy9nb9wmo4yed681ogr6";

/*------------------------------------------------------------------------------
 *
 * PUBLIC - Application State
 * 
 *----------------------------------------------------------------------------*/
    
    public final Application fxApp;
    public final Tesla tesla;
    public final Stage stage;
    public final Preferences persistentState;
    public final Prefs prefs;
    public final ThreadManager tm;
    public final AppMode appMode;
    public final AppState appState;
    public final BooleanProperty shuttingDown;
    public final ObjectProperty<ChargeState> lastKnownChargeState;
    public final ObjectProperty<DriveState> lastKnownDriveState;
    public final ObjectProperty<GUIState> lastKnownGUIState;
    public final ObjectProperty<HVACState> lastKnownHVACState;
    public final ObjectProperty<StreamState> lastKnownStreamState;
    public final ObjectProperty<VehicleState> lastKnownVehicleState;
    public final TrackedObject<String> schedulerActivity;
    public TrackedObject<ChargeMonitor.Cycle> lastChargeCycle;
    public final RESTServer restServer;
    public Vehicle vehicle = null;
    public LocationStore locationStore;
    public StatsStore statsStore;
    public ChargeStore chargeStore;
    public StreamProducer streamProducer;
    public StatsStreamer statsStreamer;
    public StateProducer stateProducer;
    public CommandIssuer issuer;

/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private final File appFilesFolder;
    private final MailGun mailer;
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    AppContext(Application app, Stage stage) {
        this.fxApp = app;
        this.stage = stage;
        this.persistentState = Preferences.userNodeForPackage(this.getClass());
        
        this.shuttingDown = new SimpleBooleanProperty(false);
        this.tm = new ThreadManager(shuttingDown);
        this.appMode = new AppMode(this);    
        this.appState = new AppState(this);    
        
        this.lastKnownChargeState = new SimpleObjectProperty<>(new ChargeState());
        this.lastKnownDriveState = new SimpleObjectProperty<>();
        this.lastKnownGUIState = new SimpleObjectProperty<>();
        this.lastKnownHVACState = new SimpleObjectProperty<>();
        this.lastKnownStreamState = new SimpleObjectProperty<>(new StreamState());
        this.lastKnownVehicleState = new SimpleObjectProperty<>();
        this.schedulerActivity = new TrackedObject<>("");

        restServer = new RESTServer(this);

        // Establish the prefs first, they are used be code below
        this.prefs = new Prefs(this);

        appFilesFolder = Utils.ensureAppFilesFolder(ProductName);
        Utils.setupLogger(appFilesFolder, "visibletesla", logger, prefs.getLogLevel());
        
        tesla = (prefs.enableProxy.get()) ?
            new Tesla(prefs.proxyHost.get(), prefs.proxyPort.get()) : new Tesla();

        mailer = new MailGun("api", prefs.useCustomMailGunKey.get()
                ? prefs.mailGunKey.get() : MailGunKey);
        issuer = new CommandIssuer(this);
    }

    public boolean lockAppInstance() {
        return (Utils.obtainLock(vehicle.getVIN() + ".lck", appFilesFolder));
    }
                        
    public void prepForVehicle(Vehicle v) {
        vehicle = v;

        lastChargeCycle = (new ChargeMonitor(this).lastChargeCycle);
        try {
            locationStore = new LocationStore(
                    this, new File(appFilesFolder, v.getVIN() + ".locs.log"));
            statsStore = new StatsStore(
                    this, new File(appFilesFolder, v.getVIN() + ".stats.log"));
            chargeStore = new ChargeStore(
                    this, new File(appFilesFolder, v.getVIN() + ".charge.json"));
        } catch (IOException e) {
            logger.severe("Unable to establish repository: " + e.getMessage());
            Dialogs.showErrorDialog(stage,
                    "VisibleTesla has encountered a severe error\n"
                    + "while trying to access its data files. Another\n"
                    + "copy of VisibleTesla may already be writing to them\n"
                    + "or they may be missing.\n\n"
                    + "VisibleTesla will close when you close this window.",
                    "Problem accessing data files", "Problem launching application");
            Platform.exit();
        }
        
        streamProducer = new StreamProducer(this);
        stateProducer = new StateProducer(this);
        statsStreamer = new StatsStreamer(this);

        restServer.launch();
    }


    public void noteUpdatedState(final BaseState state) {
        if (Platform.isFxApplicationThread()) { noteUpdatedStateInternal(state); }
        else {
            Platform.runLater(new Runnable() {
                @Override public void run() { noteUpdatedStateInternal(state); } });
        }
    }
    
    private void noteUpdatedStateInternal(BaseState state) {
        if (state instanceof ChargeState) {
            lastKnownChargeState.set(((ChargeState)state));
        } else if (state instanceof DriveState) {
            lastKnownDriveState.set(((DriveState)state));
        } else if (state instanceof GUIState) {
            lastKnownGUIState.set(((GUIState)state));
        } else if (state instanceof HVACState) {
            lastKnownHVACState.set(((HVACState)state));
        } else if (state instanceof VehicleState) {
            lastKnownVehicleState.set(((VehicleState)state));
        } else if (state instanceof StreamState) {
            lastKnownStreamState.set(((StreamState)state));
        }
    }

    public boolean sendNotification(String addr, String msg) {
        final int SubjectLength = 30;
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
            logger.warning(
                    "Unable to send a notification because no address was specified: " + msg);
            return false;
        }
        if (!mailer.send(addr, subject, msg)) {
            logger.warning("Failed sending message to: " + addr + ": " + msg);
            return false;
        }
        return true;
    }
    
    public File appFileFolder() { return appFilesFolder; }

/*------------------------------------------------------------------------------
 *
 * Managing where application files and logs are stored
 * 
 *----------------------------------------------------------------------------*/
    
//    private File ensureAppFilesFolder() {
//        File aff = getAppFileFolder();
//        if (aff.exists()) { return aff; }
//        if (aff.mkdir()) { return aff; }
//        logger.warning("Could not create Application Files Folder: " + aff);
//        return null;
//    }
//
//    public File getAppFileFolder() {
//        String path = null;
//        if (SystemUtils.IS_OS_MAC) {
//            path = System.getProperty("user.home") + "/Library/Application Support/" + ProductName;
//        } else if (SystemUtils.IS_OS_WINDOWS) {
//            File base = javax.swing.filechooser.FileSystemView.getFileSystemView().getDefaultDirectory();
//            path = base.getAbsolutePath() + File.separator + ProductName;
//        } else if (SystemUtils.IS_OS_LINUX) {
//            path = System.getProperty("user.home") + File.separator + "." + ProductName;
//        }
//        return (path == null) ? null : new File(path);
//    }
//
//    public void openFileViewer(String where) {
//        String command = "";
//
//        if (SystemUtils.IS_OS_MAC) {
//            command = "open";
//        } else if (SystemUtils.IS_OS_WINDOWS) {
//            command = "Explorer.exe";
//        } else if (SystemUtils.IS_OS_LINUX) {
//            //command = "vi";
//            command = "xdg-open";
//        }
//
//        try {
//            Process p = (new ProcessBuilder(command, where)).start();
//            p.waitFor();
//            if (p.exitValue() != 0) {
//                logger.warning(
//                        "Unable to open file viewer: "
//                        + IOUtils.toString(p.getErrorStream()));
//            }
//        } catch (IOException | InterruptedException ex) {
//            logger.warning("Unable able to open file viewer: " + ex);
//        }
//    }

    
}
