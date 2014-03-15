/*
 * RW.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Dec 14, 2013
 */

package org.noroomattheinn.visibletesla.trigger;

import java.math.BigDecimal;
import java.util.Locale;
import org.noroomattheinn.tesla.Tesla;
import org.noroomattheinn.visibletesla.Area;


/**
 * RW: Manage the internalization and externalization of values of various types.
 * RW is short for ReadWrite.
 * 
 * @author joe
 */
public abstract class RW<T> {
    public abstract String toExternal(T value);
    public abstract T fromExternal(String external);
    public abstract String formatted(T value);
    
    public static final RW<BigDecimal> bdHelper = new BDHelper();
    public static final RW<String> stringHelper = new StringHelper();
    public static final RW<Area> areaHelper = new AreaHelper();
}


class BDHelper extends RW<BigDecimal> {
    @Override public String toExternal(BigDecimal value) {
        return String.format(Locale.US, "%3.1f", value.doubleValue());
    }
    
    @Override public BigDecimal fromExternal(String external) {
        try {
            return new BigDecimal(Double.valueOf(external));
        } catch (NumberFormatException e) {
            Tesla.logger.warning("Malformed externalized Trigger value: " + external);
            return new BigDecimal(Double.valueOf(50));
        }
    }

    @Override public String formatted(BigDecimal value) {
        return String.format("%3.1f", value.doubleValue());
    }
}

class StringHelper extends RW<String> {
    @Override public String toExternal(String value) { return value; }
    @Override public String fromExternal(String external) { return external; }
    @Override public String formatted(String value) { return value; }
}

class AreaHelper extends RW<Area> {
    @Override public String toExternal(Area value) {
        return String.format(Locale.US, "%3.5f^%3.5f^%2.1f^%s",
                value.lat, value.lng, value.radius, value.name);
    }

    @Override public Area fromExternal(String external) {
        String[] elements = external.split("\\^");
        if (elements.length != 4) {
            Tesla.logger.severe("Malformed Area String: " + external);
            return new Area();
        }
        double lat, lng, radius;
        try {
            lat = Double.valueOf(elements[0]);
            lng = Double.valueOf(elements[1]);
            radius = Double.valueOf(elements[2]);
            return new Area(lat, lng, radius, elements[3]);
        } catch (NumberFormatException e) {
            Tesla.logger.severe("Malformed Area String: " + external);
            return new Area();
        }
    }

    @Override public String formatted(Area value) {
        return String.format("%s [%3.5f, %3.5f], %2.1f meters",
                value.name, value.lat, value.lng, value.radius);
    }
}

