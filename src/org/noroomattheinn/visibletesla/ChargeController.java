/*
 * ChargeController.java - Copyright(c) 2013  All Rights Reserved, Joe Pasqua
 * Created: Jul 22, 2013
 */

package org.noroomattheinn.visibletesla;

import java.text.SimpleDateFormat;
import java.util.Date;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.cell.PropertyValueFactory;
import jfxtras.labs.scene.control.gauge.Battery;
import jfxtras.labs.scene.control.gauge.Lcd;
import org.noroomattheinn.tesla.APICall;
import org.noroomattheinn.tesla.ChargeState;
import org.noroomattheinn.tesla.GUIState;
import org.noroomattheinn.tesla.Result;
import org.noroomattheinn.tesla.Vehicle;
import org.noroomattheinn.utils.Utils;


public class ChargeController extends BaseController {
    private static final double KilometersPerMile = 1.60934;
    
    // Controller & State objects
    private ChargeState chargeState;
    private org.noroomattheinn.tesla.ChargeController chargeController;
    private boolean useMiles;
    
    // UI Controls
    @FXML private ToggleButton maxRangeButton, stdRangeButton;
    @FXML private Button startButton, stopButton;
    
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
    @FXML private TableView<Property> propertyTable;
    @FXML private TableColumn<Property,String> propNameColumn;
    @FXML private TableColumn<Property,String> propValColumn;
    @FXML private TableColumn<Property,String> propUnitsColumn;

    // Each Property defines a row of the table...
    private Property pilotCurrent = new Property("Pilot Current", "0.0", "Amps");
    private Property voltage = new Property("Voltage", "0.0", "Volts");
    private Property batteryCurrent = new Property("Battery Current", "0.0", "Amps");
    private Property nRangeCharges = new Property("# Range Charges", "0.0", "Count");
    private Property fastCharger = new Property("Fast Charger", "No", "");
    private Property chargeRate = new Property("Charge Rate", "0.0", "MPH");
    private Property remaining = new Property("Time Left", "00:00:00", "HH:MM:SS");
    private Property actualCurrent = new Property("Current", "0.0", "Amps");
    private Property chargerPower = new Property("Charger Power", "0.0", "kW");
    private Property chargingState = new Property("State", "Disconnected", "");
    
    final ObservableList<Property> data = FXCollections.observableArrayList(
            actualCurrent, voltage, chargeRate, remaining, chargingState,
            pilotCurrent, batteryCurrent, fastCharger, chargerPower,
            nRangeCharges);

    
    // Controller-specific initialization
    protected void doInitialize() {
        // Prepare the property table
        propNameColumn.setCellValueFactory( new PropertyValueFactory<Property,String>("name") );
        propValColumn.setCellValueFactory( new PropertyValueFactory<Property,String>("value") );
        propUnitsColumn.setCellValueFactory( new PropertyValueFactory<Property,String>("units") );
        propertyTable.setItems(data);
        showPendingChargeLabels(false);
    }

    @FXML void chargeButtonHandler(ActionEvent event) {
        final Button b = (Button)event.getSource();
        issueCommand(new Callback() {
            public Result execute() { return chargeController.setChargeState(b == startButton); } },
            true);
    }
    
    @FXML void rangeHandler(ActionEvent event) {
        ToggleButton source = (ToggleButton)event.getSource();
        final boolean max = (source == maxRangeButton);
        issueCommand(
            new Callback() { public Result execute() { return chargeController.setChargeRange(max); } },
            true);
    }

    protected void prepForVehicle(Vehicle v) {
        if (chargeController == null || v != vehicle) {
            chargeController = new org.noroomattheinn.tesla.ChargeController(v);
        }
        
        GUIState gs = vehicle.getCachedGUIOptions();    // TO DO: If (gs == null) ...
        useMiles = gs.distanceUnits().equalsIgnoreCase("mi/hr");
        if (simulatedUnitType != null)
            useMiles = (simulatedUnitType == Utils.UnitType.Imperial);
        String units = useMiles ? "Miles" : "Km";
        estOdometer.setUnit(units);
        idealOdometer.setUnit(units);
        ratedOdometer.setUnit(units);

        chargeRate.setUnits(useMiles ? "mph" : "kph");
    }

    protected APICall getRefreshableState() { return new ChargeState(vehicle); }
    
    protected void reflectNewState(Object newState) {
        chargeState = Utils.cast(newState);
        if (chargeState == null) return;
            // We shouldn't get here if the state is null, but be careful anyway

        reflectRange();
        reflectBatteryStats();
        reflectChargeStatus();
        reflectProperties();
    }
    
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
    
    private String getDurationString(double hoursFloat) {
        int hours = (int)hoursFloat;
        double fractionalHour = hoursFloat - hours;
        int minutes = (int)(fractionalHour * 60);
        int seconds = (int)((fractionalHour * 60) - minutes) * 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
    
    //
    // Utility Methods for updating elements of the UI
    //
    private void showPendingChargeLabels(boolean show) {
            chargeScheduledLabel.setVisible(show);
            scheduledTimeLabel.setVisible(show);
    }
    
    private void reflectChargeStatus() {
        if (chargeState.chargingState() == ChargeState.State.Charging) {
            startButton.setVisible(false);
            stopButton.setVisible(true);
        } else {
            startButton.setVisible(true);
            stopButton.setVisible(false);
        }
        
        if (chargeState.chargeToMaxRange())
            maxRangeButton.setSelected(true);
        else
            stdRangeButton.setSelected(true);
        
        // Set the labels that indicate a charge is pending
        if (chargeState.scheduledChargePending()) {
            Date d = new Date(chargeState.scheduledStart()*1000);
            String time = new SimpleDateFormat("hh:mm a").format(d);
            scheduledTimeLabel.setText("Charging will start at " + time);
            showPendingChargeLabels(true);
        } else {
            showPendingChargeLabels(false);
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
    
    public static class Property {
        private final SimpleStringProperty name;
        private final SimpleStringProperty value;
        private final SimpleStringProperty units;

        private Property(String name, String value, String units) {
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

    
    //
    // Handle Simulated Values
    //
        private Utils.UnitType simulatedUnitType = null;
    void setSimulatedUnits(Utils.UnitType t) { simulatedUnitType = t; }
}
