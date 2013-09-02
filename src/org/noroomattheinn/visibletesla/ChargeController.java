/*
 * ChargeController.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Jul 22, 2013
 */

package org.noroomattheinn.visibletesla;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.Callable;
import javafx.beans.property.SimpleStringProperty;
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
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private ChargeState chargeState;
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
    private GenericProperty pilotCurrent = new GenericProperty("Pilot Current", "0.0", "Amps");
    private GenericProperty voltage = new GenericProperty("Voltage", "0.0", "Volts");
    private GenericProperty batteryCurrent = new GenericProperty("Battery Current", "0.0", "Amps");
    private GenericProperty nRangeCharges = new GenericProperty("# Range Charges", "0.0", "Count");
    private GenericProperty fastCharger = new GenericProperty("Supercharger", "No", "");
    private GenericProperty chargeRate = new GenericProperty("Charge Rate", "0.0", "MPH");
    private GenericProperty remaining = new GenericProperty("Time Left", "00:00:00", "HH:MM:SS");
    private GenericProperty actualCurrent = new GenericProperty("Current", "0.0", "Amps");
    private GenericProperty chargerPower = new GenericProperty("Charger Power", "0.0", "kW");
    private GenericProperty chargingState = new GenericProperty("State", "Disconnected", "");
    
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
                chargeState.chargeLimitSOCStd() : chargeState.chargeLimitSOCMax();
        setChargePercent(percent);
    }

    
    @FXML void chargeButtonHandler(ActionEvent event) {
        final Button b = (Button)event.getSource();
        issueCommand(new Callable<Result>() {
            public Result call() { return chargeController.setChargeState(b == startButton); } },
            AfterCommand.Refresh);
    }
    
    private void setChargePercent(final int percent) {
        chargeSlider.setValue(percent);
        chargeSetting.setText(percent + " %");
        issueCommand(new Callable<Result>() {
            public Result call() {
                return chargeController.setChargePercent(percent); } },
            AfterCommand.Refresh);
    }
    
    
/*------------------------------------------------------------------------------
 *
 * Methods overridden from BaseController
 * 
 *----------------------------------------------------------------------------*/

    protected void fxInitialize() {
        // Prepare the property table
        propNameColumn.setCellValueFactory( new PropertyValueFactory<GenericProperty,String>("name") );
        propValColumn.setCellValueFactory( new PropertyValueFactory<GenericProperty,String>("value") );
        propUnitsColumn.setCellValueFactory( new PropertyValueFactory<GenericProperty,String>("units") );
        propertyTable.setItems(data);
        updatePendingChargeLabels(false);
        
        // Until we know that we've got the right software version, disable the slider
        chargeSlider.setDisable(true);
    }

    protected void prepForVehicle(Vehicle v) {
        if (differentVehicle(chargeController, v)) {
            chargeController = new org.noroomattheinn.tesla.ChargeController(v);
            chargeState = new ChargeState(v);
        }
        
        GUIState gs = v.cachedGUIState();
        useMiles = gs.distanceUnits().equalsIgnoreCase("mi/hr");
        if (appContext.simulatedUnits.get() != null)
            useMiles = (appContext.simulatedUnits.get() == Utils.UnitType.Imperial);
        String units = useMiles ? "Miles" : "Km";
        estOdometer.setUnit(units);
        idealOdometer.setUnit(units);
        ratedOdometer.setUnit(units);

        chargeRate.setUnits(useMiles ? "mph" : "kph");
    }

    protected void refresh() {  updateState(chargeState); }
    
    protected void reflectNewState() {
        if (!chargeState.hasValidData()) return; // No Data Yet...
        
        reflectRange();
        reflectBatteryStats();
        reflectChargeStatus();
        reflectProperties();
        chargeSlider.setDisable(Utils.compareVersions(
            vehicle.cachedVehicleState().version(), MinVersionForChargePct) < 0);
        boolean isConnectedToPower = (chargeState.chargerPilotCurrent() > 0);
        startButton.setDisable(!isConnectedToPower);
        stopButton.setDisable(!isConnectedToPower);
    }
    
