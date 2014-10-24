/*
 * GenericTrigger.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Dec 14, 2013
 */

package org.noroomattheinn.visibletesla.trigger;

import java.util.Comparator;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import org.noroomattheinn.tesla.Tesla;
import org.noroomattheinn.visibletesla.AppContext;

/**
 * GenericTrigger: A generic trigger mechanism that handles a number of different
 * predicates on an input type.
 * 
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class GenericTrigger<T> {
/*------------------------------------------------------------------------------
 *
 * Cosntants and Enums
 * 
 *----------------------------------------------------------------------------*/
    
    public enum Predicate {FallsBelow, HitsOrExceeds, Becomes, AnyChange, GT, LT, EQ};
    public interface RW<T> extends Comparator<T> {
        public String toExternal(T value);
        public T fromExternal(String external);
        public String formatted(T value);
        public boolean isAny(T value);
    }
    
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private final AppContext        ac;
    private final String            triggerName;
    private final String            key;
    private final BooleanProperty   isEnabled;
    private final ObjectProperty<T> targetProperty;
    private final T                 targetDefault;
    private final Predicate         predicate;
    private final long              bounceInterval;
    private final RW<T>             th;
    
    private T                       curVal, lastVal;
    private long                    lastTimeSatisfied;
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public GenericTrigger(AppContext ac, BooleanProperty isEnabled, RW<T> th,
            String name, String key, Predicate predicate,
            ObjectProperty<T> targetProperty, T targetDefault,
            long bounceInterval) {
        this.ac = ac;
        this.triggerName = name;
        this.key = key;
        this.isEnabled = isEnabled;
        this.targetProperty = targetProperty;
        this.targetDefault = targetDefault;
        this.predicate = predicate;
        this.bounceInterval = bounceInterval;
        this.th = th;
        
        this.lastTimeSatisfied = 0;
        this.curVal = this.lastVal = null;
        
        targetProperty.addListener(new ChangeListener<T>() {
            @Override public void changed(ObservableValue<? extends T> ov, T t, T t1)
            { externalize(); }
        });
        isEnabled.addListener(new ChangeListener<Boolean>() {
            @Override public void changed(ObservableValue<? extends Boolean> ov, Boolean t, Boolean t1)
            { externalize(); }
        });
        internalize();
    }
    
    public String getTriggerName() { return triggerName; }
    public Predicate getPredicateType() { return predicate; }
    public String getPredicateName() { 
        switch (predicate) {
            case FallsBelow: return "fell below";
            case HitsOrExceeds: return "hit or exceeded";
            case Becomes: return "became";
            case AnyChange: return "occurred";
            case GT: return "is greater than";
            case LT: return "is less than";
            case EQ: return "is equal to";
        }
        return predicate.name();
    }
    
    public String getCurrentVal() {
        return curVal == null ? "" : th.formatted(curVal);
    }
    
    public String getTargetVal() {
        T triggerVal = targetProperty.get();
        return triggerVal == null ? "" : th.formatted(triggerVal);
    }

    public boolean evalPredicate(T newVal) {
        curVal = newVal;
        if (isEnabled.get() && !bouncing()) {
            if (satisfied(newVal)) {
                lastTimeSatisfied = System.currentTimeMillis();
                return true;
            }
        }
        return false;
    }
    
    public String defaultMessage() {
        String val = getCurrentVal();
        String targetVal = getCurrentVal();
        String pName = getPredicateName();
        switch (predicate) {
            case HitsOrExceeds:
            case FallsBelow:
                return String.format("%s %s %s (%s)",
                    triggerName, pName, targetVal, val);
            case Becomes:
                return String.format("%s became: %s", triggerName, val);
            case AnyChange:
                return String.format("%s Activity: %s", triggerName, val);
            case EQ:
            case LT:
            case GT:
                return String.format("%s %s %s", triggerName, pName, targetVal);
        }
        // If we ever get here it is a bug in the code - I added a type
        // and didn't account for it in the switch. Do something useful...
        Tesla.logger.severe("Unexpected Predicate type: " + pName);
        return String.format(
                "%s %s %s (%s)", triggerName, pName, targetVal, curVal);
    }
    
/*------------------------------------------------------------------------------
 *
 * Private Methods to evaluate the predicate
 * 
 *----------------------------------------------------------------------------*/
    
    private boolean satisfied(T current) {
        boolean satisfied = false;
        T targetVal = targetProperty.get();

        if (predicate == Predicate.AnyChange) {
            satisfied = true;
        } else if (predicate == Predicate.GT) {
            satisfied = th.compare(current, targetVal) > 0;
        } else if (predicate == Predicate.LT) {
            satisfied = th.compare(current, targetVal) < 0;
        } else if (predicate == Predicate.EQ) {
            satisfied = th.compare(current, targetVal) == 0;
        } else if (lastVal != null) {
            if (predicate == Predicate.FallsBelow) {
                satisfied = th.compare(lastVal, targetVal) >= 0 &&
                            th.compare(current, targetVal) <  0;
            } else if (predicate == Predicate.HitsOrExceeds) {
                satisfied = th.compare(lastVal, targetVal) <  0 &&
                            th.compare(current, targetVal) >= 0;
            } else if (predicate == Predicate.Becomes) {
                if (th.isAny(targetVal)) {
                    satisfied = (th.compare(lastVal, current) != 0);
                } else {
                    satisfied = th.compare(lastVal, current) != 0 &&
                                th.compare(current, targetVal) == 0;
                }
            }
        }
        lastVal = current;

        return satisfied;
    }
    
    private boolean bouncing() {
        if (bounceInterval == 0) return false;
        return (System.currentTimeMillis() - lastTimeSatisfied < bounceInterval);
    }
    
/*------------------------------------------------------------------------------
 *
 * Private Methods for internalizing and externalizing a Trigger
 * 
 *----------------------------------------------------------------------------*/
    
    private String fullKey() { return ac.vehicle.getVIN()+"_"+key; }
    private String onOff(boolean b) { return b ? "1" : "0"; }
    
    
    private void externalize() {
        String encoded = String.format("%s_%s", onOff(isEnabled.get()), th.toExternal(targetProperty.get()));
        ac.persistentState.put(fullKey(), encoded);
    }
    
    private void internalize() {
        String dfltEncoded = th.toExternal(targetDefault);
        String encoded = ac.persistentState.get(fullKey(), "0_" + dfltEncoded);
        String[] elements = encoded.split("_");
        if (elements.length >= 2) {
            isEnabled.set(elements[0].equals("1"));
            targetProperty.set(th.fromExternal(elements[1]));
        } else {
            isEnabled.set(false);
            targetProperty.set(th.fromExternal(dfltEncoded));
            Tesla.logger.warning("Malformed externalized trigger: " + encoded);
        }
    }
}
