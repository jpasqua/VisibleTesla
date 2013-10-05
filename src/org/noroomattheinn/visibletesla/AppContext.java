/*
 * AppContext.java -  - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Aug 30, 2013
 */

package org.noroomattheinn.visibletesla;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.prefs.Preferences;
import javafx.application.Application;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.stage.Stage;
import org.noroomattheinn.tesla.GUIState;
import org.noroomattheinn.tesla.Options;
import org.noroomattheinn.tesla.VehicleState;
import org.noroomattheinn.utils.Utils;

/**
 * AppContext - Stores application wide state for use by all of the individual
 * Tabs.
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */

public class AppContext {
    public enum InactivityMode {AllowSleeping, AllowDaydreaming, StayAwake};
    
    public Application app;
    public Stage stage;
    public Preferences prefs;
    
    public GUIState cachedGUIState;
    public VehicleState cachedVehicleState;
    
    public ObjectProperty<InactivityMode> inactivityState;
    public BooleanProperty shuttingDown;
    public Map properties;
    
    public ObjectProperty<Utils.UnitType> simulatedUnits;
    public ObjectProperty<Options.WheelType> simulatedWheels;
    public ObjectProperty<Options.PaintColor> simulatedColor;
    public ObjectProperty<Options.RoofType> simulatedRoof;
    
    private ArrayList<Thread> threads = new ArrayList<>();
    private Utils.Callback<InactivityMode,Void> inactivityModeListener;
    
    AppContext(Application app, Stage stage) {
        this.app = app;
        this.stage = stage;
        this.prefs = Preferences.userNodeForPackage(this.getClass());
        this.inactivityModeListener = null;
        
        this.inactivityState = new SimpleObjectProperty<>(InactivityMode.StayAwake);
        this.shuttingDown = new SimpleBooleanProperty(false);
        this.properties = new HashMap();
        
        this.simulatedUnits = new SimpleObjectProperty<>();
        this.simulatedWheels = new SimpleObjectProperty<>();
        this.simulatedColor = new SimpleObjectProperty<>();
        this.simulatedRoof = new SimpleObjectProperty<>();
    }

    public void setInactivityModeListener(Utils.Callback<InactivityMode,Void> listener) {
        inactivityModeListener = listener;
    }
    
    public void requestInactivityMode(InactivityMode mode) {
        if (inactivityModeListener != null) {
            inactivityModeListener.call(mode);
        }
    }
    
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
