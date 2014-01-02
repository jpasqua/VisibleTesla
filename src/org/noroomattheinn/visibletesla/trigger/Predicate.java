/*
 * Predicate.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Dec 14, 2013
 */

package org.noroomattheinn.visibletesla.trigger;

/**
 * Predicate: Tests whether a subject and target satisfy a given predicate.
 * 
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class Predicate<T extends Comparable<T>> {

/*------------------------------------------------------------------------------
 *
 * Constants and Enums
 * 
 *----------------------------------------------------------------------------*/

    public enum Type {FallsBelow, HitsOrExceeds, Becomes, AnyChange, GT, LT, EQ};
        
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private Type type;
    private T last;
    private Target<T> targetContainer;

    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    Predicate(Type type, Target<T> target) {
        this.type = type;
        this.targetContainer = target;
        this.last = null;
    }
    
    public Type getType() { return type; }
    
    public boolean satisfied(T current) {
        boolean satisfied = false;
        T target = targetContainer.get();
        
        dump(current, target);
        
        if (type == Type.AnyChange) {
            satisfied = true;
        } else if (type == Type.GT || type == Type.LT || type == Type.EQ) {
            satisfied = (type == Type.EQ) ? current.compareTo(target) == 0 :
                ((type == Type.GT) ? current.compareTo(target) > 0 :
                                     current.compareTo(target) < 0);
        } else if (last != null) {
            if (type == Type.FallsBelow || type == Type.HitsOrExceeds) {
                satisfied = (type == Type.FallsBelow) ?
                        (last.compareTo(target) >= 0 && current.compareTo(target) < 0) :
                        (last.compareTo(target) < 0 && current.compareTo(target) >= 0);
            } else  if (type == Type.Becomes) {
                satisfied = target.equals("Anything") ?  (!last.equals(current)) :
                    (!last.equals(target) && current.equals(target));
            }
        }
        last = current;
        return satisfied;
    }
    
    @Override public String toString() {
        switch (type) {
            case FallsBelow: return "fell below";
            case HitsOrExceeds: return "hit or exceeded";
            case Becomes: return "became";
            case AnyChange: return "occurred";
            case GT: return "is greater than";
            case LT: return "is less than";
            case EQ: return "is equal to";
        }
        return type.name();
    }
    
    private void dump(T current, T target) {
//        System.err.format("satisifed: Type: %s, Current: %s, Target: %s, Last: %s\n",
//                type.toString(), current.toString(), target.toString(),
//                last == null ? "null" : last.toString());
    }
    
}
