/*
 * GeoOptionsDialog.java  - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Mar 05, 2014
 */

package org.noroomattheinn.visibletesla.dialogs;

import java.net.URL;
import java.util.Map;
import java.util.ResourceBundle;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.noroomattheinn.tesla.Tesla;
import org.noroomattheinn.utils.GeoUtils;
import org.noroomattheinn.visibletesla.AppContext;
import org.noroomattheinn.visibletesla.Area;

/**
 * NotifyOptionsDialog
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */

public class GeoOptionsDialog implements DialogUtils.DialogController {

/*------------------------------------------------------------------------------
 *
 * Constants and Enums
 * 
 *----------------------------------------------------------------------------*/

/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
        
    private Stage myStage;
    Area area = null;
    private boolean cancelled;
    private AppContext appContext;
    
/*------------------------------------------------------------------------------
 *
 * UI Elements
 * 
 *----------------------------------------------------------------------------*/
        
    @FXML private ResourceBundle resources;
    @FXML private URL location;

    @FXML private Button lookupButton;
    @FXML private Button okButton;
    @FXML private Button cancelButton;
    @FXML private TextField addrField;
    @FXML private Label latLabel;
    @FXML private Label lngLabel;
    @FXML private Slider radiusSlider;
    @FXML private Label radiusLabel;
    
/*------------------------------------------------------------------------------
 *
 * UI Initialization and Action Handlers
 * 
 *----------------------------------------------------------------------------*/
    
    @FXML void initialize() {
        cancelled = true;
        area = null;
        bind(radiusSlider, radiusLabel);
        okButton.setDisable(true);
        addrField.textProperty().addListener(new ChangeListener<String>() {
            @Override public void changed(ObservableValue<? extends String> ov, String t, String t1) {
                okButton.setDisable(true);
            }
        });
    }
    
    @FXML void buttonHandler(ActionEvent event) {
        Button b = (Button)event.getSource();
        if (b == okButton) {
            cancelled = false;
            area = new Area(area.lat, area.lng, radiusSlider.getValue(), area.name);
                    // Radius might have changed without another lookup
            myStage.close();
        } else if (b == cancelButton) {
            cancelled = true;
            area = null;
            myStage.close();
        } else if (b == lookupButton) {
            double[] latLng = lookupAndShow();
            if (latLng != null) {
                area = new Area(
                        latLng[0], latLng[1], radiusSlider.getValue(),
                        addrField.getText().trim());
                okButton.setDisable(false);
            } else {
                area = null;
                okButton.setDisable(true);
            }
        }
    }
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public Area getArea() { return area; }
    public boolean cancelled() { return cancelled; }

/*------------------------------------------------------------------------------
 *
 * Methods overriden from DialogController
 * 
 *----------------------------------------------------------------------------*/

    @Override public void setStage(Stage stage) { this.myStage = stage; }
    
    @Override public void setProps(Map props) {
        appContext = (AppContext)props.get("APP_CONTEXT");
        if (appContext == null) {
            Tesla.logger.severe("AppContext must be provided to GeoOptionsDialog!");
            myStage.close();
            return;
        }
        
        area = (Area)props.get("AREA");
        if (area == null || area.name == null || area.name.trim().isEmpty() ||
                (area.lat == 0 && area.lng == 0)) {
            addrField.setText("");
            latLabel.setText(String.format("%3.5f", 0.0));
            lngLabel.setText(String.format("%3.5f", 0.0));
            radiusSlider.setValue(10);
            okButton.setDisable(true);
        } else {
            latLabel.setText(String.format("%3.5f", area.lat));
            lngLabel.setText(String.format("%3.5f", area.lng));
            radiusSlider.setValue(area.radius);
            addrField.setText(area.name);
            okButton.setDisable(false);
        }
    }
    
    
/*------------------------------------------------------------------------------
 *
 * PRIVATE - Utility Methods
 * 
 *----------------------------------------------------------------------------*/
    
    private double[] lookupAndShow() {
        String addr = addrField.getText().trim();
        if (addr.length() != 0) {
            double[] latLng = GeoUtils.getLatLngForAddr(addr);
            if (latLng != null) {
                latLabel.setText(String.format("%3.5f", latLng[0]));
                lngLabel.setText(String.format("%3.5f", latLng[1]));
                okButton.setDisable(false);
                String url = String.format(
                        "https://maps.google.com/maps?q=%f,%f(Area)&z=18&output=embed",
                        latLng[0], latLng[1]);
                appContext.app.getHostServices().showDocument(url);
                return latLng;
            }
        }
        
        latLabel.setText(String.format("%3.5f", 0.0));
        lngLabel.setText(String.format("%3.5f", 0.0));
        okButton.setDisable(true);
        return null;
    }
    
    private void bind(final Slider slider, final Label label) {
        label.setText(Math.round(slider.getValue()) + "");
        slider.valueProperty().addListener(new ChangeListener<Number>() {
            @Override public void changed(ObservableValue<? extends Number> observableValue,
                                          Number oldValue, Number newValue) {
                if (newValue == null) {
                    label.setText("...");
                } else {
                    label.setText(Math.round(newValue.intValue())+"");
                }
            }
        });
    }
}
