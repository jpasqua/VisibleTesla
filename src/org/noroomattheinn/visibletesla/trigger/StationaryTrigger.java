/*
 * StationaryTrigger.java - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Oct 2, 2014
 */
package org.noroomattheinn.visibletesla.trigger;

import java.math.BigDecimal;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;

/**
 * StationaryTrigger: Determines whether the car has remained stationary and
 * in Park for a given period of time.
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class StationaryTrigger {
    private long periodBegan;
    private final BooleanProperty isEnabled;
    private final ObjectProperty<BigDecimal> threshold;
    private boolean alreadyTriggered = false;
    
    /**
     * An object that monitors whether the doors have been forgotten unlocked
     * @param isEnabled A property that indicates whether this trigger is active
     * @param threshold A period of time (in minutes) that the car must have
     *                  been stationary before the trigger activates.
     */
    public StationaryTrigger(BooleanProperty isEnabled,
            ObjectProperty<BigDecimal> threshold) {
        this.threshold = threshold;
        this.isEnabled = isEnabled;
        this.periodBegan = -1;
    }

    public boolean evalPredicate(double speed, String shiftState) {
        if (!isEnabled.get()) return false;
        if (speed > 0.0 || !shiftState.equals("P")) {
            alreadyTriggered = false;
            periodBegan = -1;
            return false;
        }
        if (alreadyTriggered) {
            periodBegan = -1;
            return false;
        }
        long now = System.currentTimeMillis();
        if ((periodBegan > 0) && (now - periodBegan > thresholdInMillis())) {
            alreadyTriggered = true;
            return true;
        } else {
            if (periodBegan < 0) periodBegan = now;
            return false;
        }
    }

    private long thresholdInMillis() {
        return threshold.get().longValue() * (60 * 1000);
    }
}
