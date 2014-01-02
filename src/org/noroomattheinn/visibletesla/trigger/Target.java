/*
 * Target.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Dec 14, 2013
 */

package org.noroomattheinn.visibletesla.trigger;

import javafx.beans.property.ObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;

/**
 * Target: Represents the target value of a Predicate
 * 
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class Target<T extends Comparable<T>> {
        
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private ObjectProperty<T> property;
    private T dflt;
    private RW<T> th;
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    Target(ObjectProperty<T> property, T dflt, RW<T> th) {
        this.property = property;
        this.dflt = dflt;
        this.th = th;
    }

    public String toExternal() {
        return th.toExternal(property.get());
    }

    public String dfltToExternal() {
        return th.toExternal(dflt);
    }

    public T fromExternal(String external) {
        return th.fromExternal(external);
    }

    public T get() { return property.get(); }
    public void set(T newVal) { property.set(newVal); }
    
    public void setListener(final Trigger trigger) {
        property.addListener(new ChangeListener<T>() {
            @Override public void changed(ObservableValue<? extends T> ov, T t, T t1) {
                trigger.externalize();
            }
        });
    }
}
