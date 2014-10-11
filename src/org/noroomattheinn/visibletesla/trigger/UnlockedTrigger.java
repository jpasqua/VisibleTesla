/*
 * UnlockedTrigger.java - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Oct 2, 2014
 */
package org.noroomattheinn.visibletesla.trigger;

import java.math.BigDecimal;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;

/**
 * UnlockedTrigger: Determines whether the car has remained unlocked even after
 * it has been parked for a while.
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class UnlockedTrigger {
    private long periodBegan;
    private final BooleanProperty isEnabled;
    private final ObjectProperty<BigDecimal> threshold;
    private boolean alreadyTriggered = false;
    
    /**
     * An object that monitors whether the doors have been forgotten unlocked
     * @param isEnabled A property that indicates whether this trigger is active
     * @param threshold A period of time (in minutes) that the doors must have
     *                  been unlocked before the trigger activates.
     */
    public UnlockedTrigger(BooleanProperty isEnabled,
            ObjectProperty<BigDecimal> threshold) {
        this.threshold = threshold;
        this.isEnabled = isEnabled;
        this.periodBegan = -1;
    }

    public boolean unlocked(double speed, String shiftState) {
        if (!isEnabled.get()) return false;
        if (shiftState == null || shiftState.isEmpty()) shiftState = "P";
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
        long millis = threshold.get().longValue() * (60 * 1000);
        return millis;
    }
}
