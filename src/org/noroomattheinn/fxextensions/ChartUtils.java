/*
 * ChartUtils.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Oct 16, 2013
 */

package org.noroomattheinn.fxextensions;

import com.google.common.collect.Range;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.chart.NumberAxis;
import javafx.scene.control.ContextMenu;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import org.noroomattheinn.utils.Utils;

/**
 * ChartUtils: A selection of utilities for enhancing a VTLineChart with functions
 * like scrolling and zooming.
 * 
 * Notes: There is a tricky piece to this. Scrolling, zooming, etc. are all
 * based on mouse events. If you put the event filter on the line chart,
 * the event coords will be in the wrong space. It will be in a coord
 * system that includes the axes, etc. You want coords relative to
 * just the chartBackground. HOWEVER, if you attach the eventFilter to the
 * chart background, you won't receive events if the mouse happens to be over
 * a line in the chartBackground - it consumes the events!
 * To deal with this, put the event filter on the chart, but translate
 * the event coordinates to the chart background.

 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */

public class ChartUtils {
    
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/

    private final VTLineChart lineChart;
    private final Node chartBackground;
    private final NumberAxis xAxis, yAxis;
    private ObjectProperty<Point2D> valueTracker;
    private ContextMenu contextMenu;
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public ChartUtils(VTLineChart lineChart) {
        this.lineChart = lineChart;
        this.chartBackground = lineChart.lookup(".chart-plot-background");
        this.xAxis = (NumberAxis)lineChart.getXAxis();
        this.yAxis = (NumberAxis)lineChart.getYAxis();
        
        valueTracker = null;
        contextMenu = null;

        lineChart.addEventFilter(MouseEvent.ANY, new BasicListener());
    }
    
    /**
     * Make this chart scrollable by dragging it with the mouse.
     */
    public void enableScrolling() {
        lineChart.addEventFilter(MouseEvent.ANY, new ChartScroller());
    }
    
    /**
     * Make this chart zoomable using the scrollwheel
     * @param xRange        The allowable zoom range in X
     * @param yRange        The allowable zoom range in Y
     * @param xTicks        Tick units for the x Axis
     * @param yTicks        Tick units for the y Axis
     */
    public void enableZooming(Range<Long> xRange, Range<Double>yRange, int xTicks, int yTicks) {
        lineChart.addEventFilter(ScrollEvent.ANY, new ChartZoomer(xRange, yRange, xTicks, yTicks));
    }
    /**
     * Add a context menu & activate it when the user presses the correct mouse button
     * @param menu  The context menu to be displayed
     */
    public void enableContextMenu(ContextMenu menu) {
        contextMenu = menu;
    }
    
    /**
     * Get an ObjectProperty corresponding to the x,y values under the mouse
     * cursor in the chart. Add a ChangeListener to this property to keep
     * track of changes.
     * @return  ObjectProperty that contains the current x,y values
     */
    public ObjectProperty<Point2D> getValueProperty() {
        if (valueTracker == null)
            valueTracker = new SimpleObjectProperty<>();
        return valueTracker;
    }
    
/*------------------------------------------------------------------------------
 *
 * PRIVATE - Utility Methods
 * 
 *----------------------------------------------------------------------------*/
    
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
 * PRIVATE - Classes that implement the Zooming, Scrolling, and other
 *           mouse based funtions
 * 
 *----------------------------------------------------------------------------*/
    
    private class BasicListener implements EventHandler<MouseEvent> {
        @Override public void handle(MouseEvent event) {
            EventType et = event.getEventType();
            Point2D offset = getOffset(lineChart, chartBackground);
            double x = event.getX() - offset.getX();
            double y = event.getY() - offset.getY();

            if (et == MouseEvent.MOUSE_MOVED && valueTracker != null) {
                valueTracker.set(new Point2D(x, y));
            }
            
            if (contextMenu != null && MouseButton.SECONDARY.equals(event.getButton())) {
                contextMenu.show(chartBackground, event.getScreenX(), event.getScreenY());
            }
        }
    }

    private class ChartScroller implements EventHandler<MouseEvent> {

        public ObjectProperty<Point2D> hoverValue;
        private double lastX, lastY = 0;

        ChartScroller() {
            this.hoverValue = new SimpleObjectProperty<>();
        }

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
                    axis.setLowerBound(axis.getLowerBound() - delta);
                    axis.setUpperBound(axis.getUpperBound() - delta);
                }
                oldVal = newVal;
            }
            return oldVal;
        }

        @Override public void handle(MouseEvent event) {
            EventType et = event.getEventType();
            boolean ctrl = event.isControlDown();
            boolean shift = event.isShiftDown();
            boolean none = !ctrl && !shift;
            Point2D offset = getOffset(lineChart, chartBackground);
            double x = event.getX() - offset.getX();
            double y = event.getY() - offset.getY();

            if (et == MouseEvent.MOUSE_MOVED) { hoverValue.set(new Point2D(x, y)); }

            if (ctrl || shift) {
                lastY = handle(et, y, lastY, yAxis, yAxis.getHeight(), -1);
            }
            if (none || (shift && !ctrl)) {
                lastX = handle(et, x, lastX, xAxis, xAxis.getWidth(), 1);
            }
        }
    }
    
    private class ChartZoomer implements EventHandler<ScrollEvent> {
        private final Range<Long>xRange;
        private final Range<Double>yRange;
        private final int xTicks, yTicks;

        ChartZoomer(Range<Long> xRange, Range<Double> yRange, int xTicks, int yTicks) {
            this.xRange = xRange;
            this.yRange = yRange;
            this.xTicks = xTicks;
            this.yTicks = yTicks;
        }
        
        private void handle(
                double delta, double mouseLoc, NumberAxis axis, int minorTicks,
                double min, double max) {
            if (delta == 0) return;
            double absDelta = Math.abs(delta);
            double scalePercent = 1.1;
            if (absDelta > 10) scalePercent = 1.2;
            if (absDelta > 100) scalePercent = 1.25;
            if (absDelta > 200) scalePercent = 1.5;
            if (delta < 0) scalePercent = 1.0/scalePercent;
            
            double current = axis.getValueForDisplay(mouseLoc).doubleValue();
            double lowerBound = axis.getLowerBound();
            double upperBound = axis.getUpperBound();
            double range = upperBound - lowerBound;
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
            Point2D offset = getOffset(lineChart, chartBackground);
            boolean ctrl = event.isControlDown();
            boolean shift = event.isShiftDown();
            boolean none = !ctrl && ! shift;
            Bounds b;
            
            if (ctrl || shift) {
                handle(event.getDeltaY(), event.getY()-offset.getY(), 
                       yAxis, yTicks, yRange.lowerEndpoint(), yRange.upperEndpoint());
            }
            if (none || (shift && !ctrl)) {
                handle(event.getDeltaY(), event.getX()-offset.getX(),
                       xAxis, xTicks, xRange.lowerEndpoint(), xRange.upperEndpoint());
            }
        }
    }
}
