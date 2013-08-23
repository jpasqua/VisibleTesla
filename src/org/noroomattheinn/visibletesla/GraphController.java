/*
 * HVACController.java - Copyright(c) 2013  All Rights Reserved, Joe Pasqua
 * Created: Jul 22, 2013
 */

package org.noroomattheinn.visibletesla;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.scene.chart.XYChart;
import org.noroomattheinn.tesla.ChargeState;
import org.noroomattheinn.tesla.Vehicle;

public class GraphController extends BaseController {
    static final long RefreshInterval = 60 * 1000;
    private static long lastRefreshTime = 0;
    private static Thread refreshThread = null;

    private ChargeState chargeState;
    private PrintStream statsWriter;

    private XYChart.Series<Number, Number> voltageSeries = new XYChart.Series<>();
    private XYChart.Series<Number, Number> currentSeries = new XYChart.Series<>();
    private XYChart.Series<Number, Number> rangeSeries = new XYChart.Series<>();
    
    //
    // Overriden methods from BaseController
    //
    
    protected void prepForVehicle(Vehicle v) {
        ensureRefreshThread();
        if (chargeState == null || !v.getVIN().equals(vehicle.getVIN())) {
            chargeState = new ChargeState(v);
            if (statsWriter != null) {
                statsWriter.close();
            }
            try {
                statsWriter = new PrintStream(new FileOutputStream(v.getVIN()+".stats.log", true));
            } catch (FileNotFoundException ex) {
                Logger.getLogger(GraphController.class.getName()).log(Level.SEVERE, null, ex);
                statsWriter = null;
            }
            voltageSeries = new XYChart.Series<>();
            currentSeries = new XYChart.Series<>();
            rangeSeries = new XYChart.Series<>();
        }
    }

    protected void refresh() {
        // Update the graphs from stored data
    }

    protected void reflectNewState() {
        System.out.println("# Collected Samples: " + rangeSeries.getData().size());
    }

    
    // Controller-specific initialization
    protected void doInitialize() {
        refreshButton.setDisable(true);
        refreshButton.setVisible(false);
        progressIndicator.setVisible(false);
        progressLabel.setVisible(false);
    }    

    private void getStats() {
        chargeState.refresh();
        if (chargeState.hasValidData()) {
            // TO DO: If this reading is the same as the last reading, then perhaps optimize
            long time = new Date().getTime();
            voltageSeries.getData().add(new XYChart.Data<Number, Number>(time, chargeState.chargerVoltage()));
            currentSeries.getData().add(new XYChart.Data<Number, Number>(time, chargeState.chargerActualCurrent()));
            rangeSeries.getData().add(new XYChart.Data<Number, Number>(time, chargeState.estimatedRange()));
            statsWriter.format("%d\tC_VLT\t%d\n", time, chargeState.chargerVoltage());
            statsWriter.format("%d\tC_AMP\t%d\n", time, chargeState.chargerActualCurrent());
            statsWriter.format("%d\tC_EST\t%f\n", time, chargeState.estimatedRange());
            statsWriter.flush();
        }
    }
    
    private void ensureRefreshThread() {
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
                getStats();
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
