/*
 * AppContext.java -  - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Aug 30, 2013
 */

package org.noroomattheinn.visibletesla;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;
import java.util.prefs.Preferences;
import javafx.application.Application;
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
import org.apache.commons.lang3.SystemUtils;
import org.noroomattheinn.tesla.GUIState;
import org.noroomattheinn.tesla.Options;
import org.noroomattheinn.tesla.Tesla;
import org.noroomattheinn.tesla.VehicleState;
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
    public static final String ProductVersion = "0.23.00";
    public static final String ResourceDir = "/org/noroomattheinn/TeslaResources/";
    
    public enum InactivityType {Sleep, Daydream, Awake};
    
/*------------------------------------------------------------------------------
 *
 * PUBLIC - Application State
 * 
 *----------------------------------------------------------------------------*/
    
    public Application app;
    public Stage stage;
    public Preferences prefs;
    public Prefs thePrefs;
    public File appFilesFolder;
    
    public GUIState cachedGUIState;
    public VehicleState cachedVehicleState;
    
    public ObjectProperty<InactivityType> inactivityState;
    public BooleanProperty shuttingDown;
    public Map properties;
    
    public ObjectProperty<Utils.UnitType> simulatedUnits;
    public ObjectProperty<Options.WheelType> simulatedWheels;
    public ObjectProperty<Options.PaintColor> simulatedColor;
    public ObjectProperty<Options.RoofType> simulatedRoof;
    
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private ArrayList<Thread> threads = new ArrayList<>();
    private Utils.Callback<InactivityType,Void> inactivityModeListener;
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    AppContext(Application app, Stage stage) {
        this.app = app;
        this.stage = stage;
        this.prefs = Preferences.userNodeForPackage(this.getClass());
        this.inactivityModeListener = null;
        
        this.inactivityState = new SimpleObjectProperty<>(InactivityType.Awake);
        this.shuttingDown = new SimpleBooleanProperty(false);
        this.properties = new HashMap();
        
        this.simulatedUnits = new SimpleObjectProperty<>();
        this.simulatedWheels = new SimpleObjectProperty<>();
        this.simulatedColor = new SimpleObjectProperty<>();
        this.simulatedRoof = new SimpleObjectProperty<>();
        
        this.thePrefs = new Prefs(this);
        
        appFilesFolder = ensureAppFilesFolder();
        
        establishProxy();
    }

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
        boolean storeFilesWithApp = thePrefs.storeFilesWithApp.get();
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
    
    private void establishProxy() {
        if (thePrefs.enableProxy.get()) {
            System.setProperty("http.proxyHost", thePrefs.proxyHost.get());
            System.setProperty("http.proxyPort", String.valueOf(thePrefs.proxyPort.get()));
            System.setProperty("https.proxyHost", thePrefs.proxyHost.get());
            System.setProperty("https.proxyPort", String.valueOf(thePrefs.proxyPort.get()));
        }
    }
    
/*------------------------------------------------------------------------------
 *
 * Managing Threads and Handling clean shutdown
 * 
 *----------------------------------------------------------------------------*/
    
    private int threadID = 0;
    
    public Thread launchThread(Runnable r, String name) {
        Thread t = new Thread(r);
        t.setName(name == null ? ("00 VT - " + threadID) : name);
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
