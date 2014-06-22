/*
 * VTLineChart.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Oct 12, 2013
 */

package org.noroomattheinn.visibletesla.chart;


import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.collections.ObservableList;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.PathElement;

/**
 * A custom subclass of LineChart that knows how to display lines,
 * markers, or both
 * 
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class VTLineChart extends LineChart<Number,Number> {
    
/*------------------------------------------------------------------------------
 *
 * Constants and Enums
 * 
 *----------------------------------------------------------------------------*/
    
    public enum DisplayMode {LinesOnly, MarkersOnly, Both};
    public static final long ALongLongTime = 60*24*365;  // 1 Year in minutes
    
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private final BiMap<Integer,VTSeries> seriesNumberToSeries = HashBiMap.create();
    private final Map<Integer,Boolean> visibilityMap = new HashMap<>();
    private DisplayMode displayMode = DisplayMode.LinesOnly;
    private double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
    private double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
    private long gapTime = ALongLongTime * 60;  // Time that constitutes a gap in data (seconds)
    private boolean ignoreGaps = false;         // Should we ignore gaps
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/

    //
    // Constructors
    //
    
    public VTLineChart(NumberAxis x, NumberAxis y, ObservableList<XYChart.Series<Number,Number>> data) {
        super(x, y, data);
    }
        
    public VTLineChart(NumberAxis x, NumberAxis y) {
        super(x, y);
    }

    
/*------------------------------------------------------------------------------
 *
 * PUBLIC - Handle the assignment of series to the parent
 * 
 *----------------------------------------------------------------------------*/

    public void clearSeries() {
        seriesNumberToSeries.clear();
        getData().clear();
    }

    public VTSeries register(VTSeries s) {
        seriesNumberToSeries.put(seriesNumberToSeries.size(), s);
        return s;
    }
    
    public void applySeriesToChart() {
        getData().clear();
        for (int i = 0; i < seriesNumberToSeries.size(); i++) {
            getData().add(seriesNumberToSeries.get(i).getSeries());
        }
    }
    
    public Collection<VTSeries> set() { return seriesNumberToSeries.values(); }

/*------------------------------------------------------------------------------
 *
 * PUBLIC - Set/Get various characterisitics of how the data is displayed
 * 
 *----------------------------------------------------------------------------*/
    
    public void refreshChart() { layoutPlotChildren(); }

    public void setVisible(VTSeries s, boolean visible) {
        visibilityMap.put(seriesNumberToSeries.inverse().get(s), visible);
    }
    
    public boolean isVisible(VTSeries s) {
        return visibilityMap.get(seriesNumberToSeries.inverse().get(s));
    }
    
    public void setDisplayMode(DisplayMode mode) {
        this.displayMode = mode;
        for (XYChart.Series<Number,Number> s : getData()) { applyLineStyleToSeries(s);}
        refreshChart();
    }
    
    /**
     * Set whether or not to ignore gaps, and if so, what constitutes a gap.
     * Gaps are displayed without line segments in "Both" and "LinesOnly" mode.
     * @param ignore            Should we ignore gaps
     * @param gapTimeMinutes    Time in minutes
     */
    public void setIgnoreGap(boolean ignore, long gapTimeMinutes) {
        this.ignoreGaps = ignore;
        this.gapTime = gapTimeMinutes * 60;
    }
    
/*------------------------------------------------------------------------------
 *
 * PRIVATE - Utility Methods
 * 
 *----------------------------------------------------------------------------*/

    private void applyLineStyleToSeries(XYChart.Series<Number,Number> series) {
        switch (displayMode) {
            case Both:
                series.getNode().setStyle("");  // Use default style from stylesheet
                break;
            case MarkersOnly:
            case LinesOnly:
                series.getNode().setStyle("");
                series.getNode().setStyle("-fx-opacity: 1.0; -fx-stroke-width: 2px;");
                break;
        }
    }
    
    private void addLineSegment(
            ObservableList<PathElement> path, MutablePoint2D point, boolean gap) {
        if (displayMode != DisplayMode.MarkersOnly) {
            if (gap) path.add(new MoveTo(point.x, point.y));
            else path.add(new LineTo(point.x, point.y));
        }
    }
    
    private void addMarker(ObservableList<PathElement> path, MutablePoint2D point) {
        if (displayMode != DisplayMode.LinesOnly) {
            path.add(new MoveTo(point.x - 1, point.y - 1));
            path.add(new LineTo(point.x + 1, point.y - 1));
            path.add(new MoveTo(point.x - 1, point.y));
            path.add(new LineTo(point.x + 1, point.y));
            path.add(new MoveTo(point.x - 1, point.y + 1));
            path.add(new LineTo(point.x + 1, point.y + 1));
        }
    }
    
    private void trackMinMax(MutablePoint2D point) {
        if (point.y < minY) minY = point.y; if (point.y > maxY) maxY = point.y;
        if (point.x < minX) minX = point.x; if (point.x > maxX) maxX = point.x;
    }
    
