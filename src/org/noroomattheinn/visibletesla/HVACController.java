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
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import org.noroomattheinn.tesla.HVACState;
import org.noroomattheinn.tesla.Options;
import org.noroomattheinn.tesla.Result;
import org.noroomattheinn.tesla.Vehicle;
import org.noroomattheinn.utils.Utils;

public class HVACController extends BaseController {
    
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
    
    private boolean useDegreesF = false;
    private Vehicle v;
    DoubleProperty sliderValue = new SimpleDoubleProperty(70);
    
/*------------------------------------------------------------------------------
 *
 * UI Elements
 * 
 *----------------------------------------------------------------------------*/
    
    @FXML private ImageView bodyImg;
    
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
                Result r = v.setAC(hvacOnButton.isSelected());
                updateStateLater(Vehicle.StateType.HVAC, 5 * 1000);
                return r;
            } });
    }
    
    // Only called on MOUSE_RELEASED
    @FXML void tempChangeHandler(MouseEvent event) {
        double temp = tempSlider.valueProperty().doubleValue();
        if (useDegreesF) temp = Math.round(temp);
        else temp = nearestHalf(temp);
        tempSlider.setValue(temp);
        setTemp(temp);
    }

    private void setTemp(final double temp) {
        final boolean setF = useDegreesF;   // Must be final, so copy it...
        issueCommand(new Callable<Result>() {
            @Override public Result call() {
                Result r = (setF) ? v.setTempF(temp, temp)
                                  : v.setTempC(temp, temp);
                updateStateLater(Vehicle.StateType.HVAC, 5 * 1000);
                return r;
            } });
    }
    
    private double nearestHalf(double val) { return Math.floor(val*2.0)/2.0; }
    
/*------------------------------------------------------------------------------
 *
 * Methods overridden from BaseController
 * 
 *----------------------------------------------------------------------------*/
    
    @Override protected void initializeState() {
        v = ac.vehicle;
        ac.lastKnownHVACState.addListener(new ChangeListener<HVACState>() {
            @Override public void changed(ObservableValue<? extends HVACState> ov,
                HVACState old, HVACState cur) {
                if (active()) { reflectNewState(); }
            }
        });
        
        getAppropriateBody();   // Get the body image that matches the wheels
        ac.prefs.overideWheelsTo.addListener(new ChangeListener<String>() {
            @Override public void changed(
                    ObservableValue<? extends String> ov, String t, String t1) {
                getAppropriateBody();   // Get the body image that matches the wheels
            }
        });
        ac.prefs.overideWheelsActive.addListener(new ChangeListener<Boolean>() {
            @Override public void changed(
                    ObservableValue<? extends Boolean> ov, Boolean t, Boolean t1) {
                getAppropriateBody();   // Get the body image that matches the wheels
            }
        });
        useDegreesF = ac.utils.useDegreesF();
    }
    
    @Override protected void activateTab() {
        useDegreesF = ac.utils.useDegreesF();
        if (useDegreesF) {
            tempSlider.setMin(62);
            tempSlider.setMax(90);
            tempSlider.setMajorTickUnit(2);
            tempSlider.setMinorTickCount(1);
            tempSlider.setValue(70);    // Until the real value is retrieved
        } else {
            tempSlider.setMin(17.0);
            tempSlider.setMax(32.0);
            tempSlider.setMajorTickUnit(1);
            tempSlider.setMinorTickCount(1);
            tempSlider.setValue(19.5);    // Until the real value is retrieved
        }
    }
    
    @Override protected void refresh() { updateState(Vehicle.StateType.HVAC); }

    // Controller-specific initialization
    @Override protected void fxInitialize() {
        tempSlider.valueProperty().addListener(new ChangeListener<Number>() {
            @Override public void changed(ObservableValue<? extends Number> ov,
                                          Number old, Number cur) {
                double temp = adjustedTemp(cur.doubleValue());
                targetTempLabel.setText(String.format(useDegreesF ? "%.0f ºF" : "%.1f ºC", temp));
            }
        });
    }    
    
