/*
 * HVACController.java - Copyright(c) 2013  All Rights Reserved, Joe Pasqua
 * Created: Jul 22, 2013
 */

package org.noroomattheinn.visibletesla;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.AnchorPane;
import javafx.util.StringConverter;
import javafx.util.converter.TimeStringConverter;
import org.noroomattheinn.tesla.ChargeState;
import org.noroomattheinn.tesla.Vehicle;

public class GraphController extends BaseController {
    static final long RefreshInterval = 5 * 60 * 1000;
    private static long lastRefreshTime = 0;
    private static Thread refreshThread = null;

    private ChargeState chargeState;
    private PrintStream statsWriter;

    private NumberAxis xAxis, yAxis;
    private LineChart<Number, Number> lineChart;

    private XYChart.Series<Number, Number> voltageSeries = new XYChart.Series<>();
    private XYChart.Series<Number, Number> currentSeries = new XYChart.Series<>();
    private XYChart.Series<Number, Number> rangeSeries = new XYChart.Series<>();
    private XYChart.Series<Number, Number> socSeries = new XYChart.Series<>();
    private XYChart.Series<Number, Number> rocSeries = new XYChart.Series<>();
    private List<XYChart.Series<Number, Number>> visibleSeries = new ArrayList<>();
    
    @FXML private Label readout;
    @FXML private CheckBox voltageCheckbox;
    @FXML private CheckBox currentCheckbox;
    @FXML private CheckBox rangeCheckbox;
    @FXML private CheckBox socCheckbox;
    @FXML private CheckBox rocCheckbox;
    
    @FXML void optionCheckboxHandler(ActionEvent event) {
        XYChart.Series<Number, Number> series = null;
        CheckBox cb = (CheckBox)event.getSource();
        if (cb == voltageCheckbox) series = voltageSeries;
        else if (cb == currentCheckbox) series = currentSeries;
        else if (cb == rangeCheckbox) series = rangeSeries;
        else if (cb == socCheckbox) series = socSeries;
        else if (cb == rocCheckbox) series = rocSeries;
        if (series != null) {
            if (cb.isSelected()) {
                if (!visibleSeries.contains(series)) {
                    visibleSeries.add(series);
                }
            } else {
                visibleSeries.remove(series);
            }
            lineChart.getData().clear();
            lineChart.getData().addAll(visibleSeries);
        }
    }

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
            voltageSeries = new XYChart.Series<>();
            currentSeries = new XYChart.Series<>();
            rangeSeries = new XYChart.Series<>();
            socSeries = new XYChart.Series<>();
            rocSeries = new XYChart.Series<>();
            String statsFileName = v.getVIN()+".stats.log";
                // Read all the existing data
            try {
                Scanner s = new Scanner(new BufferedReader(new FileReader(statsFileName)));
                // <long>time       <String>type    <double>value
                // 1377316202051    C_EST           126.620000
                while (s.hasNext()) {
                    long time = s.nextLong();
                    String type = s.next();
                    double val = s.nextDouble();
                    switch (type) {
                        case "C_ROC":
                            addElement(rocSeries, time, val);
                            break;
                        case "C_SOC":
                            addElement(socSeries, time, val);
                            break;
                        case "C_EST":
                            addElement(rangeSeries, time, val);
                            break;
                        case "C_VLT":
                            addElement(voltageSeries, time, val);
                            break;
                        case "C_AMP":
                            addElement(currentSeries, time, val);
                            break;
                        default:
                            break;
                    }
                }
            } catch (FileNotFoundException ex) {
                Logger.getLogger(GraphController.class.getName()).log(Level.SEVERE, null, ex);
            }

