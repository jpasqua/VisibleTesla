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
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;

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
    
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private final BiMap<Integer,VTSeries> seriesNumberToSeries = HashBiMap.create();
    private final Map<Integer,Boolean> visibilityMap = new HashMap<>();
    private DisplayMode displayMode = DisplayMode.LinesOnly;
    private double minX, minY = Double.POSITIVE_INFINITY;
    private double maxX, maxY = Double.NEGATIVE_INFINITY;
    
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
    
    public boolean isSeriesVisible(VTSeries s) {
        return visibilityMap.get(seriesNumberToSeries.inverse().get(s));
    }
    
    public void setDisplayMode(DisplayMode mode) {
        this.displayMode = mode;
        for (XYChart.Series<Number,Number> s : getData()) { applyLineStyleToSeries(s);}
        refreshChart();
    }
    
/*------------------------------------------------------------------------------
 *
 * PRIVATE - Utility Methods
 * 
 *----------------------------------------------------------------------------*/

    private void applyLineStyleToSeries(XYChart.Series<Number,Number> series) {
        switch (displayMode) {
            case MarkersOnly:
                series.getNode().setStyle("-fx-stroke: transparent;");
                break;
            case Both:
                series.getNode().setStyle("");
                break;
            case LinesOnly:
                series.getNode().setStyle("");
                series.getNode().setStyle("-fx-opacity: 1.0; -fx-stroke-width: 2px;");
                break;
        }
    }
    
/*------------------------------------------------------------------------------
 *
 * PROTECTED - Methods overriden from LineChart
 * 
 *----------------------------------------------------------------------------*/
    
    @Override protected void layoutPlotChildren() {
        NumberAxis xAxis = (NumberAxis)getXAxis();
        NumberAxis yAxis = (NumberAxis)getYAxis();
        
        minX = minY = Double.POSITIVE_INFINITY;
        maxX = maxY = Double.NEGATIVE_INFINITY;
        
        for (int seriesIndex=0; seriesIndex < getData().size(); seriesIndex++) {
            XYChart.Series<Number,Number> series = getData().get(seriesIndex);
            boolean isFirst = true;
            if (series.getNode() instanceof  Path) {
                Path seriesLine = (Path)series.getNode();
                seriesLine.getElements().clear();
                Boolean visible = visibilityMap.get(seriesIndex);
                for (XYChart.Data<Number,Number> item : series.getData()) {
                    if (!visible) {
                        Node symbol = item.getNode();
                        if (symbol != null) symbol.setStyle("-fx-fill: transparent");
                        continue;
                    }
                    
                    // Keep track of the range of visible values on each axis
                    double curY = yAxis.toNumericValue(item.getYValue());
                    double curX = xAxis.toNumericValue(item.getXValue());
                    if (curY < minY) minY = curY;
                    if (curY > maxY) maxY = curY;
                    if (curX < minX) minX = curX;
                    if (curX > maxX) maxX = curX;
                    
                    double x = xAxis.getDisplayPosition(item.getXValue());
                    double y = yAxis.getDisplayPosition(yAxis.toRealValue(curY));
                    if (isFirst) {
                        isFirst = false;
                        seriesLine.getElements().add(new MoveTo(x, y));
                    } else {
                        seriesLine.getElements().add(new LineTo(x, y));
                    }
                    Node symbol = item.getNode();
                    if (symbol != null) {
                        final double w = symbol.prefWidth(-1);
                        final double h = symbol.prefHeight(-1);
                        symbol.resizeRelocate(x-(w/2), y-(h/2),w,h);
                        if (displayMode == DisplayMode.LinesOnly) symbol.setStyle("-fx-fill: transparent");
                        else symbol.setStyle("");
                    }
                }
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
            yData.add(minY); yData.add(maxY);
            ya.invalidateRange(yData);
        }
    }

}