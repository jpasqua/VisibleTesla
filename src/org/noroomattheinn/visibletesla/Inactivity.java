/*
 * InactivityHandler - Copyright(c) 2013, 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Aug 14, 2014
 */

package org.noroomattheinn.visibletesla;

import java.util.List;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.Tab;
import javafx.scene.input.InputEvent;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import org.noroomattheinn.tesla.Tesla;
import org.noroomattheinn.visibletesla.fxextensions.TrackedObject;


/**
 * Inactivity: All things related to the inactivity mode and state of the fxApp
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class Inactivity {

/*------------------------------------------------------------------------------
 *
 * Constants and Enums
 * 
 *----------------------------------------------------------------------------*/

    public enum Mode { AllowSleeping, StayAwake };
    public enum State { Idle, Active };

/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/

    private final AppContext  appContext;
    private long  timeOfLastEvent = System.currentTimeMillis();

/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public final TrackedObject<State> state;
    public final TrackedObject<Mode> mode;
        
    public Inactivity(AppContext ac) {
        this.appContext = ac;
        this.state = new TrackedObject<>(State.Active);
        this.mode = new TrackedObject<>(Mode.StayAwake);
        
        state.addTracker(false, new Runnable() {
            @Override public void run() {
                if (state.get() == State.Active) {
                    Tesla.logger.info("Resetting Idle start time to now");
                    timeOfLastEvent = System.currentTimeMillis();
                }
            }
        });
        mode.addTracker(false, new Runnable() {
            @Override public void run() {
                appContext.persistentState.put(
                    appContext.vehicle.getVIN()+"_InactivityMode", mode.get().name());
                if (state.get() == State.Idle && mode.get() == Mode.AllowSleeping)
                    state.set(State.Idle);
            }
        });
    }
    
    public void trackInactivity(List<Tab> tabs) {
        for (Tab t : tabs) {
            Node n = t.getContent();
            n.addEventFilter(KeyEvent.ANY, new EventPassThrough());
            n.addEventFilter(MouseEvent.MOUSE_PRESSED, new EventPassThrough());
            n.addEventFilter(MouseEvent.MOUSE_RELEASED, new EventPassThrough());
        }
        appContext.tm.launch(new InactivityThread(), "Inactivity");
    }
    
    public void wakeupApp() { state.set(State.Active); }
    public boolean appIsIdle() { return state.get() == State.Idle; }
    public boolean appIsActive() { return state.get() == State.Active; }
    
    public void restore() {
        String modeName = appContext.persistentState.get(
                appContext.vehicle.getVIN()+"_InactivityMode",
                Inactivity.Mode.StayAwake.name());
        // Handle obsolete values or changed names
        switch (modeName) {
            case "Sleep": modeName = "AllowSleeping"; break;    // Name Changed
            case "Awake": modeName = "StayAwake"; break;        // Name Changed
            case "AllowDaydreaming": modeName = "Awake"; break; // Obsolete
            case "Daydream": modeName = "Awake"; break;         // Obsolete
        }

        mode.set(Inactivity.Mode.valueOf(modeName));
    }
    
/*------------------------------------------------------------------------------
 *
 * PRIVATE - Methods and classes to track activity
 * 
 *----------------------------------------------------------------------------*/
    
//    private String asString(Mode t, String which) {
//        switch (t) {
//            case AllowSleeping: return "Allow Sleeping";
//            case StayAwake: return "Stay Awake";
//        }
//        return "Unexpected " + which;
//    }
    
    class InactivityThread implements Runnable {
        @Override public void run() {
            while (true) {
                appContext.utils.sleep(60 * 1000);
                if (appContext.shuttingDown.get())
                    return;
                long idleThreshold = appContext.prefs.idleThresholdInMinutes.get() * 60 * 1000;
                if (System.currentTimeMillis() - timeOfLastEvent > idleThreshold &&
                        mode.get() == Mode.AllowSleeping) {
                    state.set(State.Idle);
                }
            }
        }
    }
    
    class EventPassThrough implements EventHandler<InputEvent> {
        @Override public void handle(InputEvent ie) {
            timeOfLastEvent = System.currentTimeMillis();
            if (state.get() != State.Active) { state.set(State.Active); }
        }
    }

}
