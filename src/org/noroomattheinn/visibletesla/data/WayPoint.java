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
        this(new Row(VTData.schema));
        row.timestamp = Long.MAX_VALUE;
    }

    public WayPoint(StreamState ss, ChargeState cs) {
        this(new Row(VTData.schema));
        row.timestamp = ss.timestamp;

        set(VTData.LatitudeKey, ss.estLat);
        set(VTData.LongitudeKey, ss.estLng);
        set(VTData.HeadingKey, ss.estHeading);
        set(VTData.SpeedKey, ss.speed);
        set(VTData.OdometerKey, ss.odometer);
        set(VTData.PowerKey, ss.power);
        set(VTData.SOCKey, cs.batteryPercent);
    }
    
    public String asJSON() { return asJSON(true); }

    public String asJSON(boolean useMiles) {
        double adjustedSpeed, adjustedOdo, adjustedElevation;

        if (useMiles) {
            adjustedSpeed = get(VTData.SpeedKey);
            adjustedOdo = get(VTData.OdometerKey);
            adjustedElevation = Utils.round(Utils.metersToFeet(elevation), 0);
        } else {
            adjustedSpeed = Utils.milesToKm(get(VTData.SpeedKey));
            adjustedOdo = Utils.milesToKm(get(VTData.OdometerKey));
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
    public double getOdo()          { return get(VTData.OdometerKey); }
    public double getHeading()      { return get(VTData.HeadingKey); }
    public double getPower()        { return get(VTData.PowerKey); }
    public double getSOC()          { return get(VTData.SOCKey); }
    
    // The following getters are also part of the GeoUtils.LocationSource interface
    @Override public double getLat() { return get(VTData.LatitudeKey); }
    @Override public double getLng() { return get(VTData.LongitudeKey); }

    // Elevation is the only field that can be set after the WayPoint is created
    // This is done so that it can be added lazily only when needed
    public void setElevation(double e) { elevation = e; }
    
/*------------------------------------------------------------------------------
 *
 * Private Utility Methods
 * 
 *----------------------------------------------------------------------------*/
    
    private double get(String column) { return row.get(VTData.schema, column); }
    
    private void set(String column, double value) {
        row.set(VTData.schema, column, value);
    }
}