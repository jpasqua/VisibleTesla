/*
 * TimeBasedChart.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Aug 25, 2013
 */

package org.noroomattheinn.visibletesla.fxextensions;

import com.google.common.collect.Range;
import java.util.Date;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.chart.NumberAxis;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;
import javafx.util.StringConverter;
import javafx.util.converter.TimeStringConverter;

/**
 * TimeBasedChart
 * Notes:
 * - This chartBackground internally works with time in second - not milliseconds.
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
    private static final Range<Long> xRange = Range.closed(secondsFromMinutes(10), secondsFromDays(30));
    private static final Range<Double> yRange = Range.closed(25.0, 500.0);
    
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private final Label readout;
    private NumberAxis xAxis, yAxis;
    private VTLineChart lineChart;
    private ChartUtils utils;
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public TimeBasedChart(AnchorPane container, Label readout) {
        this.readout = readout;
        createChart();
    }
    
    public VTLineChart getChart() { return lineChart; }
    
    public void addContextMenu(ContextMenu cm) {
        utils.enableContextMenu(cm);
    }

    public void centerTime(long timeInMillis) {
        long time = secondsFromMillis(timeInMillis);
        double lowerBound = xAxis.getLowerBound();
        double upperBound = xAxis.getUpperBound();
        double centerOffset = ((upperBound + lowerBound)/2) - lowerBound;
        double newLower = time - centerOffset;
        xAxis.setLowerBound(newLower);
        xAxis.setUpperBound(newLower + (upperBound - lowerBound));
        yAxis.setAutoRanging(true);
    }
    
/*------------------------------------------------------------------------------
 *
 * PRIVATE - The guts of chartBackground creation
 * 
 *----------------------------------------------------------------------------*/

    private void createChart() {
        long nowInSeconds = secondsFromMillis(System.currentTimeMillis());
        long nowInMinutes = minutesFromSeconds(nowInSeconds);
        
        xAxis = new NumberAxis(
                secondsFromMinutes(nowInMinutes - 23 * 60), 
                secondsFromMinutes(nowInMinutes + 60), 
                secondsFromMinutes(24 * 60)/minorTicksForX);
        xAxis.setAnimated(false);
        xAxis.setAutoRanging(false);
        xAxis.setTickLabelFormatter(new DateLabelGenerator());
        
        yAxis = new NumberAxis(0.0, 250.0, (250.0-0)/minorTicksForY);
        yAxis.setAnimated(false);
        yAxis.setAutoRanging(true);
        yAxis.setTickLabelFormatter(new NumberAxis.DefaultFormatter(yAxis) {
            @Override public String toString(Number object) {
                return String.format("%3.1f", object);
            }
        });

        lineChart = new VTLineChart(xAxis, yAxis);
        //lineChart.setMinorTicksForY(minorTicksForY);
        lineChart.setCreateSymbols(false);
        lineChart.setAlternativeRowFillVisible(false);
        lineChart.setAnimated(false);
        lineChart.setLegendVisible(false);
        lineChart.setCursor(Cursor.CROSSHAIR);
        
        AnchorPane.setTopAnchor(lineChart, 0.0);
        AnchorPane.setBottomAnchor(lineChart, 0.0);
        AnchorPane.setLeftAnchor(lineChart, 0.0);
        AnchorPane.setRightAnchor(lineChart, 0.0);
        
        utils = new ChartUtils(lineChart);
        utils.enableScrolling();        
        utils.enableZooming(xRange, yRange, minorTicksForX, minorTicksForX);
        utils.getValueProperty().addListener(new ChangeListener<Point2D>() {
            @Override public void changed(ObservableValue<? extends Point2D> ov, Point2D t, Point2D t1) {
                updateReadout(t1);
            }
        });

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
 * Private Utility Methods
 * 
 *----------------------------------------------------------------------------*/
    
    private static final TimeStringConverter timeFormatter =
            new TimeStringConverter("MM/dd HH:mm");
    
    private void updateReadout(Point2D point) {
        if (readout == null) return;
        long time = millisFromSeconds(
            xAxis.getValueForDisplay(point.getX()).longValue());
        double value =
            yAxis.getValueForDisplay(point.getY()).doubleValue();
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

