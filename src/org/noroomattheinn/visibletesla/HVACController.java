/*
 * HVACController.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Jul 22, 2013
 */

package org.noroomattheinn.visibletesla;

import java.util.concurrent.Callable;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import org.noroomattheinn.tesla.HVACState;
import org.noroomattheinn.tesla.Options.WheelType;
import org.noroomattheinn.tesla.Result;
import org.noroomattheinn.tesla.Vehicle;
import org.noroomattheinn.utils.Utils;

public class HVACController extends BaseController {
    
/*------------------------------------------------------------------------------
 *
 * Constants and Enums
 * 
 *----------------------------------------------------------------------------*/

    private static final String DegreesF = "ºF";
    private static final String DegreesC = "ºC";

/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private org.noroomattheinn.tesla.HVACController controller;
    private HVACState hvacState;
    private boolean useDegreesF = false;
    DoubleProperty sliderValue = new SimpleDoubleProperty(70);
    
/*------------------------------------------------------------------------------
 *
 * UI Elements
 * 
 *----------------------------------------------------------------------------*/
    
    // Temperature Readouts    
    @FXML private Label insideTmpLabel, outsideTempLabel;
    @FXML private Label targetTempLabel;
    
    // Cold / Medium / Hot Images    
    @FXML private ImageView climateColdImg;
    @FXML private ImageView climateHotImg;
    @FXML private ImageView coldWSImg;
    
    // Fan Speed Images
    @FXML private ImageView fan0, fan1, fan2, fan3, fan4;
            
    // Defroster On/Off Images    
    @FXML private ImageView frontDefOffImg;
    @FXML private ImageView frontDefOnImg;
    @FXML private ImageView rearDefOffImg;
    @FXML private ImageView rearDefOnImg;

    // Wheel Images
    @FXML private ImageView darkRimFront, darkRimRear;
    @FXML private ImageView nineteenRimFront, nineteenRimRear;

    // Controls
    @FXML private ToggleButton  hvacOffButton, hvacOnButton;
    @FXML private Slider        tempSlider;
    
/*------------------------------------------------------------------------------
 *
 *  UI Action Handlers
 * 
 *----------------------------------------------------------------------------*/
    
    @FXML void hvacOnOffHandler(ActionEvent event) {
        issueCommand(new Callable<Result>() {
            @Override public Result call() {
                return controller.setAC(hvacOnButton.isSelected()); } },
                AfterCommand.RefreshLater);
    }
    
    @FXML void tempChangeHandler(MouseEvent event) {
        double temp = tempSlider.valueProperty().doubleValue();
        if (useDegreesF) temp = Math.round(temp);
        else temp = nearestHalf(temp);
        //targetTempLabel.setText(String.format(useDegreesF ? "%.0f ºF" : "%.1f ºC", temp));
        tempSlider.setValue(temp);
        setTemp(temp);
    }

    private void setTemp(final double temp) {
        final boolean setF = useDegreesF;   // Must be final, so copy it...
        issueCommand(new Callable<Result>() {
            @Override public Result call() { 
                if (setF) return controller.setTempF(temp, temp);
                return controller.setTempC(temp, temp); }
            }, AfterCommand.Refresh);
    }
    
    private double nearestHalf(double val) { return Math.floor(val*2.0)/2.0; }
    
/*------------------------------------------------------------------------------
 *
 * Methods overridden from BaseController
 * 
 *----------------------------------------------------------------------------*/
    
    @Override protected void prepForVehicle(Vehicle v) {
        if (differentVehicle(controller, v)) {
            controller = new org.noroomattheinn.tesla.HVACController(v);
            hvacState = new HVACState(v);
            useDegreesF =  appContext.cachedGUIState.temperatureUnits().equalsIgnoreCase("F");
            updateWheelView();  // Make sure we show the right wheels from the get-go
        }            
        if (appContext.simulatedUnits.get() != null)
            useDegreesF = (appContext.simulatedUnits.get() == Utils.UnitType.Imperial);
        
        if (useDegreesF) {
            tempSlider.setMin(65);
            tempSlider.setMax(80);
            tempSlider.setMajorTickUnit(5);
            tempSlider.setMinorTickCount(4);
        } else {
            tempSlider.setMin(18.0);
            tempSlider.setMax(27.0);
            tempSlider.setMajorTickUnit(1);
            tempSlider.setMinorTickCount(1);
        }
    }

    @Override protected void refresh() { updateState(hvacState); }

