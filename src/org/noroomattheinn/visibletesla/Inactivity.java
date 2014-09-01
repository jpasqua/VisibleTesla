/*
 * InactivityHandler - Copyright(c) 2013, 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Aug 14, 2014
 */

package org.noroomattheinn.visibletesla;

import java.util.ArrayList;
import java.util.List;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.Tab;
import javafx.scene.input.InputEvent;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import org.noroomattheinn.tesla.Tesla;
import org.noroomattheinn.utils.Utils;


/**
 * Inactivity: All things related to the inactivity mode and state of the app
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class Inactivity {

/*------------------------------------------------------------------------------
 *
 * Constants and Enums
 * 
 *----------------------------------------------------------------------------*/

    public enum Type { Sleep, Daydream, Awake };
    public interface Listener { public void handle(Inactivity.Type type); }
    
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/

    private final ObjectProperty<Type> state;
    private final ObjectProperty<Type> mode;
    private final AppContext  appContext;
    private final Notifier modeNotifier = new Notifier();
    private final Notifier stateNotifier = new Notifier();
    private long  timeOfLastEvent = System.currentTimeMillis();

/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    
    public Inactivity(AppContext appContext) {
        this.appContext = appContext;
        this.state = new SimpleObjectProperty<>(Type.Awake);
        this.mode = new SimpleObjectProperty<>(Type.Awake);
        state.addListener(new ChangeListener<Type>() {
            @Override public void changed(
                    ObservableValue<? extends Type> o, Type ov, Type nv) {
                if (nv == Type.Awake && ov != Type.Awake) {
                    Tesla.logger.info("Resetting Idle start time to now");
                    timeOfLastEvent = System.currentTimeMillis();
                }
            }
        });
        state.addListener(stateNotifier);
        mode.addListener(modeNotifier);
    }

    public Type getState() { return state.get(); }
    public void setState(Type newState) { state.set(newState); }
    public void addStateListener(Listener l) { stateNotifier.addListener(l); }
    public String stateAsString() { return asString(state.get(), "state"); }

    public Type getMode() { return mode.get(); }
    public void setMode(Type newMode) {
        mode.set(newMode);      
        appContext.persistentState.put(appContext.vehicle.getVIN()+"_InactivityMode", newMode.name());
        
        if (state.get() == Type.Awake) return;
        state.set(newMode);
    }
    public String modeAsString() { return asString(mode.get(), "mode"); }
    public void addModeListener(Listener l) {modeNotifier.addListener(l); }
    
    public void trackInactivity(List<Tab> tabs) {
        for (Tab t : tabs) {
            Node n = t.getContent();
            n.addEventFilter(KeyEvent.ANY, new EventPassThrough());
            n.addEventFilter(MouseEvent.MOUSE_PRESSED, new EventPassThrough());
            n.addEventFilter(MouseEvent.MOUSE_RELEASED, new EventPassThrough());
        }
        appContext.tm.launch(new InactivityThread(), "Inactivity");
    }
    
    public void wakeup() {
        Type current = state.get();
        if (current != Type.Awake) {
            setMode(Type.Awake);
            setMode(current);
        }
    }

    public boolean isSleeping() { return state.get() == Type.Sleep; }
    public boolean isDaydreaming() { return state.get() == Type.Daydream; }
    public boolean isAwake() { return state.get() == Type.Awake; }
    
    public void restore() {
        String modeName = appContext.persistentState.get(
                appContext.vehicle.getVIN()+"_InactivityMode",
                Inactivity.Type.Daydream.name());
        // The names changed, do any required fixup of old stored values!
        switch (modeName) {
            case "AllowSleeping": modeName = "Sleep"; break;
            case "AllowDaydreaming": modeName = "Daydream"; break;
            case "StayAwake": modeName = "Awake"; break;
        }

        appContext.inactivity.setMode(Inactivity.Type.valueOf(modeName));
    }
    
/*------------------------------------------------------------------------------
 *
 * PRIVATE - Methods and classes to track activity
 * 
 *----------------------------------------------------------------------------*/
    
    private class Notifier implements ChangeListener<Inactivity.Type> {
        private List<Listener> listeners = new ArrayList<>(5);
                
        @Override public void changed(
                final ObservableValue<? extends Inactivity.Type> o,
                final Inactivity.Type ov, final Inactivity.Type nv) {
            Platform.runLater(new Runnable() {
                @Override public void run() { for (Listener l : listeners) { l.handle(nv); } }
            });
        }
        
        public void addListener(Listener l) { listeners.add(l); }
    }

    private String asString(Type t, String which) {
        switch (t) {
            case Sleep: return "Allow Sleeping";
            case Daydream: return "Allow Daydreaming";
            case Awake: return "Stay Awake";
        }
        return "Unexpected " + which;
    }
    
    class InactivityThread implements Runnable {
        @Override public void run() {
            while (true) {
                Utils.sleep(60 * 1000);
                if (appContext.shuttingDown.get())
                    return;
                long idleThreshold = appContext.prefs.idleThresholdInMinutes.get() * 60 * 1000;
                if (System.currentTimeMillis() - timeOfLastEvent > idleThreshold) {
                    state.set(mode.get());
                }
            }
        }
    }
    
    class EventPassThrough implements EventHandler<InputEvent> {
        @Override public void handle(InputEvent ie) {
            timeOfLastEvent = System.currentTimeMillis();
            state.set(Type.Awake);
        }
    }

}
