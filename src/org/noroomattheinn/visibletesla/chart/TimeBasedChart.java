/*
 * TimeBasedChart.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Aug 25, 2013
 */

package org.noroomattheinn.visibletesla.chart;

import java.util.Date;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.AnchorPane;
import javafx.util.StringConverter;
import javafx.util.converter.TimeStringConverter;
import org.noroomattheinn.utils.Utils;

/**
 * TimeBasedChart
 * Notes:
 * - This chart internally works with time in second - not milliseconds.
 * - It is completely an implementation detail that the XAxis Tick Formatter
 *   works. It assumes that it will be called with data in x axis order. It does,
 *   but there is no guarantee of that.
 * - The minorTicksForX value is carefully chosen to make the number of ticks
 *   compatible with the average label size on the axis.
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class TimeBasedChart {
    
/*------------------------------------------------------------------------------
 *
 * Constants and Enums
 * 
 *----------------------------------------------------------------------------*/

    private static final int minorTicksForX = 10;
    private static final int minorTicksForY = 5;
    private static final double MinRangeX = secondsFromMinutes(10);
    private static final double MaxRangeX = secondsFromDays(30);
    private static final double MinRangeY = 25;
    private static final double MaxRangeY = 500;
    
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private NumberAxis xAxis, yAxis;
    private LineChart<Number, Number> lineChart;
    private Label readout;
    private AnchorPane root;
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public TimeBasedChart(AnchorPane container, Label readout) {
        this.root = container;
        this.readout = readout;
        createChart();
    }
    
    public LineChart<Number,Number> getChart() { return lineChart; }

/*------------------------------------------------------------------------------
 *
 * The guts of chart creation
 * 
 *----------------------------------------------------------------------------*/

    public void centerTime(long timeInMillis) {
        long time = secondsFromMillis(timeInMillis);
        double lowerBound = xAxis.getLowerBound();
        double upperBound = xAxis.getUpperBound();
        double centerOffset = ((upperBound + lowerBound)/2) - lowerBound;
        double newLower = time - centerOffset;
        xAxis.setLowerBound(newLower);
        xAxis.setUpperBound(newLower + (upperBound - lowerBound));
        yAxis.setLowerBound(-1);
        yAxis.setUpperBound(1);
        yAxis.setAutoRanging(true);
    }

    private void createChart() {
        long nowInSeconds = secondsFromMillis(System.currentTimeMillis());
        long nowInMinutes = minutesFromSeconds(nowInSeconds);
        xAxis = new NumberAxis(
                secondsFromMinutes(nowInMinutes - 15), 
                secondsFromMinutes(nowInMinutes + 15), 
                secondsFromMinutes(30)/minorTicksForX);
        yAxis = new NumberAxis();
        
        xAxis.setAnimated(false);
        yAxis.setAnimated(false);

        xAxis.setTickLabelFormatter(new DateLabelGenerator());
        yAxis.setTickLabelFormatter(new NumberAxis.DefaultFormatter(yAxis) {
            @Override public String toString(Number object) {
                return String.format("%3.1f", object);
            }
        });

        lineChart = new LineChart<>(xAxis, yAxis);
        lineChart.setCreateSymbols(false);
        lineChart.setAlternativeRowFillVisible(false);
        lineChart.setAnimated(false);
        lineChart.setLegendVisible(false);
        lineChart.setCursor(Cursor.CROSSHAIR);
        
        AnchorPane.setTopAnchor(lineChart, 0.0);
        AnchorPane.setBottomAnchor(lineChart, 0.0);
        AnchorPane.setLeftAnchor(lineChart, 0.0);
        AnchorPane.setRightAnchor(lineChart, 0.0);
        root.getChildren().add(0, lineChart);
        
        // This is tricky. If you put the event filter on the line chart, the
        // event coords will be in the wrong space. It will be in a coord
        // system that includes the axes, etc. You want coords relative to
        // just the chart. HOWEVER, if you attach the eventFilter to the chart
        // background, you won't receive events if the mouse happens to be over
        // a line in the chart - it consumes the events!
        // To deal with this, put the event filter on the chart, but translate
        // the event coordinates to the background.
        final Node chartBackground = lineChart.lookup(".chart-plot-background");
        lineChart.addEventFilter(MouseEvent.ANY, new ChartScroller(chartBackground));
        
        lineChart.addEventFilter(ScrollEvent.ANY, new ChartZoomer(chartBackground));
    }
    
    class DateLabelGenerator extends StringConverter<Number> {
        TimeStringConverter hmConverter = new TimeStringConverter("HH:mm");
        TimeStringConverter mdConverter = new TimeStringConverter("MM/dd");
        String lastMD = "";
        
        @Override public String toString(Number t) {
            Date d = new Date(t.longValue()*(1000));
            String hourAndMinute = hmConverter.toString(d);
            String monthAndDay = mdConverter.toString(d);
            
            if (lastMD.equals(monthAndDay))
                return hourAndMinute;
            
            lastMD = monthAndDay;
            return hourAndMinute + "\n" + monthAndDay;
        }
        
        @Override public Number fromString(String string) { return Long.valueOf(string); }
    }
    
