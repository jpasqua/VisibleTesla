/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.noroomattheinn.visibletesla.dialogs;

import org.noroomattheinn.visibletesla.fxextensions.VTDialog;
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
import static org.noroomattheinn.tesla.Tesla.logger;
import org.noroomattheinn.utils.SimpleTemplate;
import org.noroomattheinn.visibletesla.Area;


public class ChooseLocationDialog extends VTDialog.Controller {
/*------------------------------------------------------------------------------
 *
 * Constants and Enums
 * 
 *----------------------------------------------------------------------------*/
    private static final String MapTemplateFileName = "ChooseLocation.html";
    
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    private Area area = null;
    private WebEngine engine;
    private boolean cancelled = true;
    private boolean loaded = false;
    
/*------------------------------------------------------------------------------
 *
 * UI Elements
 * 
 *----------------------------------------------------------------------------*/
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
            dialogStage.close();
        } else if (b == cancelButton) {
            cancelled = true;
            area = null;
            dialogStage.close();
        }
    }

    @FXML void initialize() { engine = webView.getEngine(); }
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public static ChooseLocationDialog show(Stage stage, Area area, String apiKey) {
        ChooseLocationDialog cld = VTDialog.<ChooseLocationDialog>load(
            ChooseLocationDialog.class.getResource("ChooseLocation.fxml"),
            "Select an Area", stage);
        cld.setInitialValues(area, apiKey);
        cld.show();
        return cld;
    }

    public Area getArea() { return area; }
    
    public boolean cancelled() { return !loaded || cancelled; }
    
/*------------------------------------------------------------------------------
 *
 * PRIVATE Utility Methods
 * 
 *----------------------------------------------------------------------------*/
    
    private void setInitialValues(Area area, String apiKey) {
        if (apiKey == null) {
            logger.severe("API_KEY must be provided to ChooseLocationDialog!");
            dialogStage.close();
            return;
        }
        
        if (area == null || (area.lat == 0 && area.lng == 0)) {
            area = new Area(37.3941542, -122.1498701, 20.0, ""); // Tesla HQ
        } else {
            if (area.name != null) nickname.setText(area.name);
        }
        // Prep the web view...
        String mapHTML = getMapFromTemplate(area.lat, area.lng, area.radius, apiKey);
        engine.getLoadWorker().stateProperty().addListener( new ChangeListener<State>() {
            @Override public void changed(ObservableValue ov, State old, State cur) {
                if (cur == State.SUCCEEDED) {
                    loaded = true;
                }
            }
        });
        engine.loadContent(mapHTML);
    }
    
    private String getMapFromTemplate(double lat, double lng, double radius, String apiKey) {
        SimpleTemplate template = new SimpleTemplate(getClass().getResourceAsStream(MapTemplateFileName));
        return template.fillIn(
                "JFX_LAT", String.valueOf(lat), "JFX_LONG", String.valueOf(lng),
                "JFX_RADIUS", String.valueOf(radius), "JFX_GMAP_API_KEY", apiKey);
    }


}
