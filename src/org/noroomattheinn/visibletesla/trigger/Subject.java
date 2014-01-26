/*
 * Subject.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Dec 14, 2013
 */

package org.noroomattheinn.visibletesla.trigger;

/**
 * Subject: The Subject of a Trigger
 * 
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class Subject<T extends Comparable<T>> {
 
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private String name;
    private String key;
    private T value;
    private RW<T> th;

/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/

    Subject(String name, String key, RW<T> th) {
        this.name = name;
        this.key = key;
        this.th = th;
        this.value = null;
    }
    
    public void set(T newVal) { value = newVal; }
    public T get() { return value; }
    public String toExternal() {
        return value == null ? "" : th.toExternal(value);
    }
    public String formatted() {
        return value == null ? "" : th.formatted(value);
    }
    
    public String getName() { return name; }
    public String getKey() { return key; }
}