/*
 * Area.java  - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Mar 07, 2014
 */

package org.noroomattheinn.visibletesla;

import org.noroomattheinn.utils.GeoUtils;

/**
 * Area: Immutable representation of a geographic area specified by a 
 * (lat, lng) pair and a radius in meters. The area may also contain a 
 * name which may be the "address" that corresponds to the (lat, lng)
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class Area implements Comparable<Area> {
    public final double lat;
    public final double lng;
    public final double radius;
    public final String name;
    
    public Area() {
        this(0, 0, 0, "-");
    }
    
    public Area(double lat, double lng, double radius, String name) {
        this.lat = lat;
        this.lng = lng;
        this.radius = radius;
        this.name = name;
    }
    
    public boolean intersects(Area other) {
        double distance = GeoUtils.distance(lat, lng, other.lat, other.lng);
        double coverage = radius + other.radius;
        return distance <= coverage;
    }

    /**
     * An implementation of compareTo that really is about intersection. 
     * @param other The other Area to compare to
     * @return  -1  If the 2 areas do not intersect
     *           0  If the 2 areas intersect
     *           1  Never returned
     */
    @Override public int compareTo(Area other) {
        if (intersects(other)) return 0;
        return -1;
    }
    
    @Override public String toString() {
        return String.format("[(%3.5f, %3.5f), %2.1f]", lat, lng, radius);
    }
    
}
