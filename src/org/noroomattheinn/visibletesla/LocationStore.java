/*
 * LocationStore.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Nov 25, 2013
 */

package org.noroomattheinn.visibletesla;

import java.io.File;
import java.io.IOException;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import org.noroomattheinn.tesla.SnapshotState;
import org.noroomattheinn.utils.GeoUtils;

/**
 * Listen for changes in Location and store them in a StatsRepository
 * 
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class LocationStore extends DataStore {
    
/*------------------------------------------------------------------------------
 *
 * Constants and Enums
 * 
 *----------------------------------------------------------------------------*/

    public static final String LatitudeKey = "L_LAT";
    public static final String LongitudeKey = "L_LNG";
    public static final String HeadingKey = "L_HDG";
    public static final String SpeedKey = "L_SPD";
    public static final String OdometerKey = "L_ODO";
    public static final String[] Keys = {
        LatitudeKey, LongitudeKey, HeadingKey, SpeedKey, OdometerKey};
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public ObjectProperty<SnapshotState.State> lastStoredSnapshotState;


    public LocationStore(AppContext appContext, File locationFile) throws IOException {
        super(appContext, locationFile, Keys);
        this.lastStoredSnapshotState = new SimpleObjectProperty<>();

        appContext.lastKnownSnapshotState.addListener(new ChangeListener<SnapshotState.State>() {
            @Override public void changed(
                    ObservableValue<? extends SnapshotState.State> ov,
                    SnapshotState.State old, SnapshotState.State cur) {
                storeLocation(cur);
            }
        });

        load(getLoadPeriod());
    }
    
/*------------------------------------------------------------------------------
 *
 * PRIVATE - Methods related to storing new samples
 * 
 *----------------------------------------------------------------------------*/
    
    private SnapshotState.State lastState = null;
    
    private synchronized void storeLocation(SnapshotState.State state) {
        if (!appContext.prefs.collectLocationData.get() || state == null) return;
        double speed = Math.round(state.speed*10.0)/10.0;
        
        if (tooClose(state, lastState)) return;

        long timestamp = state.timestamp;
        storeItem(LatitudeKey, timestamp, state.estLat);
        storeItem(LongitudeKey, timestamp, state.estLng);
        storeItem(HeadingKey, timestamp, state.heading);
        storeItem(SpeedKey, timestamp, speed);
        storeItem(OdometerKey, timestamp, state.odometer);
        repo.flushElements();
        
        lastState = state;
        lastStoredSnapshotState.set(state);
    }
    
    private boolean tooClose(SnapshotState.State wp1, SnapshotState.State wp2) {
        if (wp1 == null || wp2 == null) return false;
        
        double meters = GeoUtils.distance(wp1.estLat, wp1.estLng, wp2.estLat, wp2.estLng);
        
        
        // A big turn makes it "far". Note that heading changes can be spurious.
        // Sometimes we see heading changes when the car is sitting still.
        // Ignore those.
        double turn =  180.0 - Math.abs((Math.abs(wp1.heading - wp2.heading)%360.0) - 180.0);
        if (turn > 10 && meters > 0.05) return false; 
        
        // A long time between readings makes it "far"
        long timeDelta = Math.abs(wp1.timestamp - wp2.timestamp);
        if (timeDelta > 10 * 60 * 1000) return false;
        
        // A short time between readings makes it "too close"
        if (timeDelta < appContext.prefs.locMinTime.get() * 1000)  return true; 
        
        // A short distance between readings makes it "too close"
        return (meters < appContext.prefs.locMinDist.get());
    }
 
}
