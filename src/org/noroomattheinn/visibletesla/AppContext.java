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
    public final ObjectProperty<ChargeState> lastChargeState;
    public final ObjectProperty<DriveState> lastDriveState;
    public final ObjectProperty<GUIState> lastGUIState;
    public final ObjectProperty<HVACState> lastHVACState;
    public final ObjectProperty<StreamState> lastStreamState;
    public final ObjectProperty<VehicleState> lastVehicleState;
    public final TrackedObject<ChargeCycle> lastChargeCycle;
    public final TrackedObject<String> schedulerActivity;
    public final RESTServer restServer;
    public Vehicle vehicle = null;
    public LocationStore locationStore;
    public StatsStore statsStore;
    public ChargeStore chargeStore;
    public StreamProducer streamProducer;
    public StateProducer stateProducer;
    public CommandIssuer issuer;

/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private final File appFilesFolder;
    private final MailGun mailer;
    private StatsStreamer statsStreamer;
    private ChargeMonitor chargeMonitor;

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
        
        this.lastChargeState = new SimpleObjectProperty<>(new ChargeState());
        this.lastDriveState = new SimpleObjectProperty<>();
        this.lastGUIState = new SimpleObjectProperty<>();
        this.lastHVACState = new SimpleObjectProperty<>();
        this.lastStreamState = new SimpleObjectProperty<>(new StreamState());
        this.lastVehicleState = new SimpleObjectProperty<>();
        this.schedulerActivity = new TrackedObject<>("");
        this.lastChargeCycle = new TrackedObject<>(null);
        
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
        chargeMonitor = new ChargeMonitor(this);
        
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
            lastChargeState.set((ChargeState)state);
        } else if (state instanceof DriveState) {
            lastDriveState.set((DriveState)state);
        } else if (state instanceof GUIState) {
            lastGUIState.set((GUIState)state);
        } else if (state instanceof HVACState) {
            lastHVACState.set((HVACState)state);
        } else if (state instanceof VehicleState) {
            lastVehicleState.set((VehicleState)state);
        } else if (state instanceof StreamState) {
            lastStreamState.set((StreamState)state);
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

}