/*------------------------------------------------------------------------------
 *
 * PROTECTED - Methods overriden from LineChart
 * 
 *----------------------------------------------------------------------------*/
    
    
    @Override protected void layoutPlotChildren() {
        
        NumberAxis xAxis = (NumberAxis)getXAxis();
        NumberAxis yAxis = (NumberAxis)getYAxis();
        double xAxisMin = xAxis.getLowerBound();
        double xAxisMax = xAxis.getUpperBound();
        

        minX = minY = Double.POSITIVE_INFINITY;
        maxX = maxY = Double.NEGATIVE_INFINITY;
        
        for (int seriesIndex = 0; seriesIndex < getData().size(); seriesIndex++) {
            XYChart.Series<Number,Number> series = getData().get(seriesIndex);
            ObservableList<PathElement> markerPath = (new Path()).getElements();
            if (series.getNode() instanceof  Path) {
                ObservableList<PathElement> line = ((Path)series.getNode()).getElements();
                line.clear();
                if (!visibilityMap.get(seriesIndex)) continue;

                line.add(new MoveTo(0,0));  // We need an initial MoveTo...
                                            // Set the actual values at the end
                
                MutablePoint2D start = null, end = null;
                MutablePoint2D previous = MutablePoint2D.negativeInfinity();
                long lastX = Long.MAX_VALUE;
                
                for (XYChart.Data<Number,Number> item : series.getData()) {
                    MutablePoint2D cur = new MutablePoint2D(
                        xAxis.toNumericValue(item.getXValue()),
                        yAxis.toNumericValue(item.getYValue()));
                    
                    long curX = item.getXValue().longValue();
                    boolean gap = false;
                    if (ignoreGaps) gap = Math.abs(lastX - curX) > gapTime;
                    lastX = curX;
                    
                    trackMinMax(cur);
                    
                    MutablePoint2D display = new MutablePoint2D(
                        xAxis.getDisplayPosition(cur.x),
                        yAxis.getDisplayPosition(yAxis.toRealValue(cur.y)));
                    
                    if (cur.x < xAxisMin) {
                        if (start == null) start = new MutablePoint2D(display);
                        else start.copy(display);
                        continue;
                    }
                        
                    if (cur.x > xAxisMax)  {
                        if (end == null) end = new MutablePoint2D(display.x, display.y);
                        continue;
                    }
                    
                    if (Math.abs(display.x - previous.x) + Math.abs(display.y - previous.y) > 2) {
                        if (start == null) start = new MutablePoint2D(display);
                        addLineSegment(line, display, gap);
                        addMarker(markerPath, display);
                        previous.copy(display);
                    }
                }
                
                if (displayMode != DisplayMode.MarkersOnly) {
                    // Fix the coords of the initial MoveTo...
                    if (start != null)  line.set(0, new MoveTo(start.x, start.y));
                    // Add a final line segment if necessary
                    if (end != null) line.add(new LineTo(end.x, end.y));
                }
                line.addAll(markerPath);
            }
        }
        updateAxisRange();
    }
    
    /**
     * Make auto-ranging work when some values are hidden. Without this override,
     * The axes would scale to the maximum value even if those values are hidden.
     * That can be confusing. We keep tack of the min/max values on each axis,
     * but we only track the visible elements. This is done in layoutPlotChildren
     * since it has to enumerate all of the points anyway.
     */
    @Override protected void updateAxisRange() {
        final NumberAxis xa = (NumberAxis)getXAxis();
        final NumberAxis ya = (NumberAxis)getYAxis();
        if (xa.isAutoRanging()) {
            List<Number> xData = new ArrayList<>();
            xData.add(minX); xData.add(maxX);
            xa.invalidateRange(xData);
        }
        if (ya.isAutoRanging()) {
            List<Number> yData = new ArrayList<>();
            if (minY == Double.POSITIVE_INFINITY || maxY == Double.NEGATIVE_INFINITY ||
                (Math.abs(minY - maxY) < 1.0)) {
                yData.add(0); yData.add(100);
            }
            else {
                yData.add(minY); yData.add(maxY);
            }
            ya.invalidateRange(yData);
        }
    }
    
}
class MutablePoint2D {

    public double x;
    public double y;

    MutablePoint2D(double x, double y) {
        this.x = x;
        this.y = y;
    }

    MutablePoint2D(MutablePoint2D orig) {
        this.x = orig.x;
        this.y = orig.y;
    }

    void copy(MutablePoint2D orig) {
        this.x = orig.x;
        this.y = orig.y;
    }
    
    static MutablePoint2D negativeInfinity() {
        return new MutablePoint2D(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY);
    }
    
    static MutablePoint2D positiveInfinity() {
        return new MutablePoint2D(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
    }
}
