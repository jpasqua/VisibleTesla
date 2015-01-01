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
import org.noroomattheinn.utils.MailGun;
import org.noroomattheinn.utils.Utils;
import static org.noroomattheinn.utils.Utils.timeSince;
import org.noroomattheinn.visibletesla.fxextensions.TrackedObject;

/**
 * App - Stores application-wide state for use by all of the individual
 * Tabs and provides a number of utility methods.
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
    
    public final Application fxApp;
    public final Tesla tesla;
    public final Stage stage;
    public final MailGun mailer;
    public CommandIssuer issuer;
    public final TrackedObject<String> schedulerActivity;
    public final TrackedObject<Mode> mode;
    public final TrackedObject<State> state;

/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private static App instance = null;
    private final File appFilesFolder;
    private long lastEventTime = System.currentTimeMillis();

/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public static App create(Application app, Stage stage) {
        if (instance != null) { return instance; }
        return (instance = new App(app, stage));
    }

    public static App get() { return instance; }

    public boolean lock() {
        return (Utils.obtainLock(VTVehicle.get().getVehicle().getVIN() + ".lck", appFilesFolder));
    }

    public final String vinKey(String key) { return VTVehicle.get().vinKey(key); }

    public File appFileFolder() { return appFilesFolder; }

/*------------------------------------------------------------------------------
 *
 * Methods related to the App Mode
 * 
 *----------------------------------------------------------------------------*/
    
    public void allowSleeping() { mode.set(Mode.AllowSleeping); }
    public boolean allowingSleeping() { return mode.get() == Mode.AllowSleeping; }

    public void stayAwake() { mode.set(Mode.StayAwake); }
    public boolean stayingAwake() { return mode.get() == Mode.StayAwake; }

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
    
    public void setActive() { state.set(State.Active); }
    public boolean isActive() { return state.get() == State.Active; }

    public void setIdle() { state.set(State.Idle); }
    public boolean isIdle() { return state.get() == State.Idle; }

    public void trackInactivity(List<Tab> tabs) {
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
    
    private App(Application app, Stage stage) {
        Prefs prefs = Prefs.get();
        this.fxApp = app;
        this.stage = stage;

        this.mode = new TrackedObject<>(Mode.StayAwake);
        this.mode.addTracker(false, new Runnable() {
            @Override public void run() {
                logger.finest("App Mode changed to " + mode.get());
                Prefs.store().put(
                        vinKey("InactivityMode"), mode.get().name());
                if (mode.get() == Mode.StayAwake) {
                    setActive();
                }
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

        mailer = new MailGun("api", prefs.useCustomMailGunKey.get()
                ? prefs.mailGunKey.get() : Prefs.MailGunKey);
        issuer = new CommandIssuer();
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
