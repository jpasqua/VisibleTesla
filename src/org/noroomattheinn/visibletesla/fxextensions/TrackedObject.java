/*
 * TrackedObject - Copyright(c) 2013, 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Oct 18 16, 2014
 */
package org.noroomattheinn.visibletesla.fxextensions;

import java.util.ArrayList;
import java.util.List;
import javafx.application.Platform;

/**
 * TrackedObject is like an observable object with a listener. Wrap an object of
 * a generic T in a TrackedObject and then you can add trackers. Trackers will be
 * called any time the object is set, EVEN if it is set to the same object or an
 * equal() object. A tracker is like a listener except it is not passed any state.
 * Also, it can be run immediately, or later on the main JavaFX thread. 
 * @author joe
 */
public class TrackedObject<T> {
    private class Tracker {
        boolean runLater;
        Runnable r;
        Tracker(Runnable r, boolean later) { this.r = r; this.runLater = later; }
    }
    private final List<Tracker> trackers;
    private T val;

    private TrackedObject() { this(null); }

    public TrackedObject(T initialVal) {
        trackers = new ArrayList<>(4);
        val = initialVal;
    }

    public T get() { return val; }
    public void set(T newVal) {
        this.val = newVal;
        for (Tracker tracker : trackers) {
            if (tracker.runLater) Platform.runLater(tracker.r);
            else tracker.r.run();
        }
    }

    public void addTracker(boolean runLater, Runnable r) {
        trackers.add(new Tracker(r, runLater));
    }
}
    
