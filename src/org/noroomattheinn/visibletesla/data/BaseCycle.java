/*
 * BaseCycle.java - Copyright(c) 2013, 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Nov 25, 2014
 */
package org.noroomattheinn.visibletesla.data;

import com.google.gson.Gson;
import org.noroomattheinn.utils.RestyWrapper;
import us.monoid.json.JSONObject;

/**
 * Base class for objects that keep track of various types of cycles (eg charge
 * cycles or rest cycles).
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public abstract class BaseCycle {
/*------------------------------------------------------------------------------
 *
 * Constants and Enums
 * 
 *----------------------------------------------------------------------------*/
    private static Gson gson = new Gson();
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public long startTime, endTime; // Start and end of this cycle
    public double lat, lng;         // Location where this cycle was measured
        // We use 0,0 to mean that the lat,lng has not been set
        // 0,0 is a valid location, but it's in the middle of the
        // of the ocean, so not a useful location for this purpose
    
    /**
     * Return a JSONObject that represents this Cycle
     * @return A JSONObject that represents this Cycle
     */
    public JSONObject toJSON() {
        return RestyWrapper.newJSONObject(toJSONString());
    }

    /**
     * Return A newly created Cycle corresponding to the provide JSON string
     * @param <C>       The return type
     * @param json      The source JSON string
     * @param theClass  The class of the return type
     * @return A new instance of a subclass of BaseCycle that is internalized
     *         from the provided JSON string representation
     */
    public static <C> C fromJSON(String json, Class<C> theClass) {
        return gson.fromJson(json, theClass);
    }
    
    /**
     * Returns a custom encoding of the Cycle as a JSON String
     * @return 
     */
    public abstract String toJSONString();
    
    @Override public String toString() { return toJSONString(); }

}
