/*
 * PrefsController.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Oct 31, 2013
 */
package org.noroomattheinn.visibletesla;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialogs;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Slider;
import org.noroomattheinn.tesla.Vehicle;
import org.noroomattheinn.visibletesla.GraphController.LoadPeriod;

public class PrefsController extends BaseController {

/*------------------------------------------------------------------------------
 *
 * General Application Preferences
 * 
 *----------------------------------------------------------------------------*/

    @FXML private RadioButton generalAFFApp;
    @FXML private RadioButton generalAFFUserDir;   
    
    @FXML void showUserDirectory(ActionEvent event) {
        Dialogs.showInformationDialog(appContext.stage, 
            appContext.getAppFileFolder().getAbsolutePath(),
            "User Directory", "General Preferences");
    }
    
    @FXML void generalHandleAFF(ActionEvent event) {
        RadioButton rb = (RadioButton)event.getSource();
        boolean storeFilesWithApp = (rb == generalAFFApp);
        appContext.prefs.putBoolean(AppContext.AppFilesFolderKey, storeFilesWithApp);
        Dialogs.showInformationDialog(appContext.stage, 
            "This change will take effect the next time the application is started.\n",
            "Please Note...", "General Preferences");
    }
    
    private void loadGeneralPrefs() {
        boolean storeFilesWithApp =
                appContext.prefs.getBoolean(AppContext.AppFilesFolderKey, false);
        if (storeFilesWithApp) generalAFFApp.setSelected(true);
        else generalAFFUserDir.setSelected(true);
        
        // Write back the result in case we were reading a default value...
        appContext.prefs.putBoolean(AppContext.AppFilesFolderKey, false);
    }
    
/*------------------------------------------------------------------------------
 *
 * Preferences related to the Graphs Tab
 * 
 *----------------------------------------------------------------------------*/

    @FXML private ComboBox<String> graphsTimePeriod;
    @FXML private CheckBox incrementalLoad;
    
    @FXML void graphIncremental(ActionEvent event) {
        appContext.prefs.putBoolean(
            prefKey(GraphController.GraphIncLoadPrefKey), incrementalLoad.isSelected());
    }
    
    @FXML void graphSetTimePeriod(ActionEvent event) {
        Object item = ((ComboBox)event.getSource()).getSelectionModel().getSelectedItem();
        LoadPeriod period = GraphController.nameToLoadPeriod.get((String)item);
        appContext.prefs.put(prefKey(GraphController.GraphPeriodPrefKey), period.name());
    }

    private void loadGraphPrefs() {
        String periodName = appContext.prefs.get(
                prefKey(GraphController.GraphPeriodPrefKey), LoadPeriod.All.name());
        String choice = GraphController.nameToLoadPeriod.inverse().get(LoadPeriod.valueOf(periodName));
        graphsTimePeriod.getSelectionModel().select(choice);
        
        incrementalLoad.setSelected(appContext.prefs.getBoolean(
                prefKey(GraphController.GraphIncLoadPrefKey), true));
    }
    
/*------------------------------------------------------------------------------
 *
 * Preferences related to the Scheduler Tab
 * 
 *----------------------------------------------------------------------------*/

    @FXML private Slider minChargeVal;
    @FXML private Label  minChargeDisplay;
    
    private void loadSchedulerPrefs() {
        final String key = prefKey(SchedulerController.SchedMinChargeKey);
        
        minChargeVal.valueProperty().addListener(new ChangeListener<Number>() {
            @Override public void changed(
                ObservableValue<? extends Number> ov, Number old, Number newVal) {
                    minChargeDisplay.setText(String.valueOf(newVal.intValue()));
                    appContext.prefs.putInt(key, newVal.intValue());
            }
        });
        
        int val = appContext.prefs.getInt(key, 50);
        if (val < 50 || val > 100) {
            val = 50;
            appContext.prefs.putInt(key, val);
        }
        minChargeVal.setValue(val);
    }
    
/*------------------------------------------------------------------------------
 *
 * Methods overridden from BaseController
 * 
 *----------------------------------------------------------------------------*/
    
    @Override protected void fxInitialize() { }

    @Override protected void prepForVehicle(Vehicle v) {
        loadGeneralPrefs();
        loadGraphPrefs();
        loadSchedulerPrefs();
    }

    @Override protected void refresh() { }

    @Override protected void reflectNewState() { }
    
}
