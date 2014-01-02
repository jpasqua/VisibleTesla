/*
 * RW.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Dec 14, 2013
 */

package org.noroomattheinn.visibletesla.trigger;

import java.math.BigDecimal;
import java.util.Locale;
import org.noroomattheinn.tesla.Tesla;


/**
 * RW: Manage the internalization and externalization of values of various types.
 * RW is short for ReadWrite.
 * 
 * @author joe
 */
public abstract class RW<T> {
    public abstract String toExternal(T value);
    public abstract T fromExternal(String external);
    
    public static final RW<BigDecimal> bdHelper = new BDHelper();
    public static final RW<String> stringHelper = new StringHelper();
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
}

class StringHelper extends RW<String> {
    @Override public String toExternal(String value) { return value; }
    @Override public String fromExternal(String external) { return external; }
}

