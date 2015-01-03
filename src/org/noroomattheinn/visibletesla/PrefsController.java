/*
 * PrefsController.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Oct 31, 2013
 */
package org.noroomattheinn.visibletesla;

import org.noroomattheinn.visibletesla.prefs.Prefs;
import java.util.Date;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialogs;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.util.converter.NumberStringConverter;
import org.noroomattheinn.utils.Utils;
import org.noroomattheinn.fxextensions.TimeSelector;
import org.noroomattheinn.utils.MailGun;

public class PrefsController extends BaseController {
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private Prefs prefs;
    
/*------------------------------------------------------------------------------
 *
 * General Application Preferences
 * 
 *----------------------------------------------------------------------------*/

    //
    // UI Elements
    //
    @FXML private CheckBox      wakeOnTabChange;
    @FXML private Slider        idleThresholdSlider;
    @FXML private Label         idleThresholdLabel;
    @FXML private CheckBox      offerExperimental;
    @FXML private CheckBox      enableProxy;
    @FXML private TextField     proxyHost;
    @FXML private TextField     proxyPort;
    @FXML private ComboBox<String> graphsTimePeriod;
    @FXML private TextField     customGoogleAPIKey;
    @FXML private CheckBox      useCustomGoogleAPIKey;
    @FXML private TextField     customMailGunKey;
    @FXML private CheckBox      useCustomMailGunKey;
    @FXML private TextField     emailForNotifications;
    @FXML private Slider        fontScaleSlider;
    @FXML private Label         fontScale;
    @FXML private CheckBox      enableRest;
    @FXML private TextField     restPort;
    @FXML private PasswordField authCode;
    @FXML private TextField     customURLSrc;
    @FXML private ComboBox<String> overviewRange;
    @FXML private ComboBox<String> logLevel;
    
    @FXML private CheckBox      anonRest;
    @FXML private CheckBox      anonCharge;
    @FXML private CheckBox      anonFailure;
    @FXML private CheckBox      includeLoc;
    @FXML private Slider        ditherAmt;

    // Overrides
    @FXML private ComboBox<String>  overrideWheelsCombo;
    @FXML private CheckBox          overrideWheelsActive;
    @FXML private ComboBox<String>  overrideColorCombo;
    @FXML private CheckBox          overrideColorActive;
    @FXML private ComboBox<String>  overrideUnitsCombo;
    @FXML private CheckBox          overrideUnitsActive;
    @FXML private ComboBox<String>  overrideModelCombo;
    @FXML private CheckBox          overrideModelActive;
    @FXML private ComboBox<String>  overrideRoofCombo;
    @FXML private CheckBox          overrideRoofActive;

    //
    // Action Handlers
    //
    @FXML void setAuthCode(ActionEvent event) {
        String code = authCode.getText();
	String externalForm = App.get().setPW(code);
        prefs.authCode.set(externalForm);
    }
    
    @FXML void displayUUID(ActionEvent event) {
        AnchorPane pane = new AnchorPane();
        String body = "This value is only known to you and allows you to identify " +
            "your information amongst anonymized data at VisibleTesla.com\n" +
            "ID: " + vtVehicle.getVehicle().getUUID();

        TextArea t = new TextArea(body);
        pane.getChildren().add(t);
        Dialogs.showCustomDialog(
            app.stage, pane, "Your Anonymous UUID", "General Preferences", Dialogs.DialogOptions.OK, null);
    }
    
    @FXML void showAppFiles(ActionEvent event) {
        Utils.openFileViewer(app.appFileFolder().getAbsolutePath());
    }
    
    @FXML void wakeOnTCHandler(ActionEvent event) {
    }
    
    @FXML void generalHandleAFF(ActionEvent event) {
        Dialogs.showInformationDialog(app.stage, 
            "This change will take effect the next time the application is started.\n",
            "Please Note...", "General Preferences");
    }
    
    @FXML void testDelivery(ActionEvent event) {
        String msg = "Testing delivery from VisibleTesla on ";
        String addr = prefs.notificationAddress.get();
        if (addr == null || addr.length() == 0) {
            Dialogs.showWarningDialog(app.stage,
                    "You must supply an email address before testing delivery",
                    "Test Problem");
        }
        String date = String.format("%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS", new Date());
        if (!MailGun.get().send(addr, msg + date)) {
            Dialogs.showWarningDialog(app.stage,
                    "Error delivering your test message.\n" +
                    "Please check your email address.\n" +
                    "If you have changed any advanced settings,\n" +
                    "please double check them  or revert to defaults",
                    "Test Problem");
        } else {
            Dialogs.showInformationDialog(app.stage, 
                    "The test message has been sent to the specified\n" +
                    "address. If you do not receive it within 15 minutes,\n" +
                    "please check your email address and try again. After\n" +
                    "two attempts with a correct email address, please post\n" +
                    "a message in the forums.",
                    "Message Sent");
        }
    }
    
