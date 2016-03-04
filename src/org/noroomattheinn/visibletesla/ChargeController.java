/*
 * ChargeController.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Jul 22, 2013
 */

package org.noroomattheinn.visibletesla;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.Callable;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
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
import javafx.scene.paint.Color;
import javafx.scene.paint.Stop;
import jfxtras.labs.scene.control.gauge.Battery;
import jfxtras.labs.scene.control.gauge.Lcd;
import org.noroomattheinn.tesla.ChargeState;
import org.noroomattheinn.tesla.Result;
import org.noroomattheinn.tesla.Vehicle;
import org.noroomattheinn.utils.Utils;

public class ChargeController extends BaseController {
/*------------------------------------------------------------------------------
 *
 * Constants and Enums
 * 
 *----------------------------------------------------------------------------*/

    private static final String MinVersionForChargePct = "1.33.38";
        // This value is taken from the wiki here: http://tinyurl.com/mzwxbps
    
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private boolean useMiles;
    private boolean showTimeComplete;
    
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
    @FXML private Battery batteryGauge, usableGauge;
    @FXML private Label batteryPercentLabel;
    
    // Elements that display reminaing range
    @FXML private Lcd estOdometer;
    @FXML private Lcd idealOdometer;
    @FXML private Lcd ratedOdometer;
    @FXML private Lcd chargedOdometer;
    @FXML private Lcd chargeSelOdometer;
    @FXML private Lcd SOC;
    
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
    private final GenericProperty finishAt = new GenericProperty("Finish at", "00:00:00", "HH:MM:SS");
    private final GenericProperty actualCurrent = new GenericProperty("Current", "0.0", "Amps");
    private final GenericProperty chargerPower = new GenericProperty("Charger Power", "0.0", "kW");
    private final GenericProperty chargingState = new GenericProperty("State", "Disconnected", "");
    private final GenericProperty batteryLevel = new GenericProperty("Battery Level", "0", "%");
    
    final ObservableList<GenericProperty> data = FXCollections.observableArrayList(
            actualCurrent, voltage, chargeRate, remaining, finishAt, chargingState,
            pilotCurrent, batteryCurrent, fastCharger, chargerPower,
            nRangeCharges, batteryLevel);

    
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
        ChargeState charge = vtVehicle.chargeState.get();
        int percent = (h == stdLink) ? charge.chargeLimitSOCStd : charge.chargeLimitSOCMax;
        setChargePercent(percent);
    }

    
    @FXML void chargeButtonHandler(ActionEvent event) {
        final Button b = (Button) event.getSource();
        issueCommand(new Callable<Result>() {
            @Override public Result call() {
                Result r = vtVehicle.getVehicle().setChargeState(b == startButton);
                updateStateLater(Vehicle.StateType.Charge, 5 * 1000);
                return r;
            } }, "Start Charge");
    }
    
    private void setChargePercent(final int percent) {
        chargeSlider.setValue(percent);
        chargeSetting.setText(percent + " %");
        issueCommand(new Callable<Result>() {
            @Override public Result call() {
                Result r = vtVehicle.getVehicle().setChargePercent(percent);
                updateStateLater(Vehicle.StateType.Charge, 3 * 1000);
                return r;
            } }, "Set Charge %");
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
        
        usableGauge.setVisible(false);
        usableGauge.setLevelColors(new Stop[]{
            new Stop(0.0, Color.web("#FFC1C1")),
            new Stop(0.55, Color.web("#FFFFC1")),
            new Stop(1.0, Color.web("#C1D6B8"))
        });
        
        // Until we know that we've got the right software version, disable the slider
        chargeSlider.setDisable(true);
    }

    @Override protected void initializeState() {
        chargeSlider.setDisable(Utils.compareVersions(
            vtVehicle.vehicleState.get().version, MinVersionForChargePct) < 0);
        
        setTimeType(prefs.chargeTimeType.get());
        prefs.chargeTimeType.addListener(new ChangeListener<String>() {
            @Override public void changed(ObservableValue<? extends String> ov, String t, String t1) {
                setTimeType(t1);
            }
        });

        reflectNewState();
        
        vtVehicle.chargeState.addTracker(new Runnable() {
            @Override public void run() { if (active()) { reflectNewState(); }
            }
        });
    }
    
    @Override protected void activateTab() {
        useMiles = vtVehicle.unitType() == Utils.UnitType.Imperial;
        String units = useMiles ? "Miles" : "Km";
        estOdometer.setUnit(units);
        idealOdometer.setUnit(units);
        ratedOdometer.setUnit(units);
        chargedOdometer.setUnit(units);
        chargeSelOdometer.setUnit(units);
        SOC.setUnit("%");
        chargeRate.setUnits(useMiles ? "mph" : "km/h");
        if (vtVehicle.chargeState.get() != null) { reflectNewState(); }
    }

    @Override protected void refresh() { updateState(Vehicle.StateType.Charge); }
        
