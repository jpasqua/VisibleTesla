/*
 * StatsEntry.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Oct 12, 2013
 */

package org.noroomattheinn.visibletesla;

public class StatsEntry {

    public final long time;
    public final double value;
    public final String type;

    StatsEntry(long time, String type, double value) {
        this.time = time;
        this.type = type;
        this.value = value;
    }
}
