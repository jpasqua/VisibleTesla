/*
 * AppContext.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Aug 30, 2013
 */

package org.noroomattheinn.visibletesla;

import org.noroomattheinn.visibletesla.stats.StatsPublisher;
import org.noroomattheinn.visibletesla.stats.Stat;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.prefs.Preferences;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Dialogs;
import javafx.stage.Stage;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOCase;
import org.apache.commons.io.filefilter.FileFileFilter;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.noroomattheinn.tesla.APICall;
import org.noroomattheinn.tesla.ChargeState;
import org.noroomattheinn.tesla.DrivingState;
import org.noroomattheinn.tesla.GUIState;
import org.noroomattheinn.tesla.HVACState;
import org.noroomattheinn.tesla.Options;
import org.noroomattheinn.tesla.SnapshotState;
import org.noroomattheinn.tesla.Tesla;
import org.noroomattheinn.tesla.Vehicle;
import org.noroomattheinn.tesla.VehicleState;
import org.noroomattheinn.utils.MailGun;
import org.noroomattheinn.utils.SimpleTemplate;
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

    private static final String SimpleMapTemplate = "SimpleMap.html";
    
    public static final String AppFilesFolderKey = "APP_AFF";
    public static final String WakeOnTCKey = "APP_WAKE_ON_TC";
    public static final String IdleThresholdKey = "APP_IDLE_THRESHOLD";
    
    public static final String ProductName = "VisibleTesla";
    public static final String ProductVersion = "0.26.01";
    public static final String ResourceDir = "/org/noroomattheinn/TeslaResources/";
    public static final String GoogleMapsAPIKey = 
            "AIzaSyAZDh-9z3wgvLFnhTu72O5h2Qn9_4Omyj4";
    public static final String MailGunKey = 
            "key-2x6kwt4t-f4qcy9nb9wmo4yed681ogr6";
    
    public enum InactivityType {Sleep, Daydream, Awake};
    
