/*
 * HVACController.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Jul 22, 2013
 */

package org.noroomattheinn.visibletesla;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.net.URL;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.chart.XYChart.Data;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialogs;
import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;
import javafx.stage.FileChooser;
import org.noroomattheinn.tesla.ChargeState;
import org.noroomattheinn.tesla.GUIState;
import org.noroomattheinn.tesla.SnapshotState;
import org.noroomattheinn.tesla.Vehicle;
import org.noroomattheinn.utils.Utils;
import org.noroomattheinn.visibletesla.chart.TimeBasedChart;
import org.noroomattheinn.visibletesla.chart.Variable;
import org.noroomattheinn.visibletesla.chart.VariableSet;

// TO DO:
//

// To add a new variable:
// 1. If the variable requires a new state object:
//    1.1 Add the declaration of the object
//    1.2 Initialize the object in prepForVehicle
//    1.3 In getAndRecordStats: refresh the object and addElement on each variable
// 2. Add the corresponding checkbox
//    2.1 Add a decalration for the checkbox and compile this source
//    2.2 Open GraphUI.fxml and add a checkbox to the dropdown list
// 3. Register the new variable in prepVariables()

public class GraphController extends BaseController implements StatsRepository.Recorder {
    
/*------------------------------------------------------------------------------
 *
 * Constants and Enums
 * 
 *----------------------------------------------------------------------------*/

    private static final long DefaultInterval = 2 * 60 * 1000;  // 2 Minutes
    private static final long MinInterval     =     20 * 1000;  // 20 Seconds
    private static final long MaxInterval     = 5 * 60 * 1000;  // 5 Minutes
    
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private ChargeState chargeState;
    private SnapshotState snapshotState;
    private StatsRepository repo;
    private VariableSet variables;
    private long lastSnapshotTime = 0;

/*------------------------------------------------------------------------------
 *
 * UI Elements
 * 
 *----------------------------------------------------------------------------*/

    @FXML private Label readout;
    @FXML private CheckBox voltageCheckbox;
    @FXML private CheckBox currentCheckbox;
    @FXML private CheckBox rangeCheckbox;
    @FXML private CheckBox socCheckbox;
    @FXML private CheckBox rocCheckbox;
    @FXML private CheckBox powerCheckbox;
    @FXML private CheckBox batteryCurrentCheckbox;
    @FXML private CheckBox speedCheckbox;
    @FXML private AnchorPane itemListContent;
    @FXML private Button showItemsButton;
    @FXML private AnchorPane arrow;
          private TimeBasedChart chart;
          
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
          
    void exportCSV() {
        if (variables == null) {
            Dialogs.showWarningDialog(
                    appContext.stage,
                    "Your graph data hasn't been loaded yet.\n" +
                    "Please select the Graphs tab then try again");
            return;
        }
        
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Graph Data");
        fileChooser.setInitialDirectory(new File(System.getProperty("user.home")));

        final File file = fileChooser.showSaveDialog(appContext.stage);
        if (file == null)
            return;
        
        try {
            final PrintStream csvWriter = new PrintStream(new FileOutputStream(file));

            csvWriter.println("timestamp,\ttype,\tvalue");

            for (Variable v : variables.set()) {
                for (Data<Number, Number> data : v.seriesData) {
                    csvWriter.format("%d,\t%s,\t%3.1f\n", data.getXValue(), v.type, data.getYValue());
                }
            }

            csvWriter.close();
            Dialogs.showInformationDialog(
                    appContext.stage, "Your data has been exported");
        } catch (FileNotFoundException ex) {
            Dialogs.showErrorDialog(
                    appContext.stage, "Unable to save to: " + file);
        }
        
    }
          
/*------------------------------------------------------------------------------
 *
 * This section implements UI Actionhandlers
 * 
 *----------------------------------------------------------------------------*/
    
