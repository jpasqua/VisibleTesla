/*
 * ChargeController.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Jul 22, 2013
 */

package org.noroomattheinn.visibletesla;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.Callable;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import jfxtras.labs.scene.control.gauge.Battery;
import jfxtras.labs.scene.control.gauge.Lcd;
import org.noroomattheinn.tesla.ChargeState;
import org.noroomattheinn.tesla.GUIState;
import org.noroomattheinn.tesla.Result;
import org.noroomattheinn.tesla.Vehicle;
import org.noroomattheinn.utils.Utils;


public class ChargeController extends BaseController {
    
/*------------------------------------------------------------------------------
 *
 * Constants and Enums
 * 
 *----------------------------------------------------------------------------*/

    private static final double KilometersPerMile = 1.60934;
    private static final String MinVersionForChargePct = "1.33.38";
        // This value is taken from the wiki here: http://tinyurl.com/mzwxbps
    
/*------------------------------------------------------------------------------
 *
 * Internal Status
 * 
 *----------------------------------------------------------------------------*/
    
    private ChargeState charge;
    private org.noroomattheinn.tesla.ChargeController chargeController;
    private boolean useMiles;
    
/*------------------------------------------------------------------------------
 *
 * UI Elements
 * 
 *----------------------------------------------------------------------------*/
    
    @FXML private Button startButton, stopButton;
    @FXML private Slider chargeSlider;
    @FXML private Label chargeSetting;
    @FXML private Hyperlink stdLink, maxLink;
    
    // Elements that display charge status
    @FXML private Battery batteryGauge;
    @FXML private ImageView snowflake;
    @FXML private Label batteryPercentLabel;
    
    // Elements that display reminaing range
    @FXML private Lcd estOdometer;
    @FXML private Lcd idealOdometer;
    @FXML private Lcd ratedOdometer;
    
    // Charging Schedule
    @FXML private Label chargeScheduledLabel, scheduledTimeLabel;

    // The Charge Property TableView and its Columns
    @FXML private TableView<GenericProperty> propertyTable;
    @FXML private TableColumn<GenericProperty,String> propNameColumn;
    @FXML private TableColumn<GenericProperty,String> propValColumn;
    @FXML private TableColumn<GenericProperty,String> propUnitsColumn;
    
/*------------------------------------------------------------------------------
 *
 * The Elements of the Property Table
 * 
 *----------------------------------------------------------------------------*/
    
    // Each Property defines a row of the table...
    private final GenericProperty pilotCurrent = new GenericProperty("Pilot Current", "0.0", "Amps");
    private final GenericProperty voltage = new GenericProperty("Voltage", "0.0", "Volts");
    private final GenericProperty batteryCurrent = new GenericProperty("Battery Current", "0.0", "Amps");
    private final GenericProperty nRangeCharges = new GenericProperty("# Range Charges", "0.0", "Count");
    private final GenericProperty fastCharger = new GenericProperty("Supercharger", "No", "");
    private final GenericProperty chargeRate = new GenericProperty("Charge Rate", "0.0", "MPH");
    private final GenericProperty remaining = new GenericProperty("Time Left", "00:00:00", "HH:MM:SS");
    private final GenericProperty actualCurrent = new GenericProperty("Current", "0.0", "Amps");
    private final GenericProperty chargerPower = new GenericProperty("Charger Power", "0.0", "kW");
    private final GenericProperty chargingState = new GenericProperty("State", "Disconnected", "");
    
    final ObservableList<GenericProperty> data = FXCollections.observableArrayList(
            actualCurrent, voltage, chargeRate, remaining, chargingState,
            pilotCurrent, batteryCurrent, fastCharger, chargerPower,
            nRangeCharges);

    
/*------------------------------------------------------------------------------
 *
 *  UI Action Handlers
 * 
 *----------------------------------------------------------------------------*/
    
    @FXML private void sliderMoved(MouseEvent event) {
        setChargePercent((int)chargeSlider.getValue());
    }
    
    @FXML void rangeLinkHandler(ActionEvent event) {
        Hyperlink h = (Hyperlink)event.getSource();
        int percent = (h == stdLink) ?
                charge.state.chargeLimitSOCStd : charge.state.chargeLimitSOCMax;
        setChargePercent(percent);
    }

    
    @FXML void chargeButtonHandler(ActionEvent event) {
        final Button b = (Button)event.getSource();
        issueCommand(new Callable<Result>() {
            @Override public Result call() {
                return chargeController.setChargeState(b == startButton); } },
            AfterCommand.Refresh);
    }
    
    private void setChargePercent(final int percent) {
        chargeSlider.setValue(percent);
        chargeSetting.setText(percent + " %");
        issueCommand(new Callable<Result>() {
            @Override public Result call() {
                return chargeController.setChargePercent(percent); } },
            AfterCommand.Refresh);
    }
    
    
/*------------------------------------------------------------------------------
 *
 * Methods overridden from BaseController
 * 
 *----------------------------------------------------------------------------*/

    @Override protected void fxInitialize() {
        // Prepare the property table
        propNameColumn.setCellValueFactory( new PropertyValueFactory<GenericProperty,String>("name") );
        propValColumn.setCellValueFactory( new PropertyValueFactory<GenericProperty,String>("value") );
        propUnitsColumn.setCellValueFactory( new PropertyValueFactory<GenericProperty,String>("units") );
        propertyTable.setItems(data);
        updatePendingChargeLabels(false);
        
        // Until we know that we've got the right software version, disable the slider
        chargeSlider.setDisable(true);
    }

