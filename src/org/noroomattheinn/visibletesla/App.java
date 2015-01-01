/*
 * App.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Aug 30, 2013
 */
package org.noroomattheinn.visibletesla;

import java.io.File;
import java.util.List;
import javafx.application.Application;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.Tab;
import javafx.scene.input.InputEvent;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import org.noroomattheinn.tesla.Tesla;
import static org.noroomattheinn.tesla.Tesla.logger;
import org.noroomattheinn.utils.Utils;
import static org.noroomattheinn.utils.Utils.timeSince;
import org.noroomattheinn.fxextensions.TrackedObject;

/**
 * App - Stores state about the app for use across the app. This is a singleton
 * that is created at app startup.
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class App {

/*------------------------------------------------------------------------------
 *
 * Constants and Enums
 * 
 *----------------------------------------------------------------------------*/
    
    public static final String ProductName = "VisibleTesla";
    public static final String ProductVersion = "0.32.00";

    public enum Mode { AllowSleeping, StayAwake };
    public enum State { Idle, Active };
    
/*------------------------------------------------------------------------------
 *
 * PUBLIC - Application State
 * 
 *----------------------------------------------------------------------------*/
    
    public final Application            fxApp;
    public final Tesla                  tesla;
    public final Stage                  stage;
    public final TrackedObject<String>  schedulerActivity;
    public final TrackedObject<Mode>    mode;
    public final TrackedObject<State>   state;

/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private static App  instance = null;
    
    private final File  appFilesFolder;
    private long        lastEventTime;

/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    /**
     * Called once when the app starts in order to create the singleton. This is
     * logically a separate factory, but it is here for convenience.
     * @param fxApp
     * @param stage
     * @return  The newly created singleton or the existing singleton if already
     *          created.
     */
    public static App create(Application fxApp, Stage stage) {
        if (instance != null) { return instance; }
        return (instance = new App(fxApp, stage));
    }

    /**
     * Fetch the singleton App instance.
     * @return  The singleton App instance
     */
    public static App get() { return instance; }

    /**
     * Establish ourselves as the only running instance of the app for
     * a particular vehicle id.
     * @return  true is we got the lock
     *          false if another instance is already running
     */
    public boolean lock(String vin) {
        return (Utils.obtainLock(vin + ".lck", appFilesFolder));
    }

    /**
     * Convenience function to return a Prefs key that is prefixed by the VIN
     * of the current vehicle.
     * @param key   The raw Prefs key
     * @return      The Prefs key prefixed by the VIN
     */
    public final String vinKey(String key) { return VTVehicle.get().vinKey(key); }

    /**
     * Get the system folder in which app related files are to be stored.
     * @return  The folder in which app related files are to be stored
     */
    public File appFileFolder() { return appFilesFolder; }

/*------------------------------------------------------------------------------
 *
 * Methods related to the App Mode
 * 
 *----------------------------------------------------------------------------*/
    
    /**
     * Change the app mode to AllowSleeping
     */
    public void allowSleeping() { mode.set(Mode.AllowSleeping); }
    
    /**
     * Determine whether we are in AllowSleeping mode
     * @return  true if we are in AllowSleeping mode
     *          false otherwise
     */
    public boolean allowingSleeping() { return mode.get() == Mode.AllowSleeping; }

    /**
     * Change the app mode to StayAwake
     */
    public void stayAwake() { mode.set(Mode.StayAwake); }
    
    /**
     * Determine whether we are in StayAwake mode
     * @return  true if we are in StayAwake mode
     *          false otherwise
     */
    public boolean stayingAwake() { return mode.get() == Mode.StayAwake; }

    /**
     * Set the mode based on the value in the persistent store
     */
    public void restoreMode() {
        String modeName = Prefs.store().get(vinKey("InactivityMode"), Mode.StayAwake.name());
        // Handle obsolete values or changed names
        switch (modeName) {
            case "Sleep": modeName = "AllowSleeping"; break;    // Name Changed
            case "Awake": modeName = "StayAwake"; break;        // Name Changed
            case "AllowDaydreaming": modeName = "Awake"; break; // Obsolete
            case "Daydream": modeName = "Awake"; break;         // Obsolete
            }
        mode.set(Mode.valueOf(modeName));
    }
    