/*------------------------------------------------------------------------------
 *
 * Zooming and Scrolling (actually dragging) the Chart
 * 
 *----------------------------------------------------------------------------*/
    
    class ChartZoomer implements EventHandler<ScrollEvent> {
        private Node chart;
        
        ChartZoomer(Node chart) { this.chart = chart; }
        
        private void handle(
                double delta, double mouseLoc, NumberAxis axis, int minorTicks,
                double min, double max) {

            double current = axis.getValueForDisplay(mouseLoc).doubleValue();
            double lowerBound = axis.getLowerBound();
            double upperBound = axis.getUpperBound();
            double range = upperBound - lowerBound;
            double scalePercent = delta < 0 ? (1/1.1) : 1.1;
            double newRange = Utils.clamp(range * scalePercent, min, max);
            double ratio = newRange / range;
            double newLowerBound = current - ratio * (current - lowerBound);
            double newUpperBound = newLowerBound + newRange;
            
            axis.setAutoRanging(false);
            axis.setLowerBound(newLowerBound);
            axis.setUpperBound(newUpperBound);
            if (minorTicks != 0)
                axis.setTickUnit((upperBound - lowerBound)/minorTicks);
        }
        
        @Override public void handle(ScrollEvent event) {
            Point2D offset = getOffset(lineChart, chart);
            boolean ctrl = event.isControlDown();
            boolean shift = event.isShiftDown();
            boolean none = !ctrl && ! shift;
            
            if (ctrl || shift)
                handle(event.getDeltaY(), event.getY()-offset.getY(), 
                       yAxis, minorTicksForY, MinRangeY, MaxRangeY);
            if (none || (shift && !ctrl))
                handle(event.getDeltaY(), event.getX()-offset.getX(),
                       xAxis, minorTicksForX, MinRangeX, MaxRangeX);
        }
    }

    class ChartScroller implements EventHandler<MouseEvent> {
        private double lastX, lastY = 0;
        private Node chart;
        
        ChartScroller(Node chart) { this.chart = chart; }
        
        private double handle(
                EventType et, double newVal, double oldVal,
                NumberAxis axis, double axisSizeInPixels, int scale) {
            if (et == MouseEvent.MOUSE_PRESSED) {
                oldVal = newVal;
            } else if (et == MouseEvent.MOUSE_DRAGGED || et == MouseEvent.MOUSE_MOVED) {
                if (et == MouseEvent.MOUSE_DRAGGED) {
                    double sizeInValueUnits = axis.getUpperBound() - axis.getLowerBound();
                    double factor = sizeInValueUnits / axisSizeInPixels;
                    double delta = (newVal - oldVal) * factor * scale;
                    axis.setAutoRanging(false);
                    axis.setLowerBound(axis.getLowerBound() - delta);
                    axis.setUpperBound(axis.getUpperBound() - delta);
                }
                oldVal = newVal;
            }
            return oldVal;
        }
        
        @Override
        public void handle(MouseEvent event) {
            EventType et = event.getEventType();
            boolean ctrl = event.isControlDown();
            boolean shift = event.isShiftDown();
            boolean none = !ctrl && ! shift;
            Point2D offset = getOffset(lineChart, chart);
            double x = event.getX() - offset.getX();
            double y = event.getY() - offset.getY();
            
            if (et == MouseEvent.MOUSE_MOVED) updateReadout(x, y);
            
            if (ctrl || shift)
                lastY = handle(et, y, lastY, yAxis, yAxis.getHeight(), -1);
            if (none || (shift && !ctrl))
                lastX = handle(et, x, lastX, xAxis, xAxis.getWidth(), 1);
        }
    }
    
    private Point2D getOffset(Node ancestor, Node leaf) {
        double xOff = 0;
        double yOff = 0;
        
        Node parent = null;
        while (parent != ancestor) {
            Bounds b = leaf.boundsInParentProperty().get();
            xOff += b.getMinX();
            yOff += b.getMinY();
            
            parent = leaf.getParent();
            leaf = parent;
        }
        
        return new Point2D(xOff, yOff);
    }
    
    
    
/*------------------------------------------------------------------------------
 *
 * Private Utility Methods
 * 
 *----------------------------------------------------------------------------*/
    
    private static final TimeStringConverter timeFormatter =
            new TimeStringConverter("MM/dd HH:mm");
    
    private void updateReadout(double x, double y) {
        if (readout == null) return;
        long time = millisFromSeconds(
            xAxis.getValueForDisplay(x).longValue());
        double value =
            yAxis.getValueForDisplay(y).doubleValue();
        readout.setText(
            String.format("[%s: %3.1f]",
            timeFormatter.toString(new Date(time)), value));
    }
    
    private static long secondsFromMillis(long timeInMillis) { return timeInMillis/1000; }    
    private static long millisFromSeconds(long timeInMillis) { return timeInMillis*1000; }    
    private static long minutesFromSeconds(long timeInSeconds) { return timeInSeconds / 60; }
    private static long secondsFromMinutes(long timeInMinutes) { return timeInMinutes * 60; }
    private static long secondsFromHours(long timeInHours) { return secondsFromMinutes(timeInHours * 60); }
    private static long secondsFromDays(long timeInDays) { return secondsFromHours(timeInDays * 24); }
}
