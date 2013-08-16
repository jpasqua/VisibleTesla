/*
 * LocationController.java - Copyright(c) 2013  All Rights Reserved, Joe Pasqua
 * Created: Jul 22, 2013
 */

package org.noroomattheinn.visibletesla;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import org.noroomattheinn.tesla.DrivingState;
import org.noroomattheinn.tesla.Vehicle;
import org.noroomattheinn.utils.GeoUtils;


public class LocationController extends BaseController {

    private static final String MapTemplateFileName = "MapTemplate.html";
    
    @FXML private Button launchButton;
    @FXML private WebView webView;
    
    private DrivingState drivingState;
    private boolean mapIsLoaded = false;
    
    private WebEngine engine;
    
    @FXML void launchButtonHandler(ActionEvent event) {
        String url = String.format(
                "https://maps.google.com/maps?q=%f,%f(Tesla)&z=18&output=embed",
                drivingState.latitude(), drivingState.longitude());
        app.getHostServices().showDocument(url);
    }
    
    // Controller-specific initialization
    protected void doInitialize() {
        engine = webView.getEngine();
        progressIndicator.setVisible(false);
        progressLabel.setVisible(false);
    }
    
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
            Logger.getLogger(LocationController.class.getName()).log(Level.SEVERE, null, ex);
        }
        return sb;
    }
    
    String getMapFromTemplate(String lat, String lng, String heading) {
        String addr = GeoUtils.getAddrForLatLong(lat, lng).replaceFirst(", ", "<BR>");
        StringBuilder sb = fromInputStream(getClass().getResourceAsStream(MapTemplateFileName));
        // TO DO: replaceField scans from the beginning each time which is dumb, 
        // but this approach is quick and easy to implement...
        replaceField(sb, "DIRECTION", heading);
        replaceField(sb, "LAT", lat);
        replaceField(sb, "LONG", lng);
        replaceField(sb, "ADDR", addr);
        return sb.toString();
    }
    
    protected void reflectNewState() {
        String latitude = String.valueOf(drivingState.latitude());
        String longitude = String.valueOf(drivingState.longitude());
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

    protected void refresh() {
        issueCommand(new GetAnyState(drivingState), AfterCommand.Reflect);
    }

    @Override protected void prepForVehicle(Vehicle v) {
        if (drivingState == null || v != vehicle) {
            drivingState = new DrivingState(v);
        }
    }
    
}