/*------------------------------------------------------------------------------
 *
 * Methods related to the App State
 * 
 *----------------------------------------------------------------------------*/
    
    /**
     * Determine whether the app is in the Active state
     * @return  true if we are in Active state
     *          false otherwise
     */
    public boolean isActive() { return state.get() == State.Active; }
    
    /**
     * Put the app into the Active state
     */
    public void setActive() { state.set(State.Active); }
    
    /**
     * Determine whether the app is in the Idle state
     * @return  true if we are in Active state
     *          false otherwise
     */
    public boolean isIdle() { return state.get() == State.Idle; }
    
    /**
     * Put the app into the Idle state
     */
    public void setIdle() { state.set(State.Idle); }

    /**
     * Begin watching for user inactivity (keyboard input, mouse movements, etc.)
     * on any of the specified Tabs.
     * @param tabs  Watch for user activity targeted to any of these tabs.
     */
    public void watchForUserActivity(List<Tab> tabs) {
        for (Tab t : tabs) {
            Node n = t.getContent();
            n.addEventFilter(KeyEvent.ANY, new EventPassThrough());
            n.addEventFilter(MouseEvent.MOUSE_PRESSED, new EventPassThrough());
            n.addEventFilter(MouseEvent.MOUSE_RELEASED, new EventPassThrough());
        }
        ThreadManager.get().launch(new InactivityThread(), "Inactivity");
    }

/*------------------------------------------------------------------------------
 *
 * Hide the constructor for the singleton
 * 
 *----------------------------------------------------------------------------*/
    
    /**
     * Create an App object
     * @param fxApp The JavaFX Application object
     * @param stage The JavaFX stage of the main window
     */
    private App(Application fxApp, Stage stage) {
        Prefs prefs = Prefs.get();
        this.fxApp = fxApp;
        this.stage = stage;
        this.lastEventTime = System.currentTimeMillis();

        this.mode = new TrackedObject<>(Mode.StayAwake);
        this.mode.addTracker(false, new Runnable() {
            @Override public void run() {
                logger.finest("App Mode changed to " + mode.get());
                Prefs.store().put(vinKey("InactivityMode"), mode.get().name());
                if (mode.get() == Mode.StayAwake) { setActive(); }
            }
        });

        this.state = new TrackedObject<>(State.Active);
        this.state.addTracker(false, new Runnable() {
            @Override public void run() {
                logger.finest("App State changed to " + state.get());
                if (state.get() == State.Active) {
                    logger.info("Resetting Idle start time to now");
                    lastEventTime = System.currentTimeMillis();
                }
            }
        });

        appFilesFolder = Utils.ensureAppFilesFolder(ProductName);
        Utils.setupLogger(appFilesFolder, "visibletesla", logger, prefs.getLogLevel());

        tesla = (prefs.enableProxy.get())
                ? new Tesla(prefs.proxyHost.get(), prefs.proxyPort.get()) : new Tesla();

        this.schedulerActivity = new TrackedObject<>("");
    }

/*------------------------------------------------------------------------------
 *
 * PRIVATE - Methods and classes to track activity
 * 
 *----------------------------------------------------------------------------*/
    
    class InactivityThread implements Runnable {
        @Override public void run() {
            while (true) {
                ThreadManager.get().sleep(60 * 1000);
                if (ThreadManager.get().shuttingDown()) {
                    return;
                }
                long idleThreshold = Prefs.get().idleThresholdInMinutes.get() * 60 * 1000;
                if (timeSince(lastEventTime) > idleThreshold && allowingSleeping()) {
                    state.update(State.Idle);
                }
            }
        }
    }

    class EventPassThrough implements EventHandler<InputEvent> {
        @Override public void handle(InputEvent ie) {
            lastEventTime = System.currentTimeMillis();
            state.update(State.Active);
        }
    }
}
