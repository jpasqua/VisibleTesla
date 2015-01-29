/*
 * WayPoint.java - Copyright(c) 2013, 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Nov 27, 2013
 */
package org.noroomattheinn.visibletesla.data;

import java.util.Date;
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
    
    private final long   timestamp;
    private final double odometer;
    private final double speed;
    private final double heading;
    private final double power;
    private final double soc;
    private final double lat;
    private final double lng;
    private       double elevation;
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/

    public WayPoint(
            long timestamp, double odometer, double speed,
            double heading, double lat, double lng, double elevation,
            double power, double soc) {
        this.timestamp = timestamp;
        this.odometer = odometer;
        this.speed = speed;
        this.heading = heading;
        this.lat = lat;
        this.lng = lng;
        this.elevation = elevation;
        this.power = power;
        this.soc = soc;
    }
    
    public String asJSON() { return asJSON(true); }

    public String asJSON(boolean useMiles) {
        double adjustedSpeed, adjustedOdo, adjustedElevation;

        if (useMiles) {
            adjustedSpeed = speed;
            adjustedOdo = odometer;
            adjustedElevation = Utils.round(Utils.metersToFeet(elevation), 0);
        } else {
            adjustedSpeed = Utils.milesToKm(speed);
            adjustedOdo = Utils.milesToKm(odometer);
            adjustedElevation = Utils.round(elevation, 1);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("    timestamp: \"");
        sb.append(String.format("%1$tm/%1$td/%1$ty %1$tH:%1$tM:%1$tS", new Date(timestamp)));
        sb.append("\",\n");
        sb.append("    lat: ").append(getLat()).append(",\n");
        sb.append("    lng: ").append(getLng()).append(",\n");
        sb.append("    speed: ").append(adjustedSpeed).append(",\n");
        sb.append("    heading: ").append(getHeading()).append(",\n");
        sb.append("    power: ").append(getPower()).append(",\n");
        sb.append("    odometer: ").append(adjustedOdo).append(",\n");
        sb.append("    soc: ").append(soc).append(",\n");
        sb.append("    elevation: ").append(adjustedElevation).append("\n");
        sb.append("}\n");

        return sb.toString();
    }

    @Override public String toString() { return asJSON(); }
    
    public long   getTime()         { return timestamp; }
    public double getElevation()    { return elevation; }
    public double getOdo()          { return odometer; }
    public double getHeading()      { return heading; }
    public double getPower()        { return power; }
    public double getSOC()          { return soc; }
    public double getSpeed()        { return speed; }
    
    // The following getters are also part of the GeoUtils.LocationSource interface
    @Override public double getLat() { return lat; }
    @Override public double getLng() { return lng; }

    // Elevation is the only field that can be set after the WayPoint is created
    // This is done so that it can be added lazily only when needed
    public void setElevation(double e) { elevation = e; }

}