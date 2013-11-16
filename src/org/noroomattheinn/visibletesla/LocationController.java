/*
 * LocationController.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Jul 22, 2013
 */

package org.noroomattheinn.visibletesla;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.logging.Level;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebEvent;
import javafx.scene.web.WebView;
import org.noroomattheinn.tesla.SnapshotState;
import org.noroomattheinn.tesla.Tesla;
import org.noroomattheinn.tesla.Vehicle;
import org.noroomattheinn.utils.Utils;
import org.noroomattheinn.visibletesla.AppContext.InactivityMode;


public class LocationController extends BaseController {
/*------------------------------------------------------------------------------
 *
 * Constants and Enums
 * 
 *----------------------------------------------------------------------------*/
    
    private static final String MapTemplateFileName = "MapTemplate.html";
    private static final String MapLoadingFileName = "MapLoading.html";
    private static final String RadarFileName = AppContext.ResourceDir + "MapLoading.jpg";
    
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private final Object lock = false;
    private SnapshotState snapshotState;
    private boolean mapIsLoaded = false;
    private WebEngine engine;
    
    private Thread streamer = null;
    private String loading = null;
    
/*------------------------------------------------------------------------------
 *
 * UI Elements
 * 
 *----------------------------------------------------------------------------*/
    @FXML private Button launchButton;
    @FXML private WebView webView;
    
/*------------------------------------------------------------------------------
 *
 *  UI Action Handlers
 * 
 *----------------------------------------------------------------------------*/

    @FXML void launchButtonHandler(ActionEvent event) {
        String url = String.format(
                "https://maps.google.com/maps?q=%f,%f(Tesla)&z=18&output=embed",
                snapshotState.estLat(), snapshotState.estLng());
        appContext.app.getHostServices().showDocument(url);
    }
    
/*------------------------------------------------------------------------------
 *
 * Methods overridden from BaseController
 * 
 *----------------------------------------------------------------------------*/
    
    @Override protected void fxInitialize() {
        engine = webView.getEngine();
        engine.setOnAlert(new EventHandler<WebEvent<String>>() {
                  @Override public void handle(WebEvent<String> event) {
                    System.out.println(event.getData());
                  }
                });  
        progressIndicator.setVisible(false);
        progressLabel.setVisible(false);
    }
    
    @Override protected void reflectNewState() {
        if (!snapshotState.hasValidData()) return;
        reflectInternal(snapshotState.estLat(), snapshotState.estLng(), snapshotState.estHeading());
    }

    @Override protected void refresh() { 
        synchronized(lock) { lock.notify(); } }

    @Override protected void prepForVehicle(Vehicle v) {
        if (differentVehicle(snapshotState, v)) {
            if (loading == null) { loading = getLoadingPage(); }
            engine.loadContent(loading);
            snapshotState = new SnapshotState(v);
            ensureStreamer();
        }
    }
    
/*------------------------------------------------------------------------------
 *
 * Private Utility Methods and Classes
 * 
 *----------------------------------------------------------------------------*/
    
    private void reflectInternal(double lat, double lng, int theHeading) {
        if (!snapshotState.hasValidData()) return;
        String latitude = String.valueOf(lat);
        String longitude = String.valueOf(lng);
        String heading = String.valueOf(theHeading);
        if (!mapIsLoaded) {
            String mapHTML = getMapFromTemplate(latitude, longitude, heading);
            engine.loadContent(mapHTML);
        } else {
            engine.executeScript(String.format(
                "moveMarker(%s, %s, %s)", latitude, longitude, heading));
        }
        mapIsLoaded = true;
    }
    
    private void ensureStreamer() {
        if (streamer == null) {
            streamer = appContext.launchThread(new LocationStreamer(lock), "00 LocationStreamer");
            while (streamer.getState() != Thread.State.WAITING) {
                Utils.yieldFor(10);
            }
        }
    }
    
/*------------------------------------------------------------------------------
 *
 * PRIVATE - Methods to load and customize html templates
 * 
 *----------------------------------------------------------------------------*/
    
    private void replaceField(StringBuilder sb, String placeholder, String newText) {
        int length = placeholder.length();
        int loc = sb.indexOf(placeholder);
        sb.replace(loc, loc+length, newText);
    }
    
    private StringBuilder fromInputStream(InputStream is) {
        InputStreamReader r = new InputStreamReader(is);
        StringBuilder sb = new StringBuilder();
        try {
            int c;
            while ((c = r.read()) != -1) { sb.append((char) c); }
        } catch (IOException ex) {
            Tesla.logger.log(Level.SEVERE, null, ex);
        }
        return sb;
    }
    
    private String getLoadingPage() {
        StringBuilder sb = fromInputStream(
                getClass().getResourceAsStream(MapLoadingFileName));
        String imageLoc = getClass().getResource(RadarFileName).toExternalForm();
        replaceField(sb, "IMAGE", imageLoc);
        return sb.toString();
    }
    
    private String getMapFromTemplate(String lat, String lng, String heading) {
        StringBuilder sb = fromInputStream(getClass().getResourceAsStream(MapTemplateFileName));
        // TO DO: replaceField scans from the beginning each time which is dumb, 
        // but this approach is quick and easy to implement...
        replaceField(sb, "DIRECTION", heading);
        replaceField(sb, "LAT", lat);
        replaceField(sb, "LONG", lng);
        return sb.toString();
    }
    
/*------------------------------------------------------------------------------
 *
 * PRIVATE - The class that enables streaming of location data
 * 
 *----------------------------------------------------------------------------*/
    
    private class LocationStreamer implements Runnable, ChangeListener<AppContext.InactivityMode> {
        private static final long StreamingThreshold = 400;
        final Object lock;
        private InactivityMode inactivityState = InactivityMode.StayAwake;
        
        @Override public void
        changed(ObservableValue<? extends InactivityMode> o, InactivityMode ov, InactivityMode nv) {
            inactivityState = nv;
        }

        LocationStreamer(Object lock) {
            this.lock = lock;
        }

        private void doUpdateLater(final SnapshotState ss) {
            Platform.runLater(new Runnable() {
                @Override public void run() {
                    reflectInternal(ss.estLat(), ss.estLng(), ss.estHeading());
                } });
        }
        
        @Override public void run() {
            while (!appContext.shuttingDown.get()) {
                try {
                    synchronized (lock) { lock.wait(); }
                    snapshotState.refresh();
                    if (snapshotState.hasValidData()) {
                        long lastSnapshot = snapshotState.timestamp().getTime();
                        doUpdateLater(snapshotState);
                        // Now, stream data as long as it comes...
                        while (snapshotState.refreshFromStream()) {
                            if (inactivityState != InactivityMode.StayAwake)
                                break;
                            if (snapshotState.timestamp().getTime() - lastSnapshot > StreamingThreshold) {
                                doUpdateLater(snapshotState);
                                lastSnapshot = snapshotState.timestamp().getTime();
                            }
                        }
                    }
                } catch (InterruptedException ex) {
                    Tesla.logger.log(Level.INFO, "LocationStreamer Interrupted");
                }
            }
        }
    }
}