    //
    // Intialize the UI
    //
    private void initGeneralPrefsUI() {
        // Basic
        bindToCheckBox(wakeOnTabChange, prefs.wakeOnTabChange);
        bindToIntegerProperty(idleThresholdSlider, idleThresholdLabel,
                              prefs.idleThresholdInMinutes);
        bindToComboBox(graphsTimePeriod, prefs.loadPeriod);
        bindToTextField(emailForNotifications, prefs.notificationAddress);
        bindToComboBox(overviewRange, prefs.overviewRange);
        
        bindToCheckBox(anonRest, prefs.submitAnonRest);
        bindToCheckBox(anonCharge, prefs.submitAnonCharge);
        bindToCheckBox(anonFailure, prefs.submitAnonFailure);
        bindToCheckBox(includeLoc, prefs.includeLocData);
        bindToDoubleProperty(ditherAmt, null, prefs.ditherLocAmt);
        
        // Advanced
        bindToCheckBox(enableProxy, prefs.enableProxy);
        bindToTextField(proxyHost, prefs.proxyHost);
        bindToTextField(proxyPort, prefs.proxyPort);
        bindToCheckBox(offerExperimental, prefs.offerExperimental);
        bindToCheckBox(useCustomGoogleAPIKey, prefs.useCustomGoogleAPIKey);
        bindToTextField(customGoogleAPIKey, prefs.googleAPIKey);
        bindToCheckBox(useCustomMailGunKey, prefs.useCustomMailGunKey);
        bindToTextField(customMailGunKey, prefs.mailGunKey);
        bindToIntegerProperty(fontScaleSlider, fontScale, prefs.fontScale);
        bindToCheckBox(enableRest, prefs.enableRest);
        bindToTextField(restPort, prefs.restPort);
        bindToTextField(customURLSrc, prefs.customURLSource);
        bindToComboBox(logLevel, prefs.logLevel);

        // Overrides
        bindToComboBox(overrideWheelsCombo, prefs.overideWheelsTo);
        bindToCheckBox(overrideWheelsActive, prefs.overideWheelsActive);
        bindToComboBox(overrideColorCombo, prefs.overideColorTo);
        bindToCheckBox(overrideColorActive, prefs.overideColorActive);
        bindToComboBox(overrideUnitsCombo, prefs.overideUnitsTo);
        bindToCheckBox(overrideUnitsActive, prefs.overideUnitsActive);
        bindToComboBox(overrideModelCombo, prefs.overideModelTo);
        bindToCheckBox(overrideModelActive, prefs.overideModelActive);
        bindToComboBox(overrideRoofCombo, prefs.overideRoofTo);
        bindToCheckBox(overrideRoofActive, prefs.overideRoofActive);
    }
    
/*------------------------------------------------------------------------------
 *
 * Preferences related to the Location Tab
 * 
 *----------------------------------------------------------------------------*/

    //
    // UI Elements
    //
    @FXML private CheckBox collectLocationData;
    @FXML private Slider locMinTime;
    @FXML private Label locMinTimeDisplay;
    @FXML private Slider locMinDist;
    @FXML private Label locMinDistDisplay;
    @FXML private CheckBox streamWhenPossible;

    //
    // Initialize the UI
    //
    private void initLocationPrefsUI() {
        bindToCheckBox(collectLocationData, prefs.collectLocationData);
        bindToCheckBox(streamWhenPossible, prefs.streamWhenPossible);
        bindToIntegerProperty(locMinTime, locMinTimeDisplay, prefs.locMinTime);
        bindToIntegerProperty(locMinDist, locMinDistDisplay, prefs.locMinDist);
    }

/*------------------------------------------------------------------------------
 *
 * Preferences related to the Graphs Tab
 * 
 *----------------------------------------------------------------------------*/

    //
    // UI Elements
    //
    @FXML private CheckBox ignoreGaps;
    @FXML private Slider gapTime;
    @FXML private Label gapTimeDisplay;
    
