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
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebEvent;
import javafx.scene.web.WebView;
import org.noroomattheinn.tesla.DrivingState;
import org.noroomattheinn.tesla.Tesla;
import org.noroomattheinn.tesla.Vehicle;


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
    private DrivingState drivingState;
    private boolean mapIsLoaded = false;
    private WebEngine engine;

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
                drivingState.latitude(), drivingState.longitude());
        appContext.app.getHostServices().showDocument(url);
    }
    
/*------------------------------------------------------------------------------
 *
 * Methods overridden from BaseController
 * 
 *----------------------------------------------------------------------------*/
    
    protected void fxInitialize() {
        engine = webView.getEngine();
        engine.setOnAlert(new EventHandler<WebEvent<String>>() {
                  @Override public void handle(WebEvent<String> event) {
                    System.out.println(event.getData());
                  }
                });  
        progressIndicator.setVisible(false);
        progressLabel.setVisible(false);
    }
    
    protected void reflectNewState() {
        if (!drivingState.hasValidData()) return;
        double jitter = 0;
//        double jitter = (Math.random()-0.5)*0.003;    // For testing only!
        String latitude = String.valueOf(drivingState.latitude() + jitter);
        String longitude = String.valueOf(drivingState.longitude() - jitter);
        String heading = String.valueOf(drivingState.heading());
        if (!mapIsLoaded) {
            String mapHTML = getMapFromTemplate(latitude, longitude, heading);
            engine.loadContent(mapHTML);
        } else {
            engine.executeScript(String.format(
                "moveMarker(%s, %s, %s)", latitude, longitude, heading));
        }
        mapIsLoaded = true;
    }

    protected void refresh() { updateState(drivingState); }

    @Override protected void prepForVehicle(Vehicle v) {
        if (differentVehicle(drivingState, v)) {
            drivingState = new DrivingState(v);
        }
    }
    
/*------------------------------------------------------------------------------
 *
 * Private Utility Methods and Classes
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
    
    String getMapFromTemplate(String lat, String lng, String heading) {
        StringBuilder sb = fromInputStream(getClass().getResourceAsStream(MapTemplateFileName));
        // TO DO: replaceField scans from the beginning each time which is dumb, 
        // but this approach is quick and easy to implement...
        replaceField(sb, "DIRECTION", heading);
        replaceField(sb, "LAT", lat);
        replaceField(sb, "LONG", lng);
        return sb.toString();
    }
    
}
