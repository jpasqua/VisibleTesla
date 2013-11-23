/*
 * PrefsController.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Oct 31, 2013
 */
package org.noroomattheinn.visibletesla;

import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
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
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.util.converter.NumberStringConverter;
import org.noroomattheinn.tesla.Vehicle;

public class PrefsController extends BaseController {
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    private boolean loaded = false;
    
/*------------------------------------------------------------------------------
 *
 * General Application Preferences
 * 
 *----------------------------------------------------------------------------*/

    //
    // UI Elements
    //
    @FXML private CheckBox      storeFilesWithApp;
    @FXML private CheckBox      wakeOnTabChange;
    @FXML private Slider        idleThresholdSlider;
    @FXML private Label         idleThresholdLabel;
    @FXML private CheckBox      offerExperimental;
    @FXML private CheckBox      enableProxy;
    @FXML private TextField     proxyHost;
    @FXML private TextField     proxyPort;
    
    
    
    //
    // Action Handlers
    //
    @FXML void showUserDirectory(ActionEvent event) {
        Dialogs.showInformationDialog(appContext.stage, 
            appContext.getAppFileFolder().getAbsolutePath(),
            "User Directory", "General Preferences");
    }
    
    @FXML void wakeOnTCHandler(ActionEvent event) {
    }
    
    @FXML void generalHandleAFF(ActionEvent event) {
        Dialogs.showInformationDialog(appContext.stage, 
            "This change will take effect the next time the application is started.\n",
            "Please Note...", "General Preferences");
    }
    
    //
    // Intialize the UI
    //
    private void initGeneralPrefsUI() {
        // Basic
        bindToCheckBox(wakeOnTabChange, appContext.thePrefs.wakeOnTabChange);
        bindToCheckBox(offerExperimental, appContext.thePrefs.offerExperimental);
        bindToIntegerProperty(idleThresholdSlider, idleThresholdLabel,
                              appContext.thePrefs.idleThresholdInMinutes);
        
        // Advanced
        bindToCheckBox(storeFilesWithApp, appContext.thePrefs.storeFilesWithApp);
        bindToCheckBox(enableProxy, appContext.thePrefs.enableProxy);
        bindToTextField(proxyHost, appContext.thePrefs.proxyHost);
        bindToTextField(proxyPort, appContext.thePrefs.proxyPort);
    }
    
/*------------------------------------------------------------------------------
 *
 * Preferences related to the Graphs Tab
 * 
 *----------------------------------------------------------------------------*/

    //
    // UI Elements
    //
    @FXML private ComboBox<String> graphsTimePeriod;
    @FXML private CheckBox incrementalLoad;
    
    //
    // Action Handlers
    //
    @FXML void graphIncremental(ActionEvent event) { }
    @FXML void graphSetTimePeriod(ActionEvent event) { }

    //
    // Initialize the UI
    //
    private void initGraphPrefsUI() {
        bindToComboBox(graphsTimePeriod, appContext.thePrefs.loadPeriod);
        bindToCheckBox(incrementalLoad, appContext.thePrefs.incrementalLoad);
    }
    
/*------------------------------------------------------------------------------
 *
 * Preferences related to the Scheduler Tab
 * 
 *----------------------------------------------------------------------------*/

    @FXML private Slider minChargeVal;
    @FXML private Label  minChargeDisplay;
    @FXML private CheckBox safeMinCharge;
    @FXML private CheckBox safePlugged;
    
    private void initSchedulerPrefsUI() {
        bindToIntegerProperty(minChargeVal, minChargeDisplay, appContext.thePrefs.lowChargeValue);
        bindToCheckBox(safeMinCharge, appContext.thePrefs.safeIncludesMinCharge);
        bindToCheckBox(safePlugged, appContext.thePrefs.safeIncludesPluggedIn);
    }
    
/*------------------------------------------------------------------------------
 *
 * Methods overriden from BaseController
 * 
 *----------------------------------------------------------------------------*/
    
    @Override protected void fxInitialize() { }

    @Override protected void prepForVehicle(Vehicle v) {
        if (!loaded) {
            initGeneralPrefsUI();
            initGraphPrefsUI();
            initSchedulerPrefsUI();
            loaded = true;
        }
    }

    @Override protected void refresh() { }

    @Override protected void reflectNewState() { }

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
        
        // Watch for any changes tot he property and update the UI appropriately
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
