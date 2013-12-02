/*
 * Stat.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Oct 12, 2013
 */

package org.noroomattheinn.visibletesla.stats;

public class Stat {

    public final Sample sample;
    public final String type;

    public Stat(long time, String type, double value) {
        this.sample = new Sample(time, value);
        this.type = type;
    }
    
    public static class Sample {
        public long timestamp;
        public double value;

        public Sample(long timestamp, double value) {
            this.timestamp = timestamp;
            this.value = value;
        }
    }
}