/*------------------------------------------------------------------------------
 *
 * PUBLIC - Application State
 * 
 *----------------------------------------------------------------------------*/
    
    public Application app;
    public Stage stage;
    public Preferences persistentState;
    public Prefs prefs;
    public File appFilesFolder;
    public Vehicle vehicle = null;
    
    public ObjectProperty<InactivityType> inactivityState;
    public BooleanProperty shuttingDown;
    
    public ObjectProperty<Utils.UnitType> simulatedUnits;
    public ObjectProperty<Options.WheelType> simulatedWheels;
    public ObjectProperty<Options.PaintColor> simulatedColor;
    public ObjectProperty<Options.RoofType> simulatedRoof;
    
    public ObjectProperty<ChargeState.State> lastKnownChargeState;
    public ObjectProperty<DrivingState.State> lastKnownDrivingState;
    public ObjectProperty<GUIState.State> lastKnownGUIState;
    public ObjectProperty<HVACState.State> lastKnownHVACState;
    public ObjectProperty<SnapshotState.State> lastKnownSnapshotState;
    public ObjectProperty<VehicleState.State> lastKnownVehicleState;
    
    public ObjectProperty<String> schedulerActivityReport;
    
    public LocationStore locationStore;
    public StatsStore statsStore;
    public VampireStats vampireStats;
    
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private final ArrayList<Thread> threads = new ArrayList<>();
    private Utils.Callback<InactivityType,Void> inactivityModeListener;
    private StatsStreamer statsStreamer;
    private final Map<String,StatsPublisher> typeToPublisher = new HashMap<>();
    private MailGun mailer = null;
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    AppContext(Application app, Stage stage) {
        this.app = app;
        this.stage = stage;
        this.persistentState = Preferences.userNodeForPackage(this.getClass());
        this.inactivityModeListener = null;
        
        this.inactivityState = new SimpleObjectProperty<>(InactivityType.Awake);
        this.shuttingDown = new SimpleBooleanProperty(false);
        
        this.lastKnownChargeState = new SimpleObjectProperty<>();
        this.lastKnownDrivingState = new SimpleObjectProperty<>();
        this.lastKnownGUIState = new SimpleObjectProperty<>();
        this.lastKnownHVACState = new SimpleObjectProperty<>();
        this.lastKnownSnapshotState = new SimpleObjectProperty<>();
        this.lastKnownVehicleState = new SimpleObjectProperty<>();
        this.schedulerActivityReport = new SimpleObjectProperty<>();
        
        this.simulatedUnits = new SimpleObjectProperty<>();
        this.simulatedWheels = new SimpleObjectProperty<>();
        this.simulatedColor = new SimpleObjectProperty<>();
        this.simulatedRoof = new SimpleObjectProperty<>();
        
        this.prefs = new Prefs(this);
        
        appFilesFolder = ensureAppFilesFolder();
        mailer = new MailGun("api",  prefs.useCustomMailGunKey.get() ?
                prefs.mailGunKey.get() : MailGunKey);
    }

    public void prepForVehicle(Vehicle v) {
        if (vehicle == null || !v.getVIN().equals(vehicle.getVIN())) {
            vehicle = v;
            
            try {
                if (locationStore != null) locationStore.close();
                locationStore = new LocationStore(
                        this, new File(appFilesFolder, v.getVIN()+".locs.log"));
                addStatPublisher(locationStore);

                if (statsStore != null) statsStore.close();
                statsStore = new StatsStore(
                        this, new File(appFilesFolder, v.getVIN()+".stats.log"));
                addStatPublisher(statsStore);
            } catch (IOException e) {
                Tesla.logger.severe("Unable to establish repository: " + e.getMessage());
                Dialogs.showErrorDialog(stage,
                        "VisibleTesla has encountered a severe error\n" +
                        "while trying to access its data files. Another\n" +
                        "copy of VisibleTesla may already be writing to them\n" +
                        "or they may be missing.\n\n"+
                        "VisibleTesla will close when you close this window.",
                        "Problem accessing data files", "Problem launching application");
                Platform.exit();
            }
            
            if (statsStreamer != null) statsStreamer.stop();
            statsStreamer = new StatsStreamer(this, v);
            vampireStats = new VampireStats(this);
        }
    }
    
    public void noteUpdatedState(APICall state) {
        if (state instanceof ChargeState)
            lastKnownChargeState.set(((ChargeState)state).state);
        else if (state instanceof DrivingState)
            lastKnownDrivingState.set(((DrivingState)state).state);
        else if (state instanceof GUIState)
            lastKnownGUIState.set(((GUIState)state).state);
        else if (state instanceof HVACState)
            lastKnownHVACState.set(((HVACState)state).state);
        else if (state instanceof VehicleState)
            lastKnownVehicleState.set(((VehicleState)state).state);
        else if (state instanceof SnapshotState)
            lastKnownSnapshotState.set(((SnapshotState)state).state);
    }
    
    public List<Stat.Sample> valuesForRange(String type, long startX, long endX) {
        StatsPublisher sp = typeToPublisher.get(type);
        if (sp == null) return null;
        return sp.valuesForRange(type, startX, endX);
    }
    
    private static final int SubjectLength = 30;
    public boolean sendNotification(String addr, String msg) {
        String subject = StringUtils.left(msg, SubjectLength);
        if (msg.length() > SubjectLength) subject = subject + "...";
        return sendNotification(addr, subject, msg);
    }
    
    public boolean sendNotification(String addr, String subject, String msg) {
        if (msg == null) return true;
        Tesla.logger.fine("Notification: " + msg);
        if (addr == null || addr.length() == 0) {
            Tesla.logger.warning(
                    "Unable to send a notification because no address was specified: " + msg);
            return false;
        }
        String date = String.format("%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS", new Date());
        String body = date + "\n" + msg;
        if (!mailer.send(addr, subject, body)) {
            Tesla.logger.warning("Failed sending message to: " + addr + ": " + msg);
            return false;
        }
        return true;
    }
    
    public void showSimpleMap(double lat, double lng, String title, int zoom) {        
        SimpleTemplate template = new SimpleTemplate(getClass().getResourceAsStream(SimpleMapTemplate));
        String map = template.fillIn(
                "LAT", String.valueOf(lat), "LONG", String.valueOf(lng),
                "TITLE", title, "ZOOM", String.valueOf(zoom));
        try {
            File tempFile = File.createTempFile("VTTrip", ".html");
            FileUtils.write(tempFile, map);
            app.getHostServices().showDocument(tempFile.toURI().toString());
        } catch (IOException ex) {
            Tesla.logger.warning("Unable to create temp file");
            // TO DO: Pop up a dialog!
        }
    }
    
