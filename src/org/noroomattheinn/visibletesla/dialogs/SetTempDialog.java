/*
 * SetValueDialog.java  - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Feb 16, 2014
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
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.stage.Stage;

/**
 * SetValueDialog
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */

public class SetTempDialog implements DialogUtils.DialogController {

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
    private boolean useDegreesF;
    private double finalValue;
    private boolean cancelled;
    
/*------------------------------------------------------------------------------
 *
 * UI Elements
 * 
 *----------------------------------------------------------------------------*/
        
    @FXML private ResourceBundle resources;
    @FXML private URL location;

    @FXML private Button okButton;
    @FXML private Button cancelButton;
    @FXML private Slider valueSlider;
    @FXML private Label tempLabel;
    @FXML private CheckBox useCarSetpoint;
    
/*------------------------------------------------------------------------------
 *
 * UI Initialization and Action Handlers
 * 
 *----------------------------------------------------------------------------*/
    
    @FXML void initialize() {
        useDegreesF = true;     // Default
        cancelled = true;
        finalValue = -1;
        bind(valueSlider, tempLabel);
        setUnits(useDegreesF);
        useCarSetpoint.selectedProperty().addListener(new ChangeListener<Boolean>() {
            @Override public void changed(ObservableValue<? extends Boolean> ov, Boolean t, Boolean t1) {
                valueSlider.setDisable(t1);
                tempLabel.setDisable(t1);
            }
        });
    }
    
    @FXML void buttonHandler(ActionEvent event) {
        Button b = (Button)event.getSource();
        if (b == okButton) {
            cancelled = false;
            finalValue = valueSlider.getValue();
        } else if (b == cancelButton) {
            cancelled = true;
            finalValue = -1;
        }
        myStage.close();
    }
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public void setUnits(boolean useDegreesF) {
        if (useDegreesF) {
            valueSlider.setMin(62);
            valueSlider.setMax(90);
            valueSlider.setMajorTickUnit(2);
            valueSlider.setMinorTickCount(1);
            valueSlider.setValue(70);   // Default Value
        } else {
            valueSlider.setMin(17.0);
            valueSlider.setMax(32.0);
            valueSlider.setMajorTickUnit(1);
            valueSlider.setMinorTickCount(1);
            valueSlider.setValue(19.5); // Default Value
        }
    }
    
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
        Boolean udf = (Boolean)props.get("USE_DEGREES_F");
        if (udf != null) useDegreesF = udf;
        setUnits(useDegreesF);
        
        Double initTemp = (Double)props.get("INIT_TEMP");
        if (initTemp != null && initTemp > 0) {
            setValue(initTemp);
            this.useCarSetpoint.setSelected(false);
        } else {
            this.useCarSetpoint.setSelected(true);
        }
    }
    
    
/*------------------------------------------------------------------------------
 *
 * PRIVATE - Methods that handle the details of the different value types
 * 
 *----------------------------------------------------------------------------*/

    private double adjustValue(double orig) {
        return useDegreesF ? Math.round(orig) : nearestHalf(orig);
    }
    
    private double nearestHalf(double val) { return Math.floor(val*2.0)/2.0; }
    
    private  void setValue(double value) {
        finalValue = value;
        valueSlider.setValue(value);
    }
    
/*------------------------------------------------------------------------------
 *
 * PRIVATE - Utility Methods
 * 
 *----------------------------------------------------------------------------*/
    
    private void bind(final Slider slider, final Label label) {
        double val = adjustValue(slider.getValue());
        label.setText(String.format("%3f.1", val));
        slider.valueProperty().addListener(new ChangeListener<Number>() {
            @Override public void changed(ObservableValue<? extends Number> observableValue,
                                          Number oldValue, Number newValue) {
                if (newValue == null) {
                    label.setText("...");
                } else {
                    double val = adjustValue(newValue.doubleValue());
                    label.setText(String.format("%3.1f", val));
                }
            }
        });
    }
    
}
