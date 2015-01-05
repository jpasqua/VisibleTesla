/*
 * AppAPI.java - Copyright(c) 2015 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Jan 4, 2015
 */
package org.noroomattheinn.visibletesla;

import org.noroomattheinn.utils.TrackedObject;

/**
 * AppAPI: Interface into VisibleTesla from external components like the
 * REST Server.
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class AppAPI {

/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/

    public enum Mode { AllowSleeping, StayAwake };
    public enum State { Idle, Active };
    
    public final TrackedObject<Mode>    mode;
    public final TrackedObject<State>   state;
    
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

    
/*------------------------------------------------------------------------------
 *
 * Methods/Data used for debugging
 * 
 *----------------------------------------------------------------------------*/
    
    private final TrackedObject<String>schedulerActivityReport;
    
    public void fakeSchedulerActivity(String activityReport) {
        schedulerActivityReport.set(activityReport);
    }
    
/*------------------------------------------------------------------------------
 *
 * Constructor: Package-access only
 * 
 *----------------------------------------------------------------------------*/
    
    AppAPI(TrackedObject<String> schedulerActivityReport) {
        this.mode = new TrackedObject<>(Mode.StayAwake);
        this.state = new TrackedObject<>(State.Active);
        this.schedulerActivityReport = schedulerActivityReport;
    }

}