            try {
                // Open the file for append
                statsWriter = new PrintStream(new FileOutputStream(v.getVIN()+".stats.log", true));
            } catch (FileNotFoundException ex) {
                Logger.getLogger(GraphController.class.getName()).log(Level.SEVERE, null, ex);
                statsWriter = null;
            }
            visibleSeries.addAll(Arrays.asList(voltageSeries, currentSeries, rangeSeries, socSeries, rocSeries));
            lineChart.getData().addAll(visibleSeries);
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
        createChart();
    }    

    private void addElement(XYChart.Series<Number, Number> series, long time, double value) {
        series.getData().add(new XYChart.Data<Number, Number>(time/(60*1000), value));
    }
    
    private void getStats() {
        chargeState.refresh();
        if (chargeState.hasValidData()) {
            // TO DO: If this reading is the same as the last reading, then perhaps optimize
            long time = new Date().getTime();
            addElement(voltageSeries, time, chargeState.chargerVoltage());
            addElement(currentSeries, time, chargeState.chargerActualCurrent());
            addElement(rangeSeries, time, chargeState.estimatedRange());
            addElement(socSeries, time, chargeState.batteryPercent());
            addElement(rocSeries, time, chargeState.chargeRate());
            statsWriter.format("%d\tC_VLT\t%d\n", time, chargeState.chargerVoltage());
            statsWriter.format("%d\tC_AMP\t%d\n", time, chargeState.chargerActualCurrent());
            statsWriter.format("%d\tC_EST\t%f\n", time, chargeState.estimatedRange());
            statsWriter.format("%d\tC_SOC\t%d\n", time, chargeState.batteryPercent());
            statsWriter.format("%d\tC_ROC\t%f\n", time, chargeState.chargeRate());
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
    
    private void createChart() {
        long now = new Date().getTime()/(60*1000);
        xAxis = new NumberAxis(now, now+30, 30/20);
        yAxis = new NumberAxis();
        
        xAxis.setAnimated(false);
        yAxis.setAnimated(false);

        xAxis.setTickLabelFormatter(new NSC());

        yAxis.setTickLabelFormatter(new NumberAxis.DefaultFormatter(yAxis) {
            @Override
            public String toString(Number object) {
                return String.format("%3.2f", object);
            }
        });

        lineChart = new LineChart<>(xAxis, yAxis);

        lineChart.setCreateSymbols(false);
        lineChart.setAlternativeRowFillVisible(false);
        lineChart.setAnimated(false);
        lineChart.setLegendVisible(false);
        AnchorPane.setTopAnchor(lineChart, 0.0);
        AnchorPane.setBottomAnchor(lineChart, 0.0);
        AnchorPane.setLeftAnchor(lineChart, 0.0);
        AnchorPane.setRightAnchor(lineChart, 0.0);
        root.getChildren().add(0, lineChart);
        
        final Node chartBackground = lineChart.lookup(".chart-plot-background");
        ChartScroller scroller = new ChartScroller(lineChart);
        chartBackground.setOnMouseClicked(scroller);
        chartBackground.setOnMouseDragged(scroller);
        chartBackground.setOnMouseEntered(scroller);
        chartBackground.setOnMouseExited(scroller);
        chartBackground.setOnMouseMoved(scroller);
        chartBackground.setOnMousePressed(scroller);
        chartBackground.setOnMouseReleased(scroller);
        
        ChartZoomer zoomer = new ChartZoomer();
        lineChart.setOnScroll(zoomer);
    }
    
    class NSC extends StringConverter<Number> {
        TimeStringConverter hmConverter = new TimeStringConverter("HH:mm");
        TimeStringConverter mdConverter = new TimeStringConverter("MM/dd");
        String lastMD = "";
        
        @Override public String toString(Number t) {
            Date d = new Date(t.longValue()*(60*1000));
            String hourAndMinute = hmConverter.toString(d);
            String monthAndDay = mdConverter.toString(d);
            if (lastMD.equals(monthAndDay))
                return hourAndMinute;
            lastMD = monthAndDay;
            return hourAndMinute + "\n" + monthAndDay;
        }
        
        @Override public Number fromString(String string) { return Long.valueOf(string); }
    }

    class ChartZoomer implements EventHandler<ScrollEvent> {
        private static final double MinRange = 30;            // 30 Minutes
        private static final double MaxRange = 30 * 24 * 60;  // A month
        @Override public void handle(ScrollEvent scrollEvent) {
            double movement = scrollEvent.getDeltaY();
            double lowerBound = xAxis.lowerBoundProperty().doubleValue();
            double upperBound = xAxis.upperBoundProperty().doubleValue();
            double range = upperBound - lowerBound;
            double scalePercent = 0.1 * ((movement < 0) ? -1 : 1);
            lowerBound -= range * scalePercent;
            upperBound += range * scalePercent;
            range = upperBound - lowerBound;
            if (range < MinRange)
                upperBound = lowerBound + MinRange;
            if (range > MaxRange)
                upperBound = lowerBound + MaxRange;
            xAxis.lowerBoundProperty().set(lowerBound);
            xAxis.upperBoundProperty().set(upperBound);
            xAxis.tickUnitProperty().set((upperBound - lowerBound)/20);
        }
    
    }
    
    class ChartScroller implements EventHandler<MouseEvent> {
        double lastX;
        LineChart<Number, Number> lineChart;
        TimeStringConverter timeFormatter = new TimeStringConverter("MM/dd HH:mm");

        ChartScroller(LineChart<Number, Number> lineChart) { this.lineChart = lineChart; }
        
        @Override
        public void handle(MouseEvent mouseEvent) {
            if (mouseEvent.getEventType() == MouseEvent.MOUSE_MOVED) {
                long time = xAxis.getValueForDisplay(mouseEvent.getX()).longValue()*1000*60;
                double value = yAxis.getValueForDisplay(mouseEvent.getY()).doubleValue();
                readout.setText(
                        String.format("[%s: %3.1f]", timeFormatter.toString(new Date(time)), value));
            }
            if (mouseEvent.getEventType() == MouseEvent.MOUSE_PRESSED) {
                lastX = mouseEvent.getX();
            } else if (mouseEvent.getEventType() == MouseEvent.MOUSE_DRAGGED ||
                    mouseEvent.getEventType() == MouseEvent.MOUSE_MOVED) {
                NumberAxis xAxis = (NumberAxis) lineChart.getXAxis();

                double newXlower;
                double newXupper;

                if (mouseEvent.getEventType() == MouseEvent.MOUSE_DRAGGED) {
                    double delta = mouseEvent.getX() - lastX;
                    double widthInPixels = xAxis.getWidth();
                    double widthInXUnits = xAxis.getUpperBound() - xAxis.getLowerBound();
                    double factor = widthInXUnits / widthInPixels;
                    delta *= factor;
                    newXlower = xAxis.getLowerBound() - delta;
                    newXupper = xAxis.getUpperBound() - delta;
                    xAxis.setLowerBound(newXlower);
                    xAxis.setUpperBound(newXupper);
                }
                lastX = mouseEvent.getX();
            }
        }
    }

}