/*------------------------------------------------------------------------------
 *
 * Methods to Reflect the Status of the Charge
 * 
 *----------------------------------------------------------------------------*/
    
    private void reflectNewState() {
        reflectRange();
        reflectBatteryStats();
        reflectChargeStatus();
        reflectProperties();
        boolean connected = vtVehicle.chargeState.get().connectedToCharger();
        startButton.setDisable(!connected);
        stopButton.setDisable(!connected);
    }

    private void reflectProperties() {
        ChargeState charge = vtVehicle.chargeState.get();
        double conversionFactor = useMiles ? 1.0 : Utils.KilometersPerMile;
        int pc = charge.chargerPilotCurrent;
        if (pc == -1) pilotCurrent.setValue("Unknown");
        else pilotCurrent.setValue(String.valueOf(pc));
        voltage.setValue(String.valueOf(charge.chargerVoltage));
        batteryCurrent.setValue(String.format("%.1f", charge.batteryCurrent));
        nRangeCharges.setValue(String.valueOf(charge.maxRangeCharges));
        fastCharger.setValue(charge.fastChargerPresent ? "Yes":"No");
        chargeRate.setValue(String.format("%.1f", charge.chargeRate*conversionFactor));
        //if (showTimeComplete) {
            long msToFull = (long)(charge.timeToFullCharge * (60*60*1000));
            if (msToFull == 0 || !charge.isCharging()) {
                finishAt.setValue("");
            } else {
                Calendar when = Calendar.getInstance();
                when.setTimeInMillis(System.currentTimeMillis() + msToFull);
                finishAt.setValue(
                    String.format("%02d:%02d:%02d", when.get(Calendar.HOUR_OF_DAY),
                                                    when.get(Calendar.MINUTE),
                                                    when.get(Calendar.SECOND)));
            }
        //} else {
            remaining.setValue(charge.timeToFull());
        //}
        actualCurrent.setValue(String.valueOf(charge.chargerActualCurrent));
        chargerPower.setValue(String.valueOf(charge.chargerPower));
        chargingState.setValue(charge.chargingState.name());
        actualCurrent.setName(charge.chargerPhases == 3 ? "Current \u2462" : "Current");
        if (charge.batteryPercent != charge.usableBatteryLevel) {
            batteryLevel.setValue(String.format(
                    "%d/%d", charge.batteryPercent, charge.usableBatteryLevel));
        } else {
            batteryLevel.setValue(String.format("%d", charge.batteryPercent));
        }
               
    }
    
    private void reflectChargeStatus() {
        ChargeState charge = vtVehicle.chargeState.get();
        int percent = charge.chargeLimitSOC;
        chargeSlider.setMin((charge.chargeLimitSOCMin/10)*10);
        chargeSlider.setMax(100);
        chargeSlider.setMajorTickUnit(10);
        chargeSlider.setMinorTickCount(4);
        chargeSlider.setBlockIncrement(10);
        chargeSlider.setValue(percent);
        chargeSetting.setText(percent + " %");
        stdLink.setVisited(percent == charge.chargeLimitSOCStd);
        maxLink.setVisited(percent == charge.chargeLimitSOCMax);      
        // Set the labels that indicate a charge is pending
        if (charge.scheduledChargePending) {
            Date d = new Date(charge.scheduledStart*1000);
            String time = new SimpleDateFormat("hh:mm a").format(d);
            scheduledTimeLabel.setText("Charging will start at " + time);
            updatePendingChargeLabels(true);
        } else {
            updatePendingChargeLabels(false);
        }
    }
    
    private void reflectBatteryStats() {
        ChargeState charge = vtVehicle.chargeState.get();
        double range = charge.range;
        int bl = charge.batteryPercent;
        int ubl = charge.usableBatteryLevel;
        
        batteryGauge.setChargingLevel(bl/100.0);
        usableGauge.setChargingLevel(ubl/100.0);
        
        switch (charge.chargingState) {
            case Complete:
            case Charging:
                batteryGauge.setCharging(true);
                usableGauge.setCharging(true);
                break;
            default:
                batteryGauge.setCharging(false);
                usableGauge.setCharging(false);
                break;
        }

        if (ubl == 0) ubl = bl;
        if (bl - ubl >= 2) {
            usableGauge.setVisible(true);
            batteryPercentLabel.setTextFill(Color.BLUE);
            bl = ubl;
        } else {
            usableGauge.setVisible(false);
            batteryPercentLabel.setTextFill(Color.BLACK);
        }
        batteryPercentLabel.setText(String.valueOf(bl));
        SOC.setValue(bl);
    }

    private void reflectRange() {
        ChargeState charge = vtVehicle.chargeState.get();
        double conversionFactor = useMiles ? 1.0 : Utils.KilometersPerMile;
        estOdometer.setValue(Utils.round(charge.estimatedRange * conversionFactor, 1));
        idealOdometer.setValue(Utils.round(charge.idealRange * conversionFactor, 1));
        ratedOdometer.setValue(Utils.round(charge.range * conversionFactor, 1));
        chargedOdometer.setValue(Utils.round(charge.idealRange * conversionFactor/charge.batteryPercent*100, 1));
        chargeSelOdometer.setValue(Utils.round(charge.idealRange * conversionFactor/charge.batteryPercent*chargeSlider.getValue(), 1));
        
    }
    
    private void updatePendingChargeLabels(boolean show) {
        chargeScheduledLabel.setVisible(show);
        scheduledTimeLabel.setVisible(show);
    }
    
    private void setTimeType(String timeType) {
        switch (timeType) {
            case "Complete At":
                remaining.setName("Will Complete");
                showTimeComplete = true;
                break;
            case "Remaining":
            default:
                remaining.setName("Time Left");
                showTimeComplete = false;
                break;
        }
    }
}
