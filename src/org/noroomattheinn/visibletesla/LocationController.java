/*
 * LocationController.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Jul 22, 2013
 */

package org.noroomattheinn.visibletesla;

import java.io.File;
import java.io.IOException;
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
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.util.Duration;
import org.apache.commons.io.FileUtils;
import org.noroomattheinn.fxextensions.MultiGauge;
import org.noroomattheinn.tesla.StreamState;
import org.noroomattheinn.utils.SimpleTemplate;
import org.noroomattheinn.utils.Utils;

import static org.noroomattheinn.tesla.Tesla.logger;


public class LocationController extends BaseController {
/*------------------------------------------------------------------------------
 *
 * Constants and Enums
 * 
 *----------------------------------------------------------------------------*/
    
    private static final String MapTemplateFileName = "MapTemplate.html";
    private static final String SimpleMapTemplate = "SimpleMap.html";
    
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private boolean mapIsLoaded = false;
    private WebEngine engine;
    
    private Animation blipAnimation = null;
    private boolean useMiles = true;
    
/*------------------------------------------------------------------------------
 *
 * UI Elements
 * 
 *----------------------------------------------------------------------------*/
    
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
        StreamState state = vtVehicle.streamState.get();
        if (state == null) return;
        showSimpleMap(state.estLat, state.estLng, "Tesla", 18);
    }
    
/*------------------------------------------------------------------------------
 *
 * Methods overridden from BaseController
 * 
 *----------------------------------------------------------------------------*/
    
    @Override protected void fxInitialize() {
        engine = webView.getEngine();
        progressIndicator.setVisible(false);
        multigauge = new MultiGauge(25, 8, 0, 100, -60, 320);
        multigauge.useGradient(Side.RIGHT, Color.DARKORANGE, Color.GREEN);
        multigauge.useGradient(Side.LEFT, Color.BLUE, Color.BLUE);
        multigauge.setLogScale(Side.RIGHT, true);
        Node mg = multigauge.getContainer();
        AnchorPane.setTopAnchor(mg, 25.0);
        AnchorPane.setRightAnchor(mg, 10.0);
        root.getChildren().add(2, mg);
        multigauge.setVal(Side.LEFT, 20);
        multigauge.setVal(Side.RIGHT, 40);
    }
    
    @Override protected void refresh() {
        vtData.produceStream(false);
    }

    @Override protected void initializeState() {
        useMiles = vtVehicle.unitType() == Utils.UnitType.Imperial;
        blipAnimation = animateBlip();
        vtData.produceStream(false);
        vtVehicle.streamState.addTracker(new Runnable() {
            @Override public void run() {
                doUpdateLater(vtVehicle.streamState.get());
            }
        });

        StreamState ss = vtVehicle.streamState.get();
        if (ss != null) { doUpdateLater(ss); }

        if (!useMiles) { multigauge.setRange(Side.LEFT, 0, 160); }
    }
    
    @Override protected void activateTab() { }
    
    private void doUpdateLater(final StreamState state) {
        Platform.runLater(new Runnable() {
            @Override public void run() {
                reflectInternal(state);
            } });
    }
    
    
/*------------------------------------------------------------------------------
 *
 * Private Utility Methods and Classes
 * 
 *----------------------------------------------------------------------------*/
    
    private void reflectInternal(StreamState ss) {
        String latitude = String.valueOf(ss.estLat);
        String longitude = String.valueOf(ss.estLng);
        String heading = String.valueOf(ss.estHeading);

        if (mapIsLoaded) {
            try {
                engine.executeScript(String.format(
                    "moveMarker(%s, %s, %s)", latitude, longitude, heading));
            } catch (Exception e) {
                logger.warning(e.toString());
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
//                    logger.log(Level.INFO, "Status Change: " + t.getData());
//                }
//            });

        }
        multigauge.setVal(Side.LEFT, useMiles ? ss.speed : Utils.milesToKm(ss.speed));
        multigauge.setVal(Side.RIGHT, ss.power);
    }
    
    public void showSimpleMap(double lat, double lng, String title, int zoom) {
        SimpleTemplate template = new SimpleTemplate(getClass().getResourceAsStream(SimpleMapTemplate));
        String map = template.fillIn(
                "LAT", String.valueOf(lat), "LONG", String.valueOf(lng),
                "TITLE", title, "ZOOM", String.valueOf(zoom));
        try {
            File tempFile = File.createTempFile("VTTrip", ".html");
            FileUtils.write(tempFile, map);
            app.showDocument(tempFile.toURI().toString());
        } catch (IOException ex) {
            logger.warning("Unable to create temp file");
            // TO DO: Pop up a dialog!
        }
    }
    
    private String getMapFromTemplate(String lat, String lng, String heading) {
        SimpleTemplate template = new SimpleTemplate(getClass().getResourceAsStream(MapTemplateFileName));
        return template.fillIn(
                "DIRECTION", heading, "LAT", lat, "LONG", lng,
                "GMAP_API_KEY", 
                prefs.useCustomGoogleAPIKey.get() ?
                    prefs.googleAPIKey.get() :
                    Prefs.GoogleMapsAPIKey
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
        

}