    private void showItemList(boolean visible) {
        itemListContent.setVisible(visible);
        itemListContent.setMouseTransparent(!visible);
        arrow.setStyle(visible ? "-fx-rotate: 0;" : "-fx-rotate: -90;");
    }
    
    @FXML void nowHandler(ActionEvent event) {
        chart.centerTime(System.currentTimeMillis());
        
    }
    
    @FXML void showItemsHandler(ActionEvent event) {
        boolean isVisible = itemListContent.isVisible();
        showItemList(!isVisible);   // Flip whether it's visible
    }
    
    @FXML void optionCheckboxHandler(ActionEvent event) {
        CheckBox cb = (CheckBox)event.getSource();
        Variable var = variables.getByKey(cb);
        var.visible = cb.isSelected();
        variables.assignToChart(chart.getChart());
        // Remember the value for next time we start up
        appContext.prefs.putBoolean(prefKey(var.type), var.visible);
    }    
    
/*------------------------------------------------------------------------------
 *
 * Variable Handling
 * 
 *----------------------------------------------------------------------------*/
    
    private void prepVariables() {
        GUIState gs = vehicle.cachedGUIState();
        Variable.Transform distTransform = gs.distanceUnits().startsWith("mi") ?
                Variable.idTransform : Variable.mToKTransform;
        variables.clear();
        variables.register(new Variable(voltageCheckbox, "C_VLT", "violet", Variable.idTransform));
        variables.register(new Variable(currentCheckbox, "C_AMP", "aqua", Variable.idTransform));
        variables.register(new Variable(rangeCheckbox, "C_EST", "red", distTransform));
        variables.register(new Variable(socCheckbox, "C_SOC", "salmon", Variable.idTransform));
        variables.register(new Variable(rocCheckbox, "C_ROC", "blue", distTransform));
        variables.register(new Variable(powerCheckbox, "S_PWR", "gray", Variable.idTransform));
        variables.register(new Variable(speedCheckbox, "S_SPD", "green", distTransform));
        variables.register(new Variable(batteryCurrentCheckbox, "C_BAM", "black", Variable.idTransform));
    }
    
    private void restoreLastSettings() {
        // Restore the last settings of the checkboxes
        for (Variable var : variables.set()) {
            boolean selected = appContext.prefs.getBoolean(prefKey(var.type), true);
            var.cb.setSelected((var.visible = selected));
        }
        variables.assignToChart(chart.getChart());
    }
    
/*------------------------------------------------------------------------------
 *
 * Methods overridden from BaseController
 * 
 *----------------------------------------------------------------------------*/
    
    protected void prepForVehicle(Vehicle v) {
        if (differentVehicle(chargeState, v)) {
            chargeState = new ChargeState(v);
            snapshotState = new SnapshotState(v);
            variables = new VariableSet();
            
            pauseRefresh();
            prepVariables();
            if (repo != null) repo.close();
            repo = new StatsRepository(v.getVIN());
            repo.loadExistingData(this);
            variables.assignToChart(chart.getChart());
            restoreLastSettings();
            ensureRefreshThread();  // If thread already exists, it unpauses
        }
    }

    protected void refresh() { }

    protected void reflectNewState() { }

    protected void fxInitialize() {
        refreshButton.setDisable(true);
        refreshButton.setVisible(false);
        progressIndicator.setVisible(false);
        progressLabel.setVisible(false);
        chart = new TimeBasedChart(root, readout);        
        showItemList(false);
    }
    
    protected void appInitialize() {
        // This is a hack!! For some reason this is the only way I can get styles
        // to work for ToolTips. I should be able to just choose the appropriate
        // css class decalratively, but that doesn't work and no one seems to
        // know why. This is a workaround.
        URL url = getClass().getClassLoader().getResource("org/noroomattheinn/styles/tooltip.css");
        appContext.stage.getScene().getStylesheets().add(url.toExternalForm());
    }
    
/*------------------------------------------------------------------------------
 *
 * Utility Methods for storing stats in memory and on disk
 * 
 *----------------------------------------------------------------------------*/
    
    
    private void addElement(Variable variable, long time, double value) {
        if (Double.isNaN(value) || Double.isInfinite(value))
            value = 0;
        // Round to 1 decimal place. The rest isn't really significant
        value = Math.round(value * 10.0)/10.0;
        recordElement(variable, time, value);
        repo.storeElement(variable.type, time, value);
    }
    
