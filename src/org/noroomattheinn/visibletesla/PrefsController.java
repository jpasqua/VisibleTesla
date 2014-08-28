/*
 * PrefsController.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Oct 31, 2013
 */
package org.noroomattheinn.visibletesla;

import java.util.Date;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
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
import javafx.scene.control.TextField;
import javafx.util.converter.NumberStringConverter;
import org.noroomattheinn.utils.PWUtils;

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
    @FXML private Button        setAuthCodeButton;
    @FXML private TextField     customURLSrc;
    @FXML private ComboBox<String> overviewRange;

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
        appContext.restEncPW = pw;
        appContext.restSalt = salt;
        appContext.prefs.authCode.set(externalForm);
    }
    
    @FXML void showAppFiles(ActionEvent event) {
        appContext.openFileViewer(appContext.getAppFileFolder().getAbsolutePath());
    }
    
    @FXML void wakeOnTCHandler(ActionEvent event) {
    }
    
    @FXML void generalHandleAFF(ActionEvent event) {
        Dialogs.showInformationDialog(appContext.stage, 
            "This change will take effect the next time the application is started.\n",
            "Please Note...", "General Preferences");
    }
    
    @FXML void testDelivery(ActionEvent event) {
        String msg = "Testing delivery from VisibleTesla on ";
        String addr = appContext.prefs.notificationAddress.get();
        if (addr == null || addr.length() == 0) {
            Dialogs.showWarningDialog(appContext.stage,
                    "You must supply an email address before testing delivery",
                    "Test Problem");
        }
        String date = String.format("%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS", new Date());
        if (!appContext.utils.sendNotification(addr, msg + date)) {
            Dialogs.showWarningDialog(appContext.stage,
                    "Error delivering your test message.\n" +
                    "Please check your email address.\n" +
                    "If you have changed any advanced settings,\n" +
                    "please double check them  or revert to defaults",
                    "Test Problem");
        } else {
            Dialogs.showInformationDialog(appContext.stage, 
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
        bindToCheckBox(wakeOnTabChange, appContext.prefs.wakeOnTabChange);
        bindToIntegerProperty(idleThresholdSlider, idleThresholdLabel,
                              appContext.prefs.idleThresholdInMinutes);
        bindToComboBox(graphsTimePeriod, appContext.prefs.loadPeriod);
        bindToTextField(emailForNotifications, appContext.prefs.notificationAddress);
        bindToComboBox(overviewRange, appContext.prefs.overviewRange);

        // Advanced
        bindToCheckBox(enableProxy, appContext.prefs.enableProxy);
        bindToTextField(proxyHost, appContext.prefs.proxyHost);
        bindToTextField(proxyPort, appContext.prefs.proxyPort);
        bindToCheckBox(offerExperimental, appContext.prefs.offerExperimental);
        bindToCheckBox(useCustomGoogleAPIKey, appContext.prefs.useCustomGoogleAPIKey);
        bindToTextField(customGoogleAPIKey, appContext.prefs.googleAPIKey);
        bindToCheckBox(useCustomMailGunKey, appContext.prefs.useCustomMailGunKey);
        bindToTextField(customMailGunKey, appContext.prefs.mailGunKey);
        bindToIntegerProperty(fontScaleSlider, fontScale, appContext.prefs.fontScale);
        bindToCheckBox(enableRest, appContext.prefs.enableRest);
        bindToTextField(restPort, appContext.prefs.restPort);
        bindToTextField(customURLSrc, appContext.prefs.customURLSource);
        
        // Overrides
        bindToComboBox(overrideWheelsCombo, appContext.prefs.overideWheelsTo);
        bindToCheckBox(overrideWheelsActive, appContext.prefs.overideWheelsActive);
        bindToComboBox(overrideColorCombo, appContext.prefs.overideColorTo);
        bindToCheckBox(overrideColorActive, appContext.prefs.overideColorActive);
        bindToComboBox(overrideUnitsCombo, appContext.prefs.overideUnitsTo);
        bindToCheckBox(overrideUnitsActive, appContext.prefs.overideUnitsActive);
        bindToComboBox(overrideModelCombo, appContext.prefs.overideModelTo);
        bindToCheckBox(overrideModelActive, appContext.prefs.overideModelActive);
        bindToComboBox(overrideRoofCombo, appContext.prefs.overideRoofTo);
        bindToCheckBox(overrideRoofActive, appContext.prefs.overideRoofActive);
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
        bindToCheckBox(collectLocationData, appContext.prefs.collectLocationData);
        bindToCheckBox(streamWhenPossible, appContext.prefs.streamWhenPossible);
        bindToIntegerProperty(locMinTime, locMinTimeDisplay, appContext.prefs.locMinTime);
        bindToIntegerProperty(locMinDist, locMinDistDisplay, appContext.prefs.locMinDist);
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

    //
    // Initialize the UI
    //
    private void initGraphsPrefsUI() {
        bindToCheckBox(ignoreGaps, appContext.prefs.ignoreGraphGaps);
        bindToIntegerProperty(gapTime, gapTimeDisplay, appContext.prefs.graphGapTime);
    }

/*------------------------------------------------------------------------------
 *
 * Preferences related to the Scheduler Tab
 * 
 *----------------------------------------------------------------------------*/

    @FXML private CheckBox safeMinCharge;
    @FXML private CheckBox safePlugged;
    
    private void initSchedulerPrefsUI() {
        bindToCheckBox(safeMinCharge, appContext.prefs.safeIncludesMinCharge);
        bindToCheckBox(safePlugged, appContext.prefs.safeIncludesPluggedIn);
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
}
