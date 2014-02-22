/*
 * SetValueDialog.java  - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Feb 16, 2014
 */

package org.noroomattheinn.visibletesla.dialogs;

import org.noroomattheinn.visibletesla.dialogs.DialogUtils;
import java.net.URL;
import java.util.Map;
import java.util.ResourceBundle;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.stage.Stage;

/**
 * SetChargeDialog
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */

public class SetChargeDialog implements DialogUtils.DialogController {

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
    private double finalValue;
    private boolean cancelled;
    private Map props;
    
/*------------------------------------------------------------------------------
 *
 * UI Elements
 * 
 *----------------------------------------------------------------------------*/
        
    @FXML private ResourceBundle resources;
    @FXML private URL location;

    @FXML private Button okButton;
    @FXML private Button cancelButton;
    @FXML private Slider chargeSlider;
    @FXML private Label chargeLabel;
    @FXML private Hyperlink stdLink;
    @FXML private Hyperlink maxLink;
    @FXML private CheckBox useCarSetpoint;
    
/*------------------------------------------------------------------------------
 *
 * UI Initialization and Action Handlers
 * 
 *----------------------------------------------------------------------------*/
    
    @FXML void initialize() {
        cancelled = true;
        finalValue = -1;
        bind(chargeSlider, chargeLabel);
        useCarSetpoint.selectedProperty().addListener(new ChangeListener<Boolean>() {
            @Override public void changed(ObservableValue<? extends Boolean> ov, Boolean t, Boolean t1) {
                chargeSlider.setDisable(t1);
                chargeLabel.setDisable(t1);
            }
        });
    }
    
    @FXML void buttonHandler(ActionEvent event) {
        Button b = (Button)event.getSource();
        if (b == okButton) {
            cancelled = false;
            finalValue = Math.round(chargeSlider.getValue());
        } else if (b == cancelButton) {
            cancelled = true;
            finalValue = -1;
        }
        myStage.close();
    }

    @FXML void rangeLinkHandler(ActionEvent event) {
        Hyperlink h = (Hyperlink)event.getSource();
        chargeSlider.setValue((h == stdLink) ? 90 : 100);
    }    
    
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public double getValue() { return finalValue; }
    public boolean cancelled() { return cancelled; }
    public boolean useCarsValue() { return useCarSetpoint.isSelected(); }

/*------------------------------------------------------------------------------
 *
 * Methods overriden from DialogController
 * 
 *----------------------------------------------------------------------------*/

    @Override public void setStage(Stage stage) { this.myStage = stage; }
    @Override public void setProps(Map props) {
        Double initCharge = (Double)props.get("INIT_CHARGE");
        if (initCharge != null && initCharge > 0) {
            chargeSlider.setValue(initCharge);
            this.useCarSetpoint.setSelected(false);
        } else {
            this.useCarSetpoint.setSelected(true);
        }
    }
    
    
/*------------------------------------------------------------------------------
 *
 * PRIVATE - Utility Methods
 * 
 *----------------------------------------------------------------------------*/
    
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
