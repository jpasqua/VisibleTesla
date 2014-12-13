/*
 * SetValueDialog.java  - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Feb 16, 2014
 */

package org.noroomattheinn.visibletesla.dialogs;

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

public class SetChargeDialog extends VTDialog.Controller {
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private double  finalValue;
    private boolean cancelled;
    
/*------------------------------------------------------------------------------
 *
 * Internal State - UI Components
 * 
 *----------------------------------------------------------------------------*/
        
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
    
    @FXML private void initialize() {
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
    
    @FXML private void buttonHandler(ActionEvent event) {
        Button b = (Button)event.getSource();
        if (b == okButton) {
            cancelled = false;
            finalValue = Math.round(chargeSlider.getValue());
        } else if (b == cancelButton) {
            cancelled = true;
            finalValue = -1;
        }
        dialogStage.close();
    }

    @FXML private void rangeLinkHandler(ActionEvent event) {
        Hyperlink h = (Hyperlink)event.getSource();
        chargeSlider.setValue((h == stdLink) ? 90 : 100);
    }    
    
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public static SetChargeDialog show(Stage stage, Double initCharge) {
        SetChargeDialog scd = VTDialog.<SetChargeDialog>load(
            SetChargeDialog.class.getResource("SetChargeDialog.fxml"),
            "Target Charge Level", stage);
        scd.setInitialValues(initCharge);
        scd.show();
        return scd;
    }
    
    public double getValue() { return finalValue; }
    public boolean cancelled() { return cancelled; }
    public boolean useCarsValue() { return useCarSetpoint.isSelected(); }

/*------------------------------------------------------------------------------
 *
 * PRIVATE - Utility Methods
 * 
 *----------------------------------------------------------------------------*/
    
    public void setInitialValues(Double initCharge) {
        if (initCharge != null && initCharge > 0) {
            chargeSlider.setValue(initCharge);
            this.useCarSetpoint.setSelected(false);
        } else {
            this.useCarSetpoint.setSelected(true);
        }
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