/*------------------------------------------------------------------------------
 *
 * Methods to Reflect the State of the Charge
 * 
 *----------------------------------------------------------------------------*/
    
    private void reflectProperties() {
        double conversionFactor = useMiles ? 1.0 : KilometersPerMile;
        pilotCurrent.setValue(String.valueOf(chargeState.chargerPilotCurrent()));
        voltage.setValue(String.valueOf(chargeState.chargerVoltage()));
        batteryCurrent.setValue(String.valueOf(chargeState.batteryCurrent()));
        nRangeCharges.setValue(String.valueOf(chargeState.maxRangeCharges()));
        fastCharger.setValue(chargeState.fastChargerPresent() ? "Yes":"No");
        chargeRate.setValue(String.valueOf(chargeState.chargeRate()*conversionFactor));
        remaining.setValue(getDurationString(chargeState.timeToFullCharge()));
        actualCurrent.setValue(String.valueOf(chargeState.chargerActualCurrent()));
        chargerPower.setValue(String.valueOf(chargeState.chargerPower()));
        chargingState.setValue(chargeState.chargingState().name());
    }
    
    private void reflectChargeStatus() {
        int percent = chargeState.chargeLimitSOC();
        chargeSlider.setMin((chargeState.chargeLimitSOCMin()/10)*10);
        chargeSlider.setMax(100);
        chargeSlider.setMajorTickUnit(10);
        chargeSlider.setMinorTickCount(4);
        chargeSlider.setBlockIncrement(10);
        chargeSlider.setValue(percent);
        chargeSetting.setText(percent + " %");
        stdLink.setVisited(percent == chargeState.chargeLimitSOCStd());
        maxLink.setVisited(percent == chargeState.chargeLimitSOCMax());
        
        // Set the labels that indicate a charge is pending
        if (chargeState.scheduledChargePending()) {
            Date d = new Date(chargeState.scheduledStart()*1000);
            String time = new SimpleDateFormat("hh:mm a").format(d);
            scheduledTimeLabel.setText("Charging will start at " + time);
            updatePendingChargeLabels(true);
        } else {
            updatePendingChargeLabels(false);
        }
    }
    
    private void reflectBatteryStats() {
        batteryGauge.setChargingLevel(chargeState.batteryPercent()/100.0);
        switch (chargeState.chargingState()) {
            case Complete:
            case Charging:
                batteryGauge.setCharging(true); break;
            case Disconnected:
            case Unknown:
                batteryGauge.setCharging(false); break;
        }
        batteryPercentLabel.setText(String.valueOf(chargeState.batteryPercent()));
    }

    private void reflectRange() {
        double conversionFactor = useMiles ? 1.0 : KilometersPerMile;
        estOdometer.setValue(chargeState.estimatedRange() * conversionFactor);
        idealOdometer.setValue(chargeState.idealRange() * conversionFactor);
        ratedOdometer.setValue(chargeState.range() * conversionFactor);
    }
    
    private void updatePendingChargeLabels(boolean show) {
        chargeScheduledLabel.setVisible(show);
        scheduledTimeLabel.setVisible(show);
    }
    
    
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
    
    public static class GenericProperty {
        private final SimpleStringProperty name;
        private final SimpleStringProperty value;
        private final SimpleStringProperty units;

        private GenericProperty(String name, String value, String units) {
            this.name = new SimpleStringProperty(name);
            this.value = new SimpleStringProperty(value);
            this.units = new SimpleStringProperty(units);
        }

        public SimpleStringProperty nameProperty() { return name; }
        public SimpleStringProperty valueProperty() { return value; }
        public SimpleStringProperty unitsProperty() { return units; }

        public String getName() { return name.get(); }
        public void setName(String newName) { name.set(newName); }

        public String getValue() { return value.get(); }
        public void setValue(String newValue) { value.set(newValue); }

        public String getUnits() { return units.get(); }
        public void setUnits(String newUnits) { units.set(newUnits); }

    }

}
