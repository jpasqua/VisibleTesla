/*
 * Trigger.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Dec 14, 2013
 */

package org.noroomattheinn.visibletesla.trigger;

import java.util.Calendar;
import java.util.GregorianCalendar;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import org.noroomattheinn.tesla.Tesla;
import org.noroomattheinn.visibletesla.AppContext;

/**
 * Trigger: Encapsulates a Subject, Predicate, and Target and triggers when
 * they have the desired relationship.
 * 
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class Trigger<T extends Comparable<T>> {
        
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private AppContext ac;
    private BooleanProperty isEnabled;
    private Subject<T> subject;
    private Predicate<T> predicate;
    private Target<T> target;
    private ObjectProperty<Calendar> timeTarget;
    
    private Calendar lastDaySatisfied;
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public Trigger(AppContext ac, BooleanProperty isEnabled,
            Subject<T> subject,
            Predicate<T> predicate,
            Target<T> target) {
        this.ac = ac;
        this.isEnabled = isEnabled;
        this.subject = subject;
        this.predicate = predicate;
        this.target = target;
    }
    
    public Trigger(AppContext ac, BooleanProperty isEnabled, RW<T> th,
            String name, String key, 
            Predicate.Type predicateType,
            ObjectProperty<T> targetProperty, T targetDefault) {
        this(ac, isEnabled, th, name, key, predicateType,
             null, targetProperty, targetDefault);
    }
    
    public Trigger(AppContext ac, BooleanProperty isEnabled, RW<T> th,
            String name, String key, 
            Predicate.Type predicateType, ObjectProperty<Calendar> timeTarget,
            ObjectProperty<T> targetProperty, T targetDefault) {
        this.ac = ac;
        this.isEnabled = isEnabled;
        subject = new Subject<>(name, key, th);
        target = new Target<>(targetProperty, targetDefault, th);
        predicate = new Predicate<>(predicateType, target);
        this.timeTarget = timeTarget;
    }
    
    /**
     * init must be called immediately after the constructor and before the 
     * instance is used for any other purpose.
     */
    public void init() {
        lastDaySatisfied = new GregorianCalendar();
        lastDaySatisfied.add(Calendar.DAY_OF_YEAR, -1);
        target.setListener(this);
        isEnabled.addListener(new ChangeListener<Boolean>() {
            @Override public void changed(
                    ObservableValue<? extends Boolean> ov, Boolean t, Boolean t1) {
                externalize();
            }
        });
        if (timeTarget != null) {
            timeTarget.addListener(new ChangeListener<Calendar>() {
                @Override public void changed(
                        ObservableValue<? extends Calendar> ov, Calendar t, Calendar t1) {
                    externalize();
                }
            });
        }
        internalize();
    }
    
    public String evalPredicate(T newVal) {
        subject.set(newVal);
        if (isEnabled.get()) {
            
            if (!satisfiesTimeTrigger()) return null;
            
            if (predicate.satisfied(subject.get())) {
                lastDaySatisfied = new GregorianCalendar();
                Predicate.Type type = predicate.getType();
                switch (type) {
                    case HitsOrExceeds:
                    case FallsBelow:
                        return String.format("%s %s %s. Current value: %s",
                            subject.getName(), predicate.toString(),
                            target.formatted(), subject.formatted());
                    case Becomes:
                        return String.format("%s became: %s",
                            subject.getName(), subject.formatted());
                    case AnyChange:
                        return String.format("%s Activity: %s",
                            subject.getName(), subject.formatted());
                    case EQ:
                    case LT:
                    case GT:
                        return String.format("%s %s %s",
                            subject.getName(), predicate.toString(), target.formatted());
                }
                // If we ever get here it is a bug in the code - I added a type
                // and didn't account for it in the switch. Do something useful...
                return String.format(
                        "%s %s %s. Current value: %s",
                        subject.getName(), predicate.toString(),
                        target.formatted(), subject.formatted());
            }
        }
        return null;
    }
    
    public void externalize() {
        String tt = "HH:MM";
        if (timeTarget != null) {
            tt = String.format("%02d:%02d",
                    timeTarget.get().get(Calendar.HOUR_OF_DAY),
                    timeTarget.get().get(Calendar.MINUTE));
        }
        String encoded = String.format(
            "%s_%s_%s", onOff(isEnabled.get()), target.toExternal(), tt);
        ac.persistentState.put(fullKey(), encoded);
    }
    
    public void internalize() {
        String dfltEncoded = target.dfltToExternal();
        String encoded = ac.persistentState.get(fullKey(), "0_" + dfltEncoded);
        String[] elements = encoded.split("_");
        if (elements.length == 2) {
            isEnabled.set(elements[0].equals("1"));
            target.set(target.fromExternal(elements[1]));
            if (timeTarget != null) setTimeTarget("22:00");
        } else if (elements.length == 3) {
            isEnabled.set(elements[0].equals("1"));
            target.set(target.fromExternal(elements[1]));
            setTimeTarget(elements[2]);
        } else {
            isEnabled.set(false);
            target.set(target.fromExternal(dfltEncoded));
            if (timeTarget != null) setTimeTarget("22:00");
            Tesla.logger.warning("Malformed externalized trigger: " + encoded);
        }
    }
    
    public BooleanProperty getEnabled() { return this.isEnabled; }
    
/*------------------------------------------------------------------------------
 *
 * Private Utility Methods
 * 
 *----------------------------------------------------------------------------*/
    
    private String fullKey() { return ac.vehicle.getVIN()+"_"+subject.getKey(); }
    private String onOff(boolean b) { return b ? "1" : "0"; }
    
    /**
     * Determines whether the time constraint for this predicate has been satisfied 
     * @return true if and only if:
     * 1. There is a timeTarget
     * 2. It hasn't already been triggered today
     * 3. The current time is after the time target [Don't worry about equal]
     */
    private boolean satisfiesTimeTrigger() {
        if (timeTarget == null) return true;
        
        Calendar now = new GregorianCalendar();
        
        // Has it already been triggered today? If so, then don't trigger again
        if (lastDaySatisfied.get(Calendar.DAY_OF_YEAR) == 
                now.get(Calendar.DAY_OF_YEAR)) return false;
        
        // Note that the day or even the year may have changed since the time
        // target was established. Update it to be sure we're on the right day.
        timeTarget.get().set(Calendar.DAY_OF_YEAR, now.get(Calendar.DAY_OF_YEAR));
        timeTarget.get().set(Calendar.YEAR, now.get(Calendar.YEAR));
        if (now.after(timeTarget.get())) {
            return (now.getTimeInMillis() - timeTarget.get().getTimeInMillis() < 10 * 60 * 1000);
        }
        return false;
    }
    
    private void setTimeTarget(String encoded) {
        if (!encoded.equals("HH:MM")) {
            try {
                int hour = Integer.valueOf(encoded.substring(0, 2));
                int minute = Integer.valueOf(encoded.substring(3, 5));
                GregorianCalendar tt = new GregorianCalendar();
                tt.set(Calendar.HOUR_OF_DAY, hour);
                tt.set(Calendar.MINUTE, minute);
                timeTarget.set(tt);
            } catch (Exception e) {
                Tesla.logger.warning("Malformed externalized trigger time target: " + encoded);
            }
        }
    }
    

}