    @FXML private CheckBox limitVS;
    @FXML private ComboBox<String> vsFromHour;
    @FXML private ComboBox<String> vsFromMin;
    @FXML private ComboBox<String> vsFromAMPM;
    @FXML private ComboBox<String> vsToHour;
    @FXML private ComboBox<String> vsToMin;
    @FXML private ComboBox<String> vsToAMPM;
    //
    // Initialize the UI
    //
    private void initGraphsPrefsUI() {
        bindToCheckBox(ignoreGaps, prefs.ignoreGraphGaps);
        bindToIntegerProperty(gapTime, gapTimeDisplay, prefs.graphGapTime);
        
        final TimeSelector vsFromTime = new TimeSelector(vsFromHour, vsFromMin, vsFromAMPM);
        final TimeSelector vsToTime = new TimeSelector(vsToHour, vsToMin, vsToAMPM);
        vsFromTime.bind(prefs.vsFrom);
        vsToTime.bind(prefs.vsTo);
        bindToCheckBox(limitVS, prefs.vsLimitEnabled);
        limitVS.selectedProperty().addListener(new ChangeListener<Boolean>() {
            @Override public void changed(ObservableValue ov, Boolean t, Boolean t1) {
                vsFromTime.enable(t1);
                vsToTime.enable(t1);
            }
        });
        vsFromTime.enable(limitVS.selectedProperty().get());
        vsToTime.enable(limitVS.selectedProperty().get());
    }

/*------------------------------------------------------------------------------
 *
 * Preferences related to the Scheduler Tab
 * 
 *----------------------------------------------------------------------------*/

    @FXML private CheckBox safeMinCharge;
    @FXML private CheckBox safePlugged;
    
    private void initSchedulerPrefsUI() {
        bindToCheckBox(safeMinCharge, prefs.safeIncludesMinCharge);
        bindToCheckBox(safePlugged, prefs.safeIncludesPluggedIn);
    }
    
/*------------------------------------------------------------------------------
 *
 * Methods overriden from BaseController
 * 
 *----------------------------------------------------------------------------*/
    
    @Override protected void fxInitialize() { }

    @Override protected void initializeState() {
        prefs = Prefs.get();
        initGeneralPrefsUI();
        initSchedulerPrefsUI();
        initLocationPrefsUI();
        initGraphsPrefsUI();
    }
    
    @Override protected void activateTab() { }
    @Override protected void refresh() { }

/*------------------------------------------------------------------------------
 *
 * PRIVATE - Utility Methods
 * 
 *----------------------------------------------------------------------------*/
    
    private void bindToComboBox(final ComboBox<String> cb, final StringProperty property) {
        cb.getSelectionModel().select(property.get());

        cb.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<String>() {
            @Override public void changed(ObservableValue<? extends String> ov, String old, String cur) {
                if (cur != null) {
                    property.set(cur);
                }
            }
        });
        
        property.addListener(new ChangeListener<String>() {
            @Override public void changed(ObservableValue<? extends String> ov, String old, String cur) {
                cb.getSelectionModel().select(cur);
            }
        });
    }
    
    private void bindToCheckBox(final CheckBox cb, final BooleanProperty property) {
        cb.setSelected(property.get());
        property.bindBidirectional(cb.selectedProperty());
    }
    
    private void bindToTextField(TextField tf, StringProperty property) {
        tf.setText(property.get());
        property.bindBidirectional(tf.textProperty());
    }
    
    private void bindToTextField(TextField tf, IntegerProperty property) {
        tf.setText(property.getValue().toString());
        Bindings.bindBidirectional(tf.textProperty(), property, new NumberStringConverter("####"));
    }
    
    private void bindToIntegerProperty(
            final Slider slider, final Label label, final IntegerProperty property) {
        
        // Watch for any changes to the property and update the UI appropriately
        property.addListener(new ChangeListener<Number>() {
            @Override public void changed(
                ObservableValue<? extends Number> ov, Number old, Number cur) {
                    slider.setValue(cur.intValue());
                    label.setText(String.valueOf(cur.intValue()));
            }
        });        
        slider.setValue(property.get());
        label.setText(String.valueOf(property.get()));
        
        slider.valueProperty().addListener(new ChangeListener<Number>() {
            @Override public void changed(
                ObservableValue<? extends Number> ov, Number old, Number cur) {
                    property.set(cur.intValue());
                    label.setText(String.valueOf(cur.intValue()));
            }
        });
    }
    
    private void bindToDoubleProperty(
            final Slider slider, final Label label, final DoubleProperty property) {
        
        // Watch for any changes to the property and update the UI appropriately
        property.addListener(new ChangeListener<Number>() {
            @Override public void changed(
                ObservableValue<? extends Number> ov, Number old, Number cur) {
                    slider.setValue(cur.doubleValue());
                    if (label != null) label.setText(String.valueOf(cur.doubleValue()));
            }
        });        
        slider.setValue(property.get());
        if (label != null) label.setText(String.valueOf(property.get()));
        
        slider.valueProperty().addListener(new ChangeListener<Number>() {
            @Override public void changed(
                ObservableValue<? extends Number> ov, Number old, Number cur) {
                    property.set(cur.doubleValue());
                    if (label != null) label.setText(String.valueOf(cur.doubleValue()));
            }
        });
    }
}
