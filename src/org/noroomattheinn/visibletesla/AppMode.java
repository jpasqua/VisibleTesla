/*
 * AppMode - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Nov 20, 2014
 */

package org.noroomattheinn.visibletesla;

import static org.noroomattheinn.tesla.Tesla.logger;
import org.noroomattheinn.visibletesla.fxextensions.TrackedObject;

/**
 * AppMode: Represents the mode of the app (AllowSleeping, etc.)
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class AppMode {

/*------------------------------------------------------------------------------
 *
 * Constants and Enums
 * 
 *----------------------------------------------------------------------------*/

    public enum Mode { AllowSleeping, StayAwake };

/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/

    private final AppContext  ac;
    private final TrackedObject<Mode> mode;

/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
            
    public AppMode(AppContext appContext) {
        this.ac = appContext;
        this.mode = new TrackedObject<>(Mode.StayAwake);
        
        mode.addTracker(false, new Runnable() {
            @Override public void run() {
                logger.finest("App Mode changed to " + mode.get());
                Prefs.store().put(
                    ac.vinKey("InactivityMode"), mode.get().name());
                if (mode.get() == Mode.StayAwake) ac.appState.setActive();
            }
        });
    }
    
    public void allowSleeping() { mode.set(Mode.AllowSleeping); }
    public boolean allowingSleeping() { return mode.get() == Mode.AllowSleeping; }
    
    public void stayAwake() { mode.set(Mode.StayAwake); }
    public boolean stayingAwake() { return mode.get() == Mode.StayAwake; }
    
    public void addTracker(boolean later, Runnable r) { mode.addTracker(later, r); }
    public long lastSet() { return mode.lastSet(); }
    
    public void restore() {
        String modeName = Prefs.store().get(
                ac.vinKey("InactivityMode"), Mode.StayAwake.name());
        // Handle obsolete values or changed names
        switch (modeName) {
            case "Sleep": modeName = "AllowSleeping"; break;    // Name Changed
            case "Awake": modeName = "StayAwake"; break;        // Name Changed
            case "AllowDaydreaming": modeName = "Awake"; break; // Obsolete
            case "Daydream": modeName = "Awake"; break;         // Obsolete
        }

        mode.set(Mode.valueOf(modeName));
    }

    @Override public String toString() { return mode.get().name(); }
}