/*------------------------------------------------------------------------------
 *
 * PRIVATE - Utility Methods
 * 
 *----------------------------------------------------------------------------*/
    
    private void addStatPublisher(StatsPublisher sp) {
        List<String> types = sp.getStatTypes();
        if (types == null || types.isEmpty())
            return;
        for (String type : types)
            typeToPublisher.put(type, sp);
    }
    
/*------------------------------------------------------------------------------
 *
 * Handling the InactivityMode and State
 * 
 *----------------------------------------------------------------------------*/
    
    public void setInactivityModeListener(Utils.Callback<InactivityType,Void> listener) {
        inactivityModeListener = listener;
    }
    
    public void requestInactivityMode(InactivityType mode) {
        if (inactivityModeListener != null) {
            inactivityModeListener.call(mode);
        }
    }
    
    public void wakeup() {
        InactivityType current = inactivityState.get();
        if (current != InactivityType.Awake) {
            requestInactivityMode(InactivityType.Awake);
            requestInactivityMode(current);
        }
    }
    
    public boolean isSleeping() { return inactivityState.get() == InactivityType.Sleep; }
    public boolean isDaydreaming() { return inactivityState.get() == InactivityType.Daydream; }
    public boolean isAwake() { return inactivityState.get() == InactivityType.Awake; }
    
    
/*------------------------------------------------------------------------------
 *
 * Managing where application files are stored
 * 
 *----------------------------------------------------------------------------*/
    
    public final File ensureAppFilesFolder() {
        boolean storeFilesWithApp = prefs.storeFilesWithApp.get();
        if (storeFilesWithApp)  return null;

        File aff = getAppFileFolder();
        if (aff.exists()) return aff;
        if (aff.mkdir()) {
            // Since we're creating this folder for the first time, we might
            // need to copy over existing files from the app folder.
            // HACK ALERT!! This code should not know the file names of the
            // the files to be copied! This is pure expediency!
            File srcDir = new File(System.getProperty("user.dir"));
            IOFileFilter logFilter = FileFilterUtils.suffixFileFilter(".stats.log");
            IOFileFilter txtFilter = FileFilterUtils.nameFileFilter("cookies.txt", IOCase.INSENSITIVE);
            IOFileFilter filter = FileFilterUtils.and(
                    FileFilterUtils.or(logFilter, txtFilter), FileFileFilter.FILE);
            try {
                FileUtils.copyDirectory(srcDir, aff, filter);
            } catch (IOException ex) {
                Dialogs.showWarningDialog(stage,
                        "Unable to copy files to Application File Folder: " + aff,
                        "Warning", "VisibleTesla");
            }
            
            return aff;
        }

        Tesla.logger.log(
                Level.WARNING,
                "Could not create Application Files Folder: {0}", aff);
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
        
/*------------------------------------------------------------------------------
 *
 * Managing Threads and Handling clean shutdown
 * 
 *----------------------------------------------------------------------------*/
    
    private int threadID = 0;
    
    public Thread launchThread(Runnable r, String name) {
        Thread t = new Thread(r);
        t.setName(name == null ? ("00 VT - " + threadID++) : name);
        t.setDaemon(true);
        t.start();
        threads.add(t);
        
        // Clean out any old terminated threads...
        Iterator<Thread> i = threads.iterator();
        while (i.hasNext()) {
            Thread cur = i.next();
            if (cur.getState() == Thread.State.TERMINATED) {
                i.remove();
            }
        }
        
        return t;
    }
    
    public void shutDown() {
        shuttingDown.set(true);
        int nActive;
        do {
            nActive = 0;
            for (Thread t : threads) {
                Thread.State state = t.getState();
                switch (state) {
                    case NEW:
                    case RUNNABLE:
                        nActive++;
                        break;
                                
                    case TERMINATED:
                        break;

                    case BLOCKED:
                    case TIMED_WAITING:
                    case WAITING:
                        nActive++;
                        t.interrupt();
                        // Should this sleep for a very short period?
                        break;

                    default: break;
                }
            } 
        } while (nActive > 0);
    }
}
