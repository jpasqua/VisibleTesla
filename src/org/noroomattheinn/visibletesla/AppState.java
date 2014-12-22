/*
 * AppState - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Nov 20, 2014
 */

package org.noroomattheinn.visibletesla;

import java.util.List;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.Tab;
import javafx.scene.input.InputEvent;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import static org.noroomattheinn.tesla.Tesla.logger;
import static org.noroomattheinn.utils.Utils.timeSince;
import org.noroomattheinn.visibletesla.fxextensions.TrackedObject;


/**
 * AppState: Track the active/inactive state of the application
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class AppState {

/*------------------------------------------------------------------------------
 *
 * Constants and Enums
 * 
 *----------------------------------------------------------------------------*/

    public enum State { Idle, Active };

/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/

    private final AppContext  ac;
    private final TrackedObject<State> state;
    private long  lastEventTime = System.currentTimeMillis();

/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
        
    public AppState(AppContext ac) {
        this.ac = ac;
        this.state = new TrackedObject<>(State.Active);
        
        state.addTracker(false, new Runnable() {
            @Override public void run() {
                logger.finest("App State changed to " + state.get());
                if (state.get() == State.Active) {
                    logger.info("Resetting Idle start time to now");
                    lastEventTime = System.currentTimeMillis();
                }
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
        ThreadManager.get().launch(new InactivityThread(), "Inactivity");
    }
    
    public void setActive() { state.set(State.Active); }
    public boolean isActive() { return state.get() == State.Active; }
    
    public void setIdle() { state.set(State.Idle); }
    public boolean isIdle() { return state.get() == State.Idle; }
    
    public void addTracker(boolean later, Runnable r) { state.addTracker(later, r); }
    public long lastSet() { return state.lastSet(); }
    
    @Override public String toString() { return state.get().name(); }

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
                ac.sleep(60 * 1000);
                if (ThreadManager.get().shuttingDown()) return;
                long idleThreshold = Prefs.get().idleThresholdInMinutes.get() * 60 * 1000;
                if (timeSince(lastEventTime) > idleThreshold && ac.appMode.allowingSleeping()) {
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
