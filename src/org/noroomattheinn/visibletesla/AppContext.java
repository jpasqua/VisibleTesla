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
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
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
import org.apache.commons.lang3.SystemUtils;
import org.noroomattheinn.tesla.BaseState;
import org.noroomattheinn.tesla.ChargeState;
import org.noroomattheinn.tesla.DriveState;
import org.noroomattheinn.tesla.GUIState;
import org.noroomattheinn.tesla.HVACState;
import org.noroomattheinn.tesla.StreamState;
import org.noroomattheinn.tesla.Tesla;
import org.noroomattheinn.tesla.Vehicle;
import org.noroomattheinn.tesla.VehicleState;
import org.noroomattheinn.utils.MailGun;
import org.noroomattheinn.utils.Utils;

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
    
    public final Application app;
    public final Stage stage;
    public final Preferences persistentState;
    public final Prefs prefs;
    public Vehicle vehicle = null;
    public final VTUtils utils;
    public final ThreadManager tm;
    public Inactivity inactivity;
    public Tesla tesla;
    public final BooleanProperty shuttingDown;
    public ObjectProperty<ChargeState> lastKnownChargeState;
    public ObjectProperty<DriveState> lastKnownDriveState;
    public ObjectProperty<GUIState> lastKnownGUIState;
    public ObjectProperty<HVACState> lastKnownHVACState;
    public ObjectProperty<StreamState> lastKnownStreamState;
    public ObjectProperty<VehicleState> lastKnownVehicleState;
    public VTUtils.StateTracker<String> schedulerActivity;
    public LocationStore locationStore;
    public StatsStore statsStore;
    public VampireStats vampireStats;
    public StreamProducer snapshotProducer;
    public StatsStreamer statsStreamer;
    public StateProducer stateProducer;
    public CommandIssuer issuer;
    public byte[] restEncPW, restSalt;
    public MailGun mailer = null;
    public String uuidForVehicle;
    
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
        this.app = app;
        this.stage = stage;
        this.persistentState = Preferences.userNodeForPackage(this.getClass());
        
        this.shuttingDown = new SimpleBooleanProperty(false);
        this.tm = new ThreadManager(shuttingDown);
        this.utils = new VTUtils(this);
        this.inactivity = new Inactivity(this);    
        
        this.lastKnownChargeState = new SimpleObjectProperty<>();
        this.lastKnownDriveState = new SimpleObjectProperty<>();
        this.lastKnownGUIState = new SimpleObjectProperty<>();
        this.lastKnownHVACState = new SimpleObjectProperty<>();
        this.lastKnownStreamState = new SimpleObjectProperty<>();
        this.lastKnownVehicleState = new SimpleObjectProperty<>();
        this.schedulerActivity = new VTUtils.StateTracker<>("");

        // Establish the prefs first, they are used be code below
        this.prefs = new Prefs(this);

        appFilesFolder = ensureAppFilesFolder();
        setupLogger(appFilesFolder);
        
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
            addStatPublisher(locationStore);
            statsStore = new StatsStore(
                    this, new File(appFilesFolder, v.getVIN() + ".stats.log"));
            addStatPublisher(statsStore);
        } catch (IOException e) {
            Tesla.logger.severe("Unable to establish repository: " + e.getMessage());
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

        snapshotProducer = new StreamProducer(this);
        stateProducer = new StateProducer(this);

        statsStreamer = new StatsStreamer(this);
        
        vampireStats = new VampireStats(this);

        (restServer = new RESTServer(this)).launch();
        tm.addStoppable(restServer);
    }


    public void noteUpdatedState(BaseState state) {
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
        Tesla.logger.warning("Could not create Application Files Folder: " + aff);
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
                Tesla.logger.warning(
                        "Unable to open file viewer: "
                        + IOUtils.toString(p.getErrorStream()));
            }
        } catch (IOException | InterruptedException ex) {
            Tesla.logger.warning("Unable able to open file viewer: " + ex);
        }
    }

    private void setupLogger(File where) {
        rotateLogs(where, 3);

        Logger logger = Logger.getLogger("");
        FileHandler fileHandler;
        try {
            fileHandler = new FileHandler((new File(where, "visibletesla-00.log")).getAbsolutePath());
            fileHandler.setFormatter(new SimpleFormatter());
            fileHandler.setLevel(Level.ALL);
            logger.addHandler(fileHandler);
        } catch (IOException | SecurityException ex) {
            logger.log(Level.SEVERE, "Unable to establish log file");
        }
    }

    private void rotateLogs(File where, int max) {
        File logfile = new File(where, String.format("visibletesla-%02d.log", max));
        if (logfile.exists()) {
            logfile.delete();
        }
        if (max > 0) {
            File previous = new File(where, String.format("visibletesla-%02d.log", max - 1));
            if (previous.exists()) {
                previous.renameTo(logfile);
            }
            rotateLogs(where, max - 1);
        }
    }
    
}
