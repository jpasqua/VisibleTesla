/*
 * TrackedObject - Copyright(c) 2013, 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Oct 18 16, 2014
 */
package org.noroomattheinn.fxextensions;

import java.util.ArrayList;
import java.util.List;
import javafx.application.Platform;

/**
 * TrackedObject is like an observable object with a listener. Wrap an object of
 * a generic T in a TrackedObject and then you can add trackers. Trackers will be
 * called any time the object is set, EVEN if it is set to the same object or an
 * equal() object. A tracker is like a listener except it is not passed any state.
 * Also, it can be run immediately, or later on the main JavaFX thread. 
 * 
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class TrackedObject<T> {
    private class Tracker {
        boolean runLater;
        Runnable r;
        Tracker(Runnable r, boolean later) { this.r = r; this.runLater = later; }
    }
    
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private final List<Tracker> trackers;
    private T val;
    private long lastSet = 0;
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/

    /**
     * Create a TrackedObject with the specified initial value
     * @param initialVal    The initial value of the TrackedObject
     */
    public TrackedObject(T initialVal) {
        trackers = new ArrayList<>(4);
        val = initialVal;
        lastSet = System.currentTimeMillis();
    }

    /**
     * Return the current value of the TrackedObject
     * @return  The current value
     */
    public T get() { return val; }
    
    /**
     * Set the value of the TrackedObject and call all of the trackers.
     * Note that the trackers are called even if the old and new values are
     * the same object. The lastSet time of the TrackedObject is updated even if
     * the old and new values are the same.
     * @param newVal    The new value for the TrackedObject
     */
    public void set(T newVal) {
        this.val = newVal;
        this.lastSet = System.currentTimeMillis();
        for (Tracker tracker : trackers) {
            if (tracker.runLater) Platform.runLater(tracker.r);
            else tracker.r.run();
        }
    }
    
    /**
     * Update the value of the TrackedObject if and only if the old and new
     * values are different. If they are, then this is equivalent to calling
     * set(). If the values are the same object, then this is a no-op. No
     * trackers will be called.
     * @param newVal    The new value for the TrackedObject
     */
    public void update(T newVal) { if (val != newVal)  set(newVal); }

    /**
     * Add a tracker to be called whenever the value is set or updated.
     * @param runLater  Should the tracker be called later on the JavaFX thread
     * @param r         The runnable to call
     */
    public void addTracker(boolean runLater, Runnable r) {
        trackers.add(new Tracker(r, runLater));
    }
    
    /**
     * Returns the time at which the value of the TrackedObject was last set
     * @return  The last set time
     */
    public long lastSet() { return lastSet; }
}
    