/*------------------------------------------------------------------------------
 *
 * Methods to Reflect the State of the HVAC System
 * 
 *----------------------------------------------------------------------------*/

    private void reflectNewState() {
        reflectHVACOnState();
        reflectActualTemps();
        reflectDefrosterState();
    }
    
    private void reflectHVACOnState() {
        // Determining whether the HVAC is on is a little tricky. You'd think
        // you could just look at hvac.state.autoConditioning, but that only tells
        // you whether the AC is running - not the heat. Until I determine a better
        // way, I'm using the fan speed to indicate whether the HVAC is on and using
        // the temp vs. temp set point to determine whether it is heating or cooling.
        HVACState hvac = ac.lastKnownHVACState.get();
        boolean hvacOn = (hvac.fanStatus > 0);
        hvacOnButton.setSelected(hvacOn);
        hvacOffButton.setSelected(!hvacOn);
        reflectFanStatus();
        updateCoolHotImages();
        double temp = hvac.driverTemp;
        if (useDegreesF) temp = Math.round(Utils.cToF(temp));
        else temp = nearestHalf(temp);
        tempSlider.setValue(temp);
    }
    
    private void reflectFanStatus() {
        // Ensure all the fan images are off
        fan0.setVisible(false); fan1.setVisible(false);
        fan2.setVisible(false); fan3.setVisible(false); fan4.setVisible(false);
        
        // Now turn on the right one...
        int fanSpeed = ac.lastKnownHVACState.get().fanStatus;   // Range of 0-7
        if (fanSpeed >= 6) fan4.setVisible(true);
        else if (fanSpeed >= 4) fan3.setVisible(true);
        else if (fanSpeed >= 2) fan2.setVisible(true);
        else if (fanSpeed == 1) fan1.setVisible(true);
        else fan0.setVisible(true); 
    }
    
    private void reflectDefrosterState() {
        HVACState hvac = ac.lastKnownHVACState.get();
        setOptionState(hvac.isFrontDefrosterOn != 0, frontDefOnImg, frontDefOffImg);
        setOptionState(hvac.isRearDefrosterOn, rearDefOnImg, rearDefOffImg);
    }
    
    private void reflectActualTemps() {
        HVACState hvac = ac.lastKnownHVACState.get();
        setTempLabel(insideTmpLabel, hvac.insideTemp);
        setTempLabel(outsideTempLabel, hvac.outsideTemp);
    }
    
    private void updateCoolHotImages() {
        HVACState hvac = ac.lastKnownHVACState.get();
        climateColdImg.setVisible(false);
        climateHotImg.setVisible(false);
        
        if (hvac.fanStatus > 0) {
            double insideTemp = hvac.insideTemp;
            if (insideTemp > hvac.driverTemp)
                climateColdImg.setVisible(true);
            else if (insideTemp < hvac.driverTemp) {
                climateHotImg.setVisible(true);
            }
        }
    } 
    
    private double adjustedTemp(double temp) {
        return (useDegreesF) ? Math.round(temp): nearestHalf(temp);
    }
    
    private void setTempLabel(Label label, double tempC) {
        if (Double.isNaN(tempC)) {   // No value is available
            label.setText("...");
            return;
        }
        double temp = adjustedTemp((useDegreesF) ? Utils.cToF(tempC) : tempC);
        label.setText(String.format(useDegreesF ? "%.0f ºF" : "%.1f ºC", temp));
    }
    
    private static final String ImagePrefix = "org/noroomattheinn/TeslaResources/";
    private void getAppropriateBody() {
        ClassLoader cl = getClass().getClassLoader();
        Options.WheelType wt = ac.utils.computedWheelType();
        String image;
        
        switch (wt) {
            case WT19:  // Silver 19" Wheels
            case WT1P:
            case WTX1:
                image = "05_climate_car_19@2x.png";
                break;
            case WTAE:  // Aero Wheels
            case WTAP:
                image = "05_climate_car_aero@2x.png";
                break;
            case WTTB:  // Turbine 19" Silver
            case WTTP:
                image = "05_climate_car_turbine@2x.png";
                break;
            case WTTG:  // Turbine 19" Charcoal
            case WTGP:
                image = "05_climate_car_turbine_dark@2x.png";
                break;
            case WTSP:  // Charcoal 21"
            case WTSE:
            case WTSG:
                image = "05_climate_car_21_dark@2x.png";
                break;
            case WT21:  // Silver 21"
            case WT2E:
            case WTSS:
            default:
                image = "05_climate_car_21@2x.png";
                break;
        }
        bodyImg.setImage(new Image(cl.getResourceAsStream(ImagePrefix+image)));
    }
}