    // Implement StatsRepository.Recorder - Called when old stats are read in
    // by the StatsRepository. All it does is record the data in memory.
    @Override public void recordElement(long time, String type, double val) {
        recordElement(variables.get(type), time, val);
    }

    private void recordElement(final Variable variable, final long time, final double value) {
        // This can be called from a background thread. If we update the chart's
        // series data directly, that could cause a UI refresh which would
        // be happening from a non-UI thread. This can, and has, resulted
        // in a concurrentModificationException.
        // Bottom Line: Do it later on the app thread.
        //Platform.runLater(new ElementRecorder(variable, time, value));
        Platform.runLater(new Runnable() {
            @Override public void run() {
                variable.addToSeries(time, value);
            }
        });
    }
    
    // Called by the background thread to gather and record a new set of samples
    private double getAndRecordStats() {
        chargeState.refresh();
        snapshotState.refresh();
        long time = System.currentTimeMillis();
        if (chargeState.hasValidData()) {
            addElement(variables.get("C_VLT"), time, chargeState.chargerVoltage());
            addElement(variables.get("C_AMP"), time, chargeState.chargerActualCurrent());
            addElement(variables.get("C_EST"), time, chargeState.range());
            addElement(variables.get("C_SOC"), time, chargeState.batteryPercent());
            addElement(variables.get("C_ROC"), time, chargeState.chargeRate());
            addElement(variables.get("C_BAM"), time, chargeState.batteryCurrent());
        }
        if (snapshotState.hasValidData()) {
            long thisSnapshotTime = snapshotState.timestamp().getTime();
            if (thisSnapshotTime != lastSnapshotTime) {
                addElement(variables.get("S_PWR"), time, snapshotState.power());
                addElement(variables.get("S_SPD"), time, snapshotState.speed());
                lastSnapshotTime = thisSnapshotTime;
            }
        }
        double avgChange = repo.flushElements();
        return avgChange > 0.2 ? 0.5 : 2.0;
    }

    private String prefKey(String key) { return vehicle.getVIN()+"_"+key; }

/*------------------------------------------------------------------------------
 *
 * This section has the code pertaining to the background thread that
 * is responsible for collecting stats on a regular basis
 * 
 *----------------------------------------------------------------------------*/
    
    private static Thread refreshThread = null;
    private static boolean collectionPaused = true;
    
    private void pauseRefresh() { collectionPaused = true; }
    
    private void ensureRefreshThread() {
        collectionPaused = false;
        if (refreshThread == null) {
            refreshThread = appContext.launchThread(new AutoCollect(), "00 AutoCollect");
        }
    }

    private class AutoCollect implements Runnable, ChangeListener<Boolean> {
        private boolean asleep = false;
        
        @Override public void 
        changed(ObservableValue<? extends Boolean> o, Boolean ov, Boolean nv) {
            asleep = nv;
        }
        
        @Override public void run() {
            appContext.shouldBeSleeping.addListener(this);
            asleep = appContext.shouldBeSleeping.get();
            long collectionInterval = DefaultInterval;
            while (true) {
                if (!collectionPaused && !asleep) {
                    double delayFactor = getAndRecordStats();
                    collectionInterval = Utils.clamp(
                            (long)(collectionInterval * delayFactor),
                            MinInterval, MaxInterval);
                }
                Utils.sleep(collectionInterval);
                if (appContext.shuttingDown.get())
                    return;
            }
        }
    }

}