    @Override protected void reflectNewState() {
        updateWheelView();
        reflectHVACOnState();
        reflectActualTemps();
        reflectDefrosterState();
    }
    
    // Controller-specific initialization
    @Override protected void fxInitialize() {
        tempSlider.valueProperty().addListener(new ChangeListener<Number>() {
            @Override public void changed(ObservableValue<? extends Number> ov,
                                          Number old, Number cur) {
                double temp = adjustedTemp(tempSlider.valueProperty().doubleValue());
                targetTempLabel.setText(String.format(useDegreesF ? "%.0f ºF" : "%.1f ºC", temp));
            }
        });
    }    
    
    private double adjustedTemp(double temp) {
        return (useDegreesF) ? Math.round(temp): nearestHalf(temp);
    }
/*------------------------------------------------------------------------------
 *
 * Methods to Reflect the State of the HVAC System
 * 
 *----------------------------------------------------------------------------*/
    
    private void reflectHVACOnState() {
        // Determining whether the HVAC is on is a little tricky. You'd think
        // you could just look at hvacState.autoConditioning, but that only tells
        // you whether the AC is running - not the heat. Until I determine a better
        // way, I'm using the fan speed to indicate whether the HVAC is on and using
        // the temp vs. temp set point to determine whether it is heating or cooling.
        boolean hvacOn = (hvacState.fanStatus() > 0);
        hvacOnButton.setSelected(hvacOn);
        hvacOffButton.setSelected(!hvacOn);
        reflectFanStatus();
        updateCoolHotImages();
        double temp = hvacState.driverTemp();
        if (useDegreesF) temp = Math.round(Utils.cToF(temp));
        else temp = nearestHalf(temp);
        tempSlider.setValue(temp);
    }
    
    private void reflectFanStatus() {
        // Ensure all the fan images are off
        fan0.setVisible(false); fan1.setVisible(false);
        fan2.setVisible(false); fan3.setVisible(false); fan4.setVisible(false);
        
        // Now turn on the right one...
        int fanSpeed = hvacState.fanStatus();   // Range of 0-7
        if (fanSpeed >= 6) fan4.setVisible(true);
        else if (fanSpeed >= 4) fan3.setVisible(true);
        else if (fanSpeed >= 2) fan2.setVisible(true);
        else if (fanSpeed == 1) fan1.setVisible(true);
        else fan0.setVisible(true); 
    }
    
    private void reflectDefrosterState() {
        setOptionState(hvacState.isFrontDefrosterOn() != 0, frontDefOnImg, frontDefOffImg);
        setOptionState(hvacState.isRearDefrosterOn(), rearDefOnImg, rearDefOffImg);
    }
    
    private void reflectActualTemps() {
        setTempLabel(insideTmpLabel, hvacState.insideTemp());
        setTempLabel(outsideTempLabel, hvacState.outsideTemp());
    }
    
    private void updateCoolHotImages() {
        climateColdImg.setVisible(false);
        climateHotImg.setVisible(false);
        
        if (hvacState.fanStatus() > 0) {
            double insideTemp = hvacState.insideTemp();
            if (insideTemp > hvacState.driverTemp())
                climateColdImg.setVisible(true);
            else if (insideTemp < hvacState.driverTemp()) {
                climateHotImg.setVisible(true);
            }
        }
    } 

    private void setTempLabel(Label label, double tempC) {
        if (Double.isNaN(tempC)) {   // No value is available
            label.setText("...");
            return;
        }
        double temp = adjustedTemp((useDegreesF) ? Utils.cToF(tempC) : tempC);
        label.setText(String.format(useDegreesF ? "%.0f ºF" : "%.1f ºC", temp));
    }

    
    // TO DO: This wheel-related code duplicates functionality in OverviewController. 
    // Refactor this to make it shareable (somehow).
    private void updateWheelView() {
        WheelType wt = (appContext.simulatedWheels.get() == null) ?
                vehicle.getOptions().wheelType() : appContext.simulatedWheels.get();
        
        nineteenRimFront.setVisible(false);
        nineteenRimRear.setVisible(false);
        darkRimFront.setVisible(false);
        darkRimRear.setVisible(false);
        switch (wt) {
            case WT19:
                nineteenRimFront.setVisible(true);
                nineteenRimRear.setVisible(true);
                break;
            case WTSP:
            case WTSG:
                darkRimFront.setVisible(true);
                darkRimRear.setVisible(true);
                break;
            case WT21:
            default:    // Unknown, use default which is WT21
                break;
        }
    }


}
