/*
 * AppContext.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Aug 30, 2013
 */
package org.noroomattheinn.visibletesla;

import org.noroomattheinn.visibletesla.rest.RESTServer;
import org.noroomattheinn.visibletesla.stats.StatsPublisher;
import org.noroomattheinn.visibletesla.stats.Stat;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Dialogs;
import javafx.scene.control.ProgressIndicator;
import javafx.stage.Stage;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
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
        
    public static final String AppFilesFolderKey = "APP_AFF";
    public static final String WakeOnTCKey = "APP_WAKE_ON_TC";
    public static final String IdleThresholdKey = "APP_IDLE_THRESHOLD";
    public static final String ProductName = "VisibleTesla";
    public static final String ProductVersion = "0.30.00";
    public static final String ResourceDir = "/org/noroomattheinn/TeslaResources/";
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
    public final MailGun mailer;
    public final TrackedObject<String> schedulerActivity;
    public final VampireStats vampireStats;
    public Vehicle vehicle = null;
    public LocationStore locationStore;
    public StatsStore statsStore;
    public ChargeStore chargeStore;
    public StreamProducer streamProducer;
    public StatsStreamer statsStreamer;
    public StateProducer stateProducer;
    public CommandIssuer issuer;
    public byte[] restEncPW, restSalt;
    public String uuidForVehicle;
    public ObjectProperty<ChargeMonitor.Cycle> lastChargeCycle;
     
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private RESTServer restServer = null;
    private final Map<String, StatsPublisher> typeToPublisher = new HashMap<>();
    private final File appFilesFolder;

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

        // Establish the prefs first, they are used be code below
        this.prefs = new Prefs(this);

        appFilesFolder = ensureAppFilesFolder();
        Utils.setupLogger(appFilesFolder, "visibletesla", logger, prefs.getLogLevel());
        
        tesla = (prefs.enableProxy.get()) ?
            new Tesla(prefs.proxyHost.get(), prefs.proxyPort.get()) : new Tesla();

        mailer = new MailGun("api", prefs.useCustomMailGunKey.get()
                ? prefs.mailGunKey.get() : MailGunKey);
        issuer = new CommandIssuer(this);
        vampireStats = new VampireStats(this);
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
            addStatPublisher(locationStore);
            statsStore = new StatsStore(
                    this, new File(appFilesFolder, v.getVIN() + ".stats.log"));
            addStatPublisher(statsStore);
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
        
        uuidForVehicle = DigestUtils.sha256Hex(vehicle.getVIN());   

        streamProducer = new StreamProducer(this);
        stateProducer = new StateProducer(this);
        statsStreamer = new StatsStreamer(this);

        (restServer = new RESTServer(this)).launch();
        tm.addStoppable(restServer);
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

    public List<Stat.Sample> valuesForRange(String type, long startX, long endX) {
        StatsPublisher sp = typeToPublisher.get(type);
        if (sp == null) {
            return null;
        }
        return sp.valuesForRange(type, startX, endX);
    }
    
    private Map<ProgressIndicator,Integer> refCount = new HashMap<>();
    public void showProgress(final ProgressIndicator pi, final boolean spinning) {
        Platform.runLater(new Runnable() {
            @Override public void run() {
                if (pi == null) return;
                Integer count = refCount.get(pi);
                if (count == null) count = 0;
                count = count + (spinning ? 1 : -1);
                refCount.put(pi, count);
                pi.setVisible(count > 0);
                pi.setProgress(count > 0 ? -1 : 0);
            }
        });
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

/*------------------------------------------------------------------------------
 *
 * PRIVATE - Utility Methods
 * 
 *----------------------------------------------------------------------------*/
    
    private void addStatPublisher(StatsPublisher sp) {
        List<String> types = sp.getStatTypes();
        if (types == null || types.isEmpty()) {
            return;
        }
        for (String type : types) {
            typeToPublisher.put(type, sp);
        }
    }
    

/*------------------------------------------------------------------------------
 *
 * Managing where application files and logs are stored
 * 
 *----------------------------------------------------------------------------*/
    
    private File ensureAppFilesFolder() {
        File aff = getAppFileFolder();
        if (aff.exists()) { return aff; }
        if (aff.mkdir()) { return aff; }
        logger.warning("Could not create Application Files Folder: " + aff);
        return null;
    }

    public File getAppFileFolder() {
        String path = null;
        if (SystemUtils.IS_OS_MAC) {
            path = System.getProperty("user.home") + "/Library/Application Support/" + ProductName;
        } else if (SystemUtils.IS_OS_WINDOWS) {
            File base = javax.swing.filechooser.FileSystemView.getFileSystemView().getDefaultDirectory();
            path = base.getAbsolutePath() + File.separator + ProductName;
        } else if (SystemUtils.IS_OS_LINUX) {
            path = System.getProperty("user.home") + File.separator + "." + ProductName;
        }
        return (path == null) ? null : new File(path);
    }

    public void openFileViewer(String where) {
        String command = "";

        if (SystemUtils.IS_OS_MAC) {
            command = "open";
        } else if (SystemUtils.IS_OS_WINDOWS) {
            command = "Explorer.exe";
        } else if (SystemUtils.IS_OS_LINUX) {
            //command = "vi";
            command = "xdg-open";
        }

        try {
            Process p = (new ProcessBuilder(command, where)).start();
            p.waitFor();
            if (p.exitValue() != 0) {
                logger.warning(
                        "Unable to open file viewer: "
                        + IOUtils.toString(p.getErrorStream()));
            }
        } catch (IOException | InterruptedException ex) {
            logger.warning("Unable able to open file viewer: " + ex);
        }
    }

    
}
