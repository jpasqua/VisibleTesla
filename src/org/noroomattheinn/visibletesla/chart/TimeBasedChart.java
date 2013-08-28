/*
 * TimeBasedChart.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Aug 25, 2013
 */

package org.noroomattheinn.visibletesla.chart;

import java.util.Date;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.AnchorPane;
import javafx.util.StringConverter;
import javafx.util.converter.TimeStringConverter;

/**
 * TimeBasedChart
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */

public class TimeBasedChart {
    private static final int minorTicksForX = 20;
    
    private NumberAxis xAxis, yAxis;
    private LineChart<Number, Number> lineChart;
    private Label readout;
    private AnchorPane root;
    
    public TimeBasedChart(AnchorPane container, Label readout) {
        this.root = container;
        this.readout = readout;
        createChart();
    }
    
    public LineChart<Number,Number> getChart() { return lineChart; }
    
    private void createChart() {
        long now = new Date().getTime()/(60*1000);
        xAxis = new NumberAxis(now-15, now+15, 30/minorTicksForX);
        yAxis = new NumberAxis();
        
        xAxis.setAnimated(false);
        yAxis.setAnimated(false);

        xAxis.setTickLabelFormatter(new NSC());

        yAxis.setTickLabelFormatter(new NumberAxis.DefaultFormatter(yAxis) {
            @Override
            public String toString(Number object) {
                return String.format("%3.1f", object);
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
        private static final double MinRange = 10;            // 30 Minutes
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
            xAxis.tickUnitProperty().set((upperBound - lowerBound)/minorTicksForX);
        }
    
    }
    
    class ChartScroller implements EventHandler<MouseEvent> {
        double lastX;
        LineChart<Number, Number> lineChart;
        TimeStringConverter timeFormatter = new TimeStringConverter("MM/dd HH:mm");

        ChartScroller(LineChart<Number, Number> lineChart) { this.lineChart = lineChart; }
        
        @Override
        public void handle(MouseEvent mouseEvent) {
            if (mouseEvent.getEventType() == MouseEvent.MOUSE_MOVED && readout != null) {
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
