/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.noroomattheinn.visibletesla.dialogs;

import java.net.URL;
import java.util.Map;
import java.util.ResourceBundle;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker.State;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import org.noroomattheinn.tesla.Tesla;
import org.noroomattheinn.utils.SimpleTemplate;
import org.noroomattheinn.visibletesla.Area;


public class ChooseLocationDialog implements DialogUtils.DialogController {
    
/*------------------------------------------------------------------------------
 *
 * Constants and Enums
 * 
 *----------------------------------------------------------------------------*/
    public static final String AREA_KEY = "AREA";
    public static final String API_KEY = "API_KEY";
    private static final String MapTemplateFileName = "ChooseLocation.html";
    
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private Stage stage;
    private Area area = null;
    private WebEngine engine;
    private boolean cancelled = true;
    private boolean loaded = false;
    private String apiKey;
    
/*------------------------------------------------------------------------------
 *
 * UI Elements
 * 
 *----------------------------------------------------------------------------*/

    @FXML private ResourceBundle resources;
    @FXML private URL location;

    @FXML private Button cancelButton, okButton;
    @FXML private WebView webView;
    @FXML private TextField nickname;
    
/*------------------------------------------------------------------------------
 *
 *  UI Action Handlers
 * 
 *----------------------------------------------------------------------------*/

    @FXML void buttonPressed(ActionEvent event) {
        Button b = (Button)event.getTarget();
        if (b == okButton) {
            String r = (String)engine.executeScript("getVals(distanceWidget)");
            String[] vals = r.split("_");
            double lat = Double.valueOf(vals[0]);
            double lng = Double.valueOf(vals[1]);
            double radius = Double.valueOf(vals[2]);
            String locationName = nickname.getText();
            if (locationName == null || locationName.isEmpty()) {
                if (vals.length == 4) {
                    locationName = vals[3];
                } else {
                    locationName = String.format("(%f, %f)", lat, lng);
                }
            }
            area = new Area(lat, lng, radius, locationName);
            cancelled = false;
            stage.close();
        } else if (b == cancelButton) {
            cancelled = true;
            area = null;
            stage.close();
        }
    }

    @FXML void initialize() {
        engine = webView.getEngine();
    }
    
/*------------------------------------------------------------------------------
 *
 * Methods overridden from DialogController
 * 
 *----------------------------------------------------------------------------*/
    
    @Override public void setStage(Stage stage) {
        this.stage = stage;
    }

    @Override public void setProps(Map props) {
        apiKey = (String)props.get(API_KEY);
        if (apiKey == null) {
            Tesla.logger.severe("API_KEY must be provided to ChooseLocationDialog!");
            stage.close();
            return;
        }
        
        area = (Area)props.get(AREA_KEY);
        if (area == null || (area.lat == 0 && area.lng == 0)) {
            area = new Area(37.3941542, -122.1498701, 20.0, ""); // Tesla HQ
        } else {
            if (area.name != null) nickname.setText(area.name);
        }
        // Prep the web view...
        String mapHTML = getMapFromTemplate(area.lat, area.lng, area.radius);
        engine.getLoadWorker().stateProperty().addListener( new ChangeListener<State>() {
            @Override public void changed(ObservableValue ov, State old, State cur) {
                if (cur == State.SUCCEEDED) {
                    loaded = true;
                }
            }
        });
        engine.loadContent(mapHTML);
    }

        
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public Area getArea() {
        return area;
    }
    
    public boolean cancelled() { return !loaded || cancelled; }
    
/*------------------------------------------------------------------------------
 *
 * PRIVATE Utility Methods
 * 
 *----------------------------------------------------------------------------*/
    
    private String getMapFromTemplate(double lat, double lng, double radius) {
        SimpleTemplate template = new SimpleTemplate(getClass().getResourceAsStream(MapTemplateFileName));
        return template.fillIn(
                "JFX_LAT", String.valueOf(lat), "JFX_LONG", String.valueOf(lng),
                "JFX_RADIUS", String.valueOf(radius), "JFX_GMAP_API_KEY", apiKey);
    }


}
