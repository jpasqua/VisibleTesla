/*
 * WayPoint.java - Copyright(c) 2013, 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Nov 27, 2013
 */
package org.noroomattheinn.visibletesla;

import java.util.Date;
import java.util.List;
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
    private final Row row;
    private double elevation;

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
    
    public static void addElevations(List<WayPoint> waypoints) {
        if (!Double.isNaN(waypoints.get(0).elevation)) return;   // Already added
        List<GeoUtils.ElevationData> edl = GeoUtils.getElevations(waypoints);
        int nElevations = nElevations(edl);
        for (int i = 0; i < waypoints.size(); i++) {
            waypoints.get(i).elevation = (i < nElevations) ? edl.get(i).elevation : 0.0;
        }
    }
    
    /**
     * Return the number of elements in the list. Unfortunately if this code is
     * simply inserted inline, it can cause null pointer warnings in subsequent
     * code - which is dumb because that's what this test is meant to avoid.
     * @param edl   The list of ElevationData objects. Can be null.
     * @return      The length of the list or 0 if null.
     */
    private static int nElevations(List<GeoUtils.ElevationData> edl) {
        return (edl == null) ? 0 : edl.size();
    }

    public String asJSON() { return asJSON(true); }

    public String asJSON(boolean useMiles) {
        double adjustedSpeed, adjustedOdo, adjustedElevation;

        if (useMiles) {
            adjustedSpeed = get(StatsCollector.SpeedKey);
            adjustedOdo = get(StatsCollector.OdometerKey);
            adjustedElevation = Utils.round(metersToFeet(elevation), 0);
        } else {
            adjustedSpeed = Utils.mToK(get(StatsCollector.SpeedKey));
            adjustedOdo = Utils.mToK(get(StatsCollector.OdometerKey));
            adjustedElevation = Utils.round(elevation, 1);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("    timestamp: \"");
        sb.append(String.format("%1$tm/%1$td/%1$ty %1$tH:%1$tM:%1$tS", new Date(row.timestamp)));
        sb.append("\",\n");
        sb.append("    lat: ").append(get(StatsCollector.LatitudeKey)).append(",\n");
        sb.append("    lng: ").append(get(StatsCollector.LongitudeKey)).append(",\n");
        sb.append("    speed: ").append(adjustedSpeed).append(",\n");
        sb.append("    heading: ").append(get(StatsCollector.HeadingKey)).append(",\n");
        sb.append("    power: ").append(get(StatsCollector.PowerKey)).append(",\n");
        sb.append("    odometer: ").append(adjustedOdo).append(",\n");
        sb.append("    elevation: ").append(adjustedElevation).append("\n");
        sb.append("}\n");

        return sb.toString();
    }

    public long getTime() { return row.timestamp; }

    @Override public String toString() { return asJSON(); }

    @Override public double getLat() { return get(StatsCollector.LatitudeKey); }
    @Override public double getLng() { return get(StatsCollector.LongitudeKey); }

    final void set(String column, double value) {
        row.set(StatsCollector.schema, column, value);
    }

    final double get(String column) { return row.get(StatsCollector.schema, column); }
    private double metersToFeet(double meters) { return meters * 3.28084; }

}