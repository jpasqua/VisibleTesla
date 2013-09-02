/*
 * HVACController.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Jul 22, 2013
 */

package org.noroomattheinn.visibletesla;

import java.util.concurrent.Callable;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.ImageView;
import org.noroomattheinn.tesla.GUIState;
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

    private static final String degreesF = "º F";
    private static final String degreesC = "º C";

    /*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private org.noroomattheinn.tesla.HVACController controller;
    private HVACState hvacState;
    private boolean useDegreesF = false;
    
/*------------------------------------------------------------------------------
 *
 * UI Elements
 * 
 *----------------------------------------------------------------------------*/
    
    // Temperature Readouts    
    @FXML private Label insideTmpLabel, outsideTempLabel;
    @FXML private Label driverTempLabel;
    
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
    @FXML private Button    driverTempUpButton, driverTempDownButton;

/*------------------------------------------------------------------------------
 *
 *  UI Action Handlers
 * 
 *----------------------------------------------------------------------------*/
    
    @FXML void hvacOnOffHandler(ActionEvent event) {
        issueCommand(new Callable<Result>() {
            public Result call() {
                return controller.setAC(hvacOnButton.isSelected()); } },
                AfterCommand.Refresh);
    }


    @FXML void tempTargetHandler(ActionEvent event) {
        Button source = (Button)event.getSource();
        int increment = (source == driverTempUpButton) ? 1 : -1;
        double tempC = hvacState.driverTemp();
        final double temp = (useDegreesF ? Utils.cToF(tempC) : tempC) + increment;
        final boolean setF = useDegreesF;   // Must be final, so copy it...
        issueCommand(new Callable<Result>() {
            public Result call() { 
                if (setF) return controller.setTempF(temp, temp);
                return controller.setTempC(temp, temp); }
            }, AfterCommand.Refresh);
    }
    
/*------------------------------------------------------------------------------
 *
 * Methods overridden from BaseController
 * 
 *----------------------------------------------------------------------------*/
    
    protected void prepForVehicle(Vehicle v) {
        if (differentVehicle(controller, v)) {
            controller = new org.noroomattheinn.tesla.HVACController(v);
            hvacState = new HVACState(v);
            GUIState gs = v.cachedGUIState();
            useDegreesF = gs.temperatureUnits().equalsIgnoreCase("F");
            updateWheelView();  // Make sure we show the right wheels from the get-go
        }            
        if (appContext.simulatedUnits.get() != null)
            useDegreesF = (appContext.simulatedUnits.get() == Utils.UnitType.Imperial);
    }

    protected void refresh() { updateState(hvacState); }

    protected void reflectNewState() {
        updateWheelView();
        reflectHVACOnState();
        reflectActualTemps();
        reflectDefrosterState();
    }

    
    // Controller-specific initialization
    protected void fxInitialize() {    }    
    
    
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
        driverTempUpButton.setDisable(!hvacOn);
        driverTempDownButton.setDisable(!hvacOn);
        reflectFanStatus();
        updateCoolHotImages();
        setTempLabel(driverTempLabel, hvacState.driverTemp(), false);
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
        setTempLabel(insideTmpLabel, hvacState.insideTemp(), true);
        setTempLabel(outsideTempLabel, hvacState.outsideTemp(), true);
    }
    
    public void updateCoolHotImages() {
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

    private void setTempLabel(Label label, double temp, boolean displayUnits) {
        if (Double.isNaN(temp)) {   // No value is available
            label.setText("...");
            return;
        }
        if (useDegreesF) temp = Utils.cToF(temp);
        String tempAsString = String.valueOf((int)(temp + 0.5));
        if (displayUnits) tempAsString = tempAsString + (useDegreesF ? degreesF : degreesC);
        label.setText(tempAsString);
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
