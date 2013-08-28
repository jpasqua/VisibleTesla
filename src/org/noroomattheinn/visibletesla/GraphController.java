/*
 * HVACController.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Jul 22, 2013
 */

package org.noroomattheinn.visibletesla;

import java.util.Date;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.chart.XYChart;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import org.noroomattheinn.tesla.ChargeState;
import org.noroomattheinn.tesla.Vehicle;
import org.noroomattheinn.visibletesla.chart.TimeBasedChart;
import org.noroomattheinn.visibletesla.chart.Variable;
import org.noroomattheinn.visibletesla.chart.VariableSet;

// TO DO:
//

public class GraphController extends BaseController implements StatsRepository.Recorder {

    private ChargeState chargeState;
    private StatsRepository repo;
    private VariableSet variables;
    
    //
    // UI Components
    //
    
    @FXML private Label readout;
    @FXML private CheckBox voltageCheckbox;
    @FXML private CheckBox currentCheckbox;
    @FXML private CheckBox rangeCheckbox;
    @FXML private CheckBox socCheckbox;
    @FXML private CheckBox rocCheckbox;
    private TimeBasedChart chart;
    
    //
    // Variables
    //
    
    //
    // UI Interaction Handlers
    //
    
    @FXML void optionCheckboxHandler(ActionEvent event) {
        CheckBox cb = (CheckBox)event.getSource();
        variables.getByKey(cb).visible = cb.isSelected();
        variables.assignToChart(chart.getChart());
    }


    private void prepVariables() {
        variables.clear();
        variables.register(new Variable(voltageCheckbox, "C_VLT", "violet"));
        variables.register(new Variable(currentCheckbox, "C_AMP", "aqua"));
        variables.register(new Variable(rangeCheckbox, "C_EST", "red"));
        variables.register(new Variable(socCheckbox, "C_SOC", "salmon"));
        variables.register(new Variable(rocCheckbox, "C_ROC", "blue"));
    }
    
    //
    // Overriden methods from BaseController
    //
    
    protected void prepForVehicle(Vehicle v) {
        if (chargeState == null || !chargeState.getVehicle().getVIN().equals(v.getVIN())) {
            chargeState = new ChargeState(v);
            variables = new VariableSet();
            
            pauseRefresh();
            prepVariables();
            if (repo != null) repo.close();
            repo = new StatsRepository(v.getVIN(), this);
            variables.assignToChart(chart.getChart());
            ensureRefreshThread();  // If thread already exists, it unpauses
        }
    }

    protected void refresh() { }

    protected void reflectNewState() { }

    protected void doInitialize() {
        refreshButton.setDisable(true);
        refreshButton.setVisible(false);
        progressIndicator.setVisible(false);
        progressLabel.setVisible(false);
        chart = new TimeBasedChart(root, readout);
    }
    
    
    //
    // Private Utility Methods for remembering values in memory
    // and in the repository
    //
    
    
    private void addElement(Variable variable, long time, double value) {
        // Round to 1 decimal place. The rest isn't really significant
        value = Math.round(value *10.0)/10.0;
        recordElement(variable, time, value);
        repo.storeElement(variable.type, time, value);
    }
    
    // Implement StatsRepository.Recorder
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
                variable.seriesData.add(new XYChart.Data<Number, Number>(time/(60*1000), value));
            }
        });
    }
    
    //
    // This section has the code pertaining to the background thread that
    // is responsible for collecting stats on a regular basis
    //
    
    static final long RefreshInterval = 5 * 60 * 1000;
    private static long lastRefreshTime = 0;
    private static Thread refreshThread = null;
    
    private void getAndRecordStats() {
        chargeState.refresh();
        if (chargeState.hasValidData()) {
            long time = new Date().getTime();
            addElement(variables.get("C_VLT"), time, chargeState.chargerVoltage());
            addElement(variables.get("C_AMP"), time, chargeState.chargerActualCurrent());
            addElement(variables.get("C_EST"), time, chargeState.range());
            addElement(variables.get("C_SOC"), time, chargeState.batteryPercent());
            addElement(variables.get("C_ROC"), time, chargeState.chargeRate());
            repo.flushElements();
        }
    }
    
    private boolean collectionPaused = true;
    
    private void pauseRefresh() { collectionPaused = true; }
    
    private void ensureRefreshThread() {
        collectionPaused = false;
        if (refreshThread == null) {
            refreshThread = new Thread(new AutoRefresh());
            refreshThread.setDaemon(true);
            refreshThread.start();
            lastRefreshTime = new Date().getTime();
        }
    }
    
    class AutoRefresh implements Runnable {
        @Override public void run() {
            while (true) {
                if (!collectionPaused)
                    getAndRecordStats();
                try {
                    long timeToSleep = RefreshInterval;
                    while (timeToSleep > 0) {
                        Thread.sleep(timeToSleep);
                        timeToSleep = RefreshInterval - (new Date().getTime() - lastRefreshTime);
                        timeToSleep = Math.min(timeToSleep, RefreshInterval);
                    }
                } catch (InterruptedException ex) { }
            }
        }
    }

}