    @Override protected void prepForVehicle(Vehicle v) {
        if (differentVehicle()) {
            chargeController = new org.noroomattheinn.tesla.ChargeController(v);
            charge = new ChargeState(v);
        }
        
        GUIState.State guiState = appContext.lastKnownGUIState.get();
        useMiles = guiState.distanceUnits.equalsIgnoreCase("mi/hr");
        if (appContext.simulatedUnits.get() != null)
            useMiles = (appContext.simulatedUnits.get() == Utils.UnitType.Imperial);
        String units = useMiles ? "Miles" : "Km";
        estOdometer.setUnit(units);
        idealOdometer.setUnit(units);
        ratedOdometer.setUnit(units);

        chargeRate.setUnits(useMiles ? "mph" : "km/h");
    }

    @Override protected void refresh() {  updateState(charge); }
    
    @Override protected void reflectNewState() {
        if (charge.state == null) return; // No State Yet...
        
        reflectRange();
        reflectBatteryStats();
        reflectChargeStatus();
        reflectProperties();
        chargeSlider.setDisable(Utils.compareVersions(
            appContext.lastKnownVehicleState.get().version, MinVersionForChargePct) < 0);
        boolean isConnectedToPower = (charge.state.chargerPilotCurrent > 0);
        startButton.setDisable(!isConnectedToPower);
        stopButton.setDisable(!isConnectedToPower);
    }
    
    public static int getPilotCurent(ChargeState cs) {
        for (int i = 0; i < 3; i++) {
            int pilotCurrent = cs.state.chargerPilotCurrent;
            if (pilotCurrent >= 0) return pilotCurrent;
            Utils.sleep(1000);
            cs.refresh();
        }
        return -1;
    }
    
/*------------------------------------------------------------------------------
 *
 * Methods to Reflect the Status of the Charge
 * 
 *----------------------------------------------------------------------------*/
    
    private void reflectProperties() {
        double conversionFactor = useMiles ? 1.0 : KilometersPerMile;
        int pc = charge.state.chargerPilotCurrent;
        if (pc == -1) pilotCurrent.setValue("Unknown");
        else pilotCurrent.setValue(String.valueOf(pc));
        voltage.setValue(String.valueOf(charge.state.chargerVoltage));
        batteryCurrent.setValue(String.format("%.1f", charge.state.batteryCurrent));
        nRangeCharges.setValue(String.valueOf(charge.state.maxRangeCharges));
        fastCharger.setValue(charge.state.fastChargerPresent ? "Yes":"No");
        chargeRate.setValue(String.format("%.1f", charge.state.chargeRate*conversionFactor));
        remaining.setValue(getDurationString(charge.state.timeToFullCharge));
        actualCurrent.setValue(String.valueOf(charge.state.chargerActualCurrent));
        chargerPower.setValue(String.valueOf(charge.state.chargerPower));
        chargingState.setValue(charge.state.chargingState.name());
        if (charge.state.chargerPhases == 3)
            actualCurrent.setName("Current \u2462");
        else
            actualCurrent.setName("Current");
    }
    
    private void reflectChargeStatus() {
        int percent = charge.state.chargeLimitSOC;
        chargeSlider.setMin((charge.state.chargeLimitSOCMin/10)*10);
        chargeSlider.setMax(100);
        chargeSlider.setMajorTickUnit(10);
        chargeSlider.setMinorTickCount(4);
        chargeSlider.setBlockIncrement(10);
        chargeSlider.setValue(percent);
        chargeSetting.setText(percent + " %");
        stdLink.setVisited(percent == charge.state.chargeLimitSOCStd);
        maxLink.setVisited(percent == charge.state.chargeLimitSOCMax);      
        // Set the labels that indicate a charge is pending
        if (charge.state.scheduledChargePending) {
            Date d = new Date(charge.state.scheduledStart*1000);
            String time = new SimpleDateFormat("hh:mm a").format(d);
            scheduledTimeLabel.setText("Charging will start at " + time);
            updatePendingChargeLabels(true);
        } else {
            updatePendingChargeLabels(false);
        }
    }
    
    private void reflectBatteryStats() {
        batteryGauge.setChargingLevel(charge.state.batteryPercent/100.0);
        switch (charge.state.chargingState) {
            case Complete:
            case Charging:
                batteryGauge.setCharging(true); break;
            default:
                batteryGauge.setCharging(false); break;
        }
        int bl = charge.state.batteryPercent;
        int ubl = charge.state.usableBatteryLevel;
        if (ubl != 0 && ubl != bl) {
            snowflake.setVisible(true);
            bl = ubl;
        } else {
            snowflake.setVisible(false);
        }
        batteryPercentLabel.setText(String.valueOf(bl));
    }

    private void reflectRange() {
        double conversionFactor = useMiles ? 1.0 : KilometersPerMile;
        estOdometer.setValue(osd(charge.state.estimatedRange * conversionFactor));
        idealOdometer.setValue(osd(charge.state.idealRange * conversionFactor));
        ratedOdometer.setValue(osd(charge.state.range * conversionFactor));
        
    }
    
    private void updatePendingChargeLabels(boolean show) {
        chargeScheduledLabel.setVisible(show);
        scheduledTimeLabel.setVisible(show);
    }
    
    /**
     * Return the given value with just one significant decimal place
     * @param value The value whose precision is to be altered
     * @return      The value with just one significant decimal place
     */
    private double osd(double value) { return Math.round(value * 10.0) / 10.0; }
    
/*------------------------------------------------------------------------------
 *
 * Private Utility Methods and Classes
 * 
 *----------------------------------------------------------------------------*/

    private String getDurationString(double hoursFloat) {
        int hours = (int)hoursFloat;
        double fractionalHour = hoursFloat - hours;
        int minutes = (int)(fractionalHour * 60);
        int seconds = (int)((fractionalHour * 60) - minutes) * 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
    

}
