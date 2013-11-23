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
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker.State;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.util.Duration;
import org.noroomattheinn.tesla.SnapshotState;
import org.noroomattheinn.tesla.Tesla;
import org.noroomattheinn.tesla.Vehicle;
import org.noroomattheinn.utils.Utils;
import org.noroomattheinn.visibletesla.AppContext.InactivityType;


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
    private Animation blipAnimation = null;
    
/*------------------------------------------------------------------------------
 *
 * UI Elements
 * 
 *----------------------------------------------------------------------------*/
    
    @FXML private Button launchButton;
    @FXML private WebView webView;
    @FXML private ImageView loadingImage;
    @FXML private Label loadingImageLabel;
    
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
            //if (loading == null) { loading = getLoadingPage(); }
            //engine.loadContent(loading);
            blipAnimation = animateBlip();
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
        String latitude = String.valueOf(lat);
        String longitude = String.valueOf(lng);
        String heading = String.valueOf(theHeading);
        
        if (mapIsLoaded) {
            engine.executeScript(String.format(
                "moveMarker(%s, %s, %s)", latitude, longitude, heading));
        } else {
            String mapHTML = getMapFromTemplate(latitude, longitude, heading);
            engine.getLoadWorker().stateProperty().addListener( new ChangeListener<State>() {
                @Override public void changed(ObservableValue ov, State old, State cur) {
                    if (cur == State.SUCCEEDED) {
                        mapIsLoaded = true;
                        stopBlip(blipAnimation);
                        loadingImage.setVisible(false);
                        loadingImageLabel.setVisible(false);
                    }
                }
            });
            engine.loadContent(mapHTML);

//            This code can be used to communicate events back from javascript in
//            the html code to this Java code. The javascript code would do something
//            like: window.status = "load complete"; and that message would be passed
//            to this onStatusChanged handler.
//            engine.setOnStatusChanged(new EventHandler<WebEvent<java.lang.String>>() {
//                @Override public void handle(WebEvent<String> t) {
//                    Tesla.logger.log(Level.INFO, "Status Change: " + t.getData());
//                }
//            });

        }
    }
    
    private void stopBlip(Animation blip) {
        EventHandler<ActionEvent> cleanup = blip.getOnFinished();
        blip.stop();
        cleanup.handle(null);
    }
    
    private Animation animateBlip() {
        final Circle core = new Circle(572, 360, 5);
        final Circle blip = new Circle(572, 360, 5);
        final Circle outline = new Circle(572, 360, 5);
        Duration blipTime = Duration.seconds(1.5);
        Duration interBlipTime = Duration.seconds(0.5);
        
        core.setFill(Color.BLUE);
        blip.setFill(Color.LIGHTBLUE);
        outline.setFill(Color.TRANSPARENT);
        outline.setStroke(Color.DARKBLUE);
        outline.setStrokeWidth(0.25);
        
        root.getChildren().addAll(blip, core, outline);
        
        FadeTransition fadeBlip = new FadeTransition(blipTime, blip);
        fadeBlip.setFromValue(0.8); fadeBlip.setToValue(0.0);
        
        ScaleTransition scaleBlip = new ScaleTransition(blipTime, blip);
        scaleBlip.setFromX(1); scaleBlip.setToX(4);
        scaleBlip.setFromY(1); scaleBlip.setToY(4);
        
        FadeTransition fadeOutline = new FadeTransition(blipTime, outline);
        fadeOutline.setFromValue(1.0); fadeOutline.setToValue(0.0);
        
        ScaleTransition scaleOutline = new ScaleTransition(blipTime, outline);
        scaleOutline.setFromX(1); scaleOutline.setToX(4);
        scaleOutline.setFromY(1); scaleOutline.setToY(4);
        
        SequentialTransition sequence = new SequentialTransition (
            new ParallelTransition(fadeBlip, scaleBlip, scaleOutline, fadeOutline),
            new PauseTransition(interBlipTime));
        sequence.setCycleCount(Timeline.INDEFINITE);
        sequence.setOnFinished(new EventHandler<ActionEvent>() {
            @Override public void handle(ActionEvent t) {
                core.setVisible(false);
                blip.setVisible(false);
                outline.setVisible(false);
            }
        });
        
        sequence.play();
        return sequence;
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
    
    private class LocationStreamer implements Runnable, ChangeListener<AppContext.InactivityType> {
        private static final long StreamingThreshold = 400;
        final Object lock;
        private InactivityType inactivityState = InactivityType.Awake;
        
        @Override public void
        changed(ObservableValue<? extends InactivityType> o, InactivityType ov, InactivityType nv) {
            inactivityState = nv;
        }

        LocationStreamer(Object lock) {
            this.lock = lock;
        }

        private void doUpdateLater(final SnapshotState ss) {
            Platform.runLater(new Runnable() {
                @Override public void run() {
                    int heading = (ss.speed() > 10) ? ss.estHeading() : ss.heading();
                    reflectInternal(ss.estLat(), ss.estLng(), heading);
                } });
        }
        
        @Override public void run() {
            while (!appContext.shuttingDown.get()) {
                try {
                    synchronized (lock) { lock.wait(); }
                    if (!snapshotState.refresh()) 
                        continue;
                    
                    long lastSnapshot = snapshotState.timestamp().getTime();
                    doUpdateLater(snapshotState);

                    // Now, stream data as long as it comes...
                    while (snapshotState.refreshFromStream()) {
                        if (inactivityState != InactivityType.Awake)
                            break;
                        if (snapshotState.timestamp().getTime() - lastSnapshot > StreamingThreshold) {
                            doUpdateLater(snapshotState);
                            lastSnapshot = snapshotState.timestamp().getTime();
                        }
                    }
                } catch (InterruptedException ex) {
                    Tesla.logger.log(Level.INFO, "LocationStreamer Interrupted");
                }
            }
        }
    }
}
