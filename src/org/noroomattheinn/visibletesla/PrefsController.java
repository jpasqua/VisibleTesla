/*
 * PrefsController.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Oct 31, 2013
 */
package org.noroomattheinn.visibletesla;

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
import javafx.scene.control.Button;
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
import org.noroomattheinn.utils.PWUtils;
import org.noroomattheinn.visibletesla.fxextensions.TimeSelector;

public class PrefsController extends BaseController {
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private PWUtils pwUtils = new PWUtils();
    
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
    
    @FXML private CheckBox      submitAnon;
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
        if (code.isEmpty()) {
            code = "visible";   // Force some value!
        }
        byte[] salt = pwUtils.generateSalt();
        byte[] pw = pwUtils.getEncryptedPassword(code, salt);
        String externalForm = pwUtils.externalRep(salt, pw);
        ac.restEncPW = pw;
        ac.restSalt = salt;
        ac.prefs.authCode.set(externalForm);
    }
    
    @FXML void displayUUID(ActionEvent event) {
        AnchorPane pane = new AnchorPane();
        String body = "This value is only known to you and allows you to identify " +
            "your information amongst anonymized data at VisibleTesla.com\n" +
            "ID: " + ac.uuidForVehicle;

        TextArea t = new TextArea(body);
        pane.getChildren().add(t);
        Dialogs.showCustomDialog(
            ac.stage, pane, "Your Anonymous UUID", "General Preferences", Dialogs.DialogOptions.OK, null);
    }
    
    @FXML void showAppFiles(ActionEvent event) {
        ac.openFileViewer(ac.getAppFileFolder().getAbsolutePath());
    }
    
    @FXML void wakeOnTCHandler(ActionEvent event) {
    }
    
    @FXML void generalHandleAFF(ActionEvent event) {
        Dialogs.showInformationDialog(ac.stage, 
            "This change will take effect the next time the application is started.\n",
            "Please Note...", "General Preferences");
    }
    
    @FXML void testDelivery(ActionEvent event) {
        String msg = "Testing delivery from VisibleTesla on ";
        String addr = ac.prefs.notificationAddress.get();
        if (addr == null || addr.length() == 0) {
            Dialogs.showWarningDialog(ac.stage,
                    "You must supply an email address before testing delivery",
                    "Test Problem");
        }
        String date = String.format("%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS", new Date());
        if (!ac.utils.sendNotification(addr, msg + date)) {
            Dialogs.showWarningDialog(ac.stage,
                    "Error delivering your test message.\n" +
                    "Please check your email address.\n" +
                    "If you have changed any advanced settings,\n" +
                    "please double check them  or revert to defaults",
                    "Test Problem");
        } else {
            Dialogs.showInformationDialog(ac.stage, 
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
        bindToCheckBox(wakeOnTabChange, ac.prefs.wakeOnTabChange);
        bindToIntegerProperty(idleThresholdSlider, idleThresholdLabel,
                              ac.prefs.idleThresholdInMinutes);
        bindToComboBox(graphsTimePeriod, ac.prefs.loadPeriod);
        bindToTextField(emailForNotifications, ac.prefs.notificationAddress);
        bindToComboBox(overviewRange, ac.prefs.overviewRange);
        
        bindToCheckBox(submitAnon, ac.prefs.submitAnonData);
        bindToCheckBox(includeLoc, ac.prefs.includeLocData);
        bindToDoubleProperty(ditherAmt, null, ac.prefs.ditherLocAmt);
        
        // Advanced
        bindToCheckBox(enableProxy, ac.prefs.enableProxy);
        bindToTextField(proxyHost, ac.prefs.proxyHost);
        bindToTextField(proxyPort, ac.prefs.proxyPort);
        bindToCheckBox(offerExperimental, ac.prefs.offerExperimental);
        bindToCheckBox(useCustomGoogleAPIKey, ac.prefs.useCustomGoogleAPIKey);
        bindToTextField(customGoogleAPIKey, ac.prefs.googleAPIKey);
        bindToCheckBox(useCustomMailGunKey, ac.prefs.useCustomMailGunKey);
        bindToTextField(customMailGunKey, ac.prefs.mailGunKey);
        bindToIntegerProperty(fontScaleSlider, fontScale, ac.prefs.fontScale);
        bindToCheckBox(enableRest, ac.prefs.enableRest);
        bindToTextField(restPort, ac.prefs.restPort);
        bindToTextField(customURLSrc, ac.prefs.customURLSource);
        bindToComboBox(logLevel, ac.prefs.logLevel);

        // Overrides
        bindToComboBox(overrideWheelsCombo, ac.prefs.overideWheelsTo);
        bindToCheckBox(overrideWheelsActive, ac.prefs.overideWheelsActive);
        bindToComboBox(overrideColorCombo, ac.prefs.overideColorTo);
        bindToCheckBox(overrideColorActive, ac.prefs.overideColorActive);
        bindToComboBox(overrideUnitsCombo, ac.prefs.overideUnitsTo);
        bindToCheckBox(overrideUnitsActive, ac.prefs.overideUnitsActive);
        bindToComboBox(overrideModelCombo, ac.prefs.overideModelTo);
        bindToCheckBox(overrideModelActive, ac.prefs.overideModelActive);
        bindToComboBox(overrideRoofCombo, ac.prefs.overideRoofTo);
        bindToCheckBox(overrideRoofActive, ac.prefs.overideRoofActive);
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
        bindToCheckBox(collectLocationData, ac.prefs.collectLocationData);
        bindToCheckBox(streamWhenPossible, ac.prefs.streamWhenPossible);
        bindToIntegerProperty(locMinTime, locMinTimeDisplay, ac.prefs.locMinTime);
        bindToIntegerProperty(locMinDist, locMinDistDisplay, ac.prefs.locMinDist);
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
        bindToCheckBox(ignoreGaps, ac.prefs.ignoreGraphGaps);
        bindToIntegerProperty(gapTime, gapTimeDisplay, ac.prefs.graphGapTime);
        
        final TimeSelector vsFromTime = new TimeSelector(vsFromHour, vsFromMin, vsFromAMPM);
        final TimeSelector vsToTime = new TimeSelector(vsToHour, vsToMin, vsToAMPM);
        vsFromTime.bind(ac.prefs.vsFrom);
        vsToTime.bind(ac.prefs.vsTo);
        bindToCheckBox(limitVS, ac.prefs.vsLimitEnabled);
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
        bindToCheckBox(safeMinCharge, ac.prefs.safeIncludesMinCharge);
        bindToCheckBox(safePlugged, ac.prefs.safeIncludesPluggedIn);
    }
    
/*------------------------------------------------------------------------------
 *
 * Methods overriden from BaseController
 * 
 *----------------------------------------------------------------------------*/
    
    @Override protected void fxInitialize() { }

    @Override protected void initializeState() {
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
