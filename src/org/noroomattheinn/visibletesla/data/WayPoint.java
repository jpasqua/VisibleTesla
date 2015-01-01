/*
 * WayPoint.java - Copyright(c) 2013, 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Nov 27, 2013
 */
package org.noroomattheinn.visibletesla.data;

import java.util.Date;
import org.noroomattheinn.tesla.ChargeState;
import org.noroomattheinn.tesla.StreamState;
import org.noroomattheinn.timeseries.Row;
import org.noroomattheinn.utils.GeoUtils;
import org.noroomattheinn.utils.Utils;
import org.noroomattheinn.visibletesla.data.StatsCollector;

/**
 * WayPoint: Describes a point on a trip
 * 
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class WayPoint implements GeoUtils.LocationSource {
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    private final Row row;
    private double elevation;
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/

    public WayPoint(Row r) {
        this.row = r;
        this.elevation = Double.NaN;
    }

    public WayPoint() {
        this(new Row(StatsCollector.schema));
        row.timestamp = Long.MAX_VALUE;
    }

    public WayPoint(StreamState ss, ChargeState cs) {
        this(new Row(StatsCollector.schema));
        row.timestamp = ss.timestamp;

        set(StatsCollector.LatitudeKey, ss.estLat);
        set(StatsCollector.LongitudeKey, ss.estLng);
        set(StatsCollector.HeadingKey, ss.estHeading);
        set(StatsCollector.SpeedKey, ss.speed);
        set(StatsCollector.OdometerKey, ss.odometer);
        set(StatsCollector.PowerKey, ss.power);
        set(StatsCollector.SOCKey, cs.batteryPercent);
    }
    
    public String asJSON() { return asJSON(true); }

    public String asJSON(boolean useMiles) {
        double adjustedSpeed, adjustedOdo, adjustedElevation;

        if (useMiles) {
            adjustedSpeed = get(StatsCollector.SpeedKey);
            adjustedOdo = get(StatsCollector.OdometerKey);
            adjustedElevation = Utils.round(Utils.metersToFeet(elevation), 0);
        } else {
            adjustedSpeed = Utils.milesToKm(get(StatsCollector.SpeedKey));
            adjustedOdo = Utils.milesToKm(get(StatsCollector.OdometerKey));
            adjustedElevation = Utils.round(elevation, 1);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("    timestamp: \"");
        sb.append(String.format("%1$tm/%1$td/%1$ty %1$tH:%1$tM:%1$tS", new Date(row.timestamp)));
        sb.append("\",\n");
        sb.append("    lat: ").append(getLat()).append(",\n");
        sb.append("    lng: ").append(getLng()).append(",\n");
        sb.append("    speed: ").append(adjustedSpeed).append(",\n");
        sb.append("    heading: ").append(getHeading()).append(",\n");
        sb.append("    power: ").append(getPower()).append(",\n");
        sb.append("    odometer: ").append(adjustedOdo).append(",\n");
        sb.append("    elevation: ").append(adjustedElevation).append("\n");
        sb.append("}\n");

        return sb.toString();
    }

    @Override public String toString() { return asJSON(); }

    public long   getTime()         { return row.timestamp; }
    public double getElevation()    { return elevation; }
    public double getOdo()          { return get(StatsCollector.OdometerKey); }
    public double getHeading()      { return get(StatsCollector.HeadingKey); }
    public double getPower()        { return get(StatsCollector.PowerKey); }
    public double getSOC()          { return get(StatsCollector.SOCKey); }
    
    // The following getters are also part of the GeoUtils.LocationSource interface
    @Override public double getLat() { return get(StatsCollector.LatitudeKey); }
    @Override public double getLng() { return get(StatsCollector.LongitudeKey); }

    // Elevation is the only field that can be set after the WayPoint is created
    // This is done so that it can be added lazily only when needed
    public void setElevation(double e) { elevation = e; }
    
/*------------------------------------------------------------------------------
 *
 * Private Utility Methods
 * 
 *----------------------------------------------------------------------------*/
    
    private double get(String column) { return row.get(StatsCollector.schema, column); }
    
    private void set(String column, double value) {
        row.set(StatsCollector.schema, column, value);
    }
}