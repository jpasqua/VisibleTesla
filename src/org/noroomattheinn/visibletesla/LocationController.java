/*
 * LocationController.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Jul 22, 2013
 */

package org.noroomattheinn.visibletesla;

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
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.util.Duration;
import org.noroomattheinn.tesla.SnapshotState;
import org.noroomattheinn.tesla.Tesla;
import org.noroomattheinn.tesla.Vehicle;
import org.noroomattheinn.utils.SimpleTemplate;
import org.noroomattheinn.utils.Utils;
import org.noroomattheinn.visibletesla.AppContext.InactivityType;


public class LocationController extends BaseController {
/*------------------------------------------------------------------------------
 *
 * Constants and Enums
 * 
 *----------------------------------------------------------------------------*/
    
    private static final String MapTemplateFileName = "MapTemplate.html";
    
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private final Object lock = false;
    private SnapshotState snapshot;
    private boolean mapIsLoaded = false;
    private WebEngine engine;
    
    private Thread streamer = null;
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
    private MultiGauge multigauge;
    
/*------------------------------------------------------------------------------
 *
 *  UI Action Handlers
 * 
 *----------------------------------------------------------------------------*/

    @FXML void launchButtonHandler(ActionEvent event) {
        // TITLE, ZOOM, LAT, LNG
        SnapshotState.State state = appContext.lastKnownSnapshotState.get();
        if (state == null) return;
        appContext.showSimpleMap(state.estLat, state.estLng, "Tesla", 18);
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
        multigauge = new MultiGauge(25, 8, 0, 100, -60, 180);
        multigauge.useGradient(Side.RIGHT, Color.DARKORANGE, Color.GREEN);
        multigauge.useGradient(Side.LEFT, Color.BLUE, Color.BLUE);
        Node mg = multigauge.getContainer();
        AnchorPane.setTopAnchor(mg, 25.0);
        AnchorPane.setRightAnchor(mg, 10.0);
        root.getChildren().add(2, mg);
        multigauge.setVal(Side.LEFT, 20);
        multigauge.setVal(Side.RIGHT, 40);
    }
    
    @Override protected void reflectNewState() {
        if (snapshot.state == null) return;
        reflectInternal(
                snapshot.state.estLat, snapshot.state.estLng,  snapshot.state.estHeading,
                snapshot.state.speed, snapshot.state.power);
    }

    @Override protected void refresh() { 
        synchronized(lock) { lock.notify(); } }

    @Override protected void prepForVehicle(Vehicle v) {
        if (differentVehicle()) {
            blipAnimation = animateBlip();
            snapshot = new SnapshotState(v);
            ensureStreamer();
        }
    }
    
/*------------------------------------------------------------------------------
 *
 * Private Utility Methods and Classes
 * 
 *----------------------------------------------------------------------------*/
    
    private void reflectInternal(double lat, double lng, int theHeading,
                                 double speed, double power) {
        String latitude = String.valueOf(lat);
        String longitude = String.valueOf(lng);
        String heading = String.valueOf(theHeading);

        if (mapIsLoaded) {
            try {
                engine.executeScript(String.format(
                    "moveMarker(%s, %s, %s)", latitude, longitude, heading));
            } catch (Exception e) {
                Tesla.logger.warning(e.toString());
            }
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
        multigauge.setVal(Side.LEFT, speed);
        multigauge.setVal(Side.RIGHT, power);
    }
    
    
    private void ensureStreamer() {
        if (streamer == null) {
            streamer = appContext.launchThread(new LocationStreamer(lock), "00 LocationStreamer");
            while (streamer.getState() != Thread.State.WAITING) {
                Utils.yieldFor(10);
            }
        }
    }
    
    private String getMapFromTemplate(String lat, String lng, String heading) {
        SimpleTemplate template = new SimpleTemplate(getClass().getResourceAsStream(MapTemplateFileName));
        return template.fillIn(
                "DIRECTION", heading, "LAT", lat, "LONG", lng,
                "GMAP_API_KEY", 
                appContext.prefs.useCustomGoogleAPIKey.get() ?
                    appContext.prefs.googleAPIKey.get() :
                    AppContext.GoogleMapsAPIKey
                );
    }
        
/*------------------------------------------------------------------------------
 *
 * Handle the anitmation associated with the "loading" screen
 * 
 *----------------------------------------------------------------------------*/
    
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

        private void doUpdateLater(final SnapshotState.State state) {
            appContext.lastKnownSnapshotState.set(state);
            Platform.runLater(new Runnable() {
                @Override public void run() {
                    reflectInternal(state.estLat, state.estLng, state.estHeading,
                                    state.speed, state.power);
                } });
        }
        
        @Override public void run() {
            appContext.inactivityState.addListener(this);
            inactivityState = appContext.inactivityState.get();

            while (!appContext.shuttingDown.get()) {
                try {
                    synchronized (lock) { lock.wait(); }
                    if (!snapshot.refresh()) 
                        continue;
                    
                    long lastSnapshot = snapshot.state.vehicleTimestamp;
                    doUpdateLater(snapshot.state);

                    // DEBUG: System.err.print("SM: ");
                    // Now, stream data as long as it comes...
                    while (snapshot.refreshFromStream()) {
                        // DEBUG: System.err.print("C");
                        if (appContext.shuttingDown.get() ||
                            inactivityState == InactivityType.Sleep) break;
                        if (snapshot.state.vehicleTimestamp - lastSnapshot > StreamingThreshold) {
                            // DEBUG: System.err.print("S");
                            doUpdateLater(snapshot.state);
                            lastSnapshot = snapshot.state.vehicleTimestamp;
                        } else {
                            // DEBUG: System.err.print("T");
                        }
                    }
                    // DEBUG: System.err.println("!");
                } catch (InterruptedException ex) {
                    Tesla.logger.log(Level.INFO, "LocationStreamer Interrupted");
                }
            }
        }
    }
}
