/*
 * VTSeries.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Aug 25, 2013
 */

package org.noroomattheinn.fxextensions;

import javafx.collections.ObservableList;
import javafx.scene.chart.XYChart;
import org.noroomattheinn.utils.Utils;

/**
 * VTSeries: Adds some small additional functionality to an XYChart.Series. It
 * should be a subclass, but unfortunately it's declared final
 * 
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */

public class VTSeries {

/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private final XYChart.Series<Number,Number> series; // The real underlying series
    private final Transform<Number> xXform;             // Transform for X values
    private final Transform<Number> yXform;             // Transform for Y Values
    private final Object seriesLock;                    // Concurrency control
    private boolean visibile;
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    
    public VTSeries(String name, Transform<Number> xXform, Transform<Number> yXform) {
        this.xXform = xXform;
        this.yXform = yXform;
        series = new XYChart.Series<>();
        series.setAppendOnly(true);
        series.setName(name);
        seriesLock = new Object();
        visibile = true;
    }

    public void setData(ObservableList<XYChart.Data<Number,Number>> data) {
        series.setData(data);
    }
    
/*------------------------------------------------------------------------------
 *
 * "Getters" for various fields
 * 
 *----------------------------------------------------------------------------*/

    public XYChart.Series<Number,Number> getSeries() { return series; }
    
    public String getName() { return series.getName(); }
    
    public void setVisible(boolean visible) { this.visibile = visible; }
    public boolean isVisible() { return this.visibile; }
    
/*------------------------------------------------------------------------------
 *
 * Adding data to the underlying series
 * 
 *----------------------------------------------------------------------------*/
    
    /**
     * Append a new XYChart.Data element to the series with the supplied values.
     * Add an appropriate marker to the newly added XYChart.Data
     * 
     * @param time  The time value (X Axis)
     * @param value The value for that time (Y Axis)
     */
    public void addToSeries(Number time, Number value) {
        synchronized (seriesLock) {
            XYChart.Data<Number,Number> data = addToData(series.getData(), time, value);
        }
    }
    
    /**
     * Append a new XYChart.Data element to the supplied list.
     * This list may be the list associated with this series, or it may be a
     * list that will later be added to this series.
     * 
     * @param time  The time value (X Axis)
     * @param value The value for that time (Y Axis)
     */
    public XYChart.Data<Number,Number> addToData(
            ObservableList<XYChart.Data<Number,Number>> list,
            Number time, Number value)
    {
        XYChart.Data<Number,Number> data = xform(time, value);
        list.add(data);
        return data;
    }
    
/*------------------------------------------------------------------------------
 *
 * PRIVATE - Utility Methods
 * 
 *----------------------------------------------------------------------------*/
    
    private XYChart.Data<Number,Number> xform(Number time, Number value) {
        return new XYChart.Data<>(xXform.transform(time), yXform.transform(value));
    }
    
/*------------------------------------------------------------------------------
 *
 * The Transform Interface and several interesting instances
 * 
 *----------------------------------------------------------------------------*/

    public interface Transform<T> {
        T transform(T value);
    }
    
    public static final Transform<Number> idTransform = new Transform<Number>() {
        @Override public Number transform(Number value) { return value; }
    };
            
    public static final Transform<Number> cToFTransform = new Transform<Number>() {
        @Override public Number transform(Number value) { return Utils.cToF(value.doubleValue()); }
    };
    
    public static final Transform<Number> fToCTransform = new Transform<Number>() {
        @Override public Number transform(Number value) { return Utils.fToC(value.doubleValue()); }
    };
    
    public static final Transform<Number> mToKTransform = new Transform<Number>() {
        @Override public Double transform(Number value) { return Utils.milesToKm(value.doubleValue()); }
    };
    
    public static final Transform<Number> kToMTransform = new Transform<Number>() {
        @Override public Double transform(Number value) { return Utils.kmToMiles(value.doubleValue()); }
    };

    public static final Transform<Number> millisToSeconds = new Transform<Number>() {
        @Override public Long transform(Number value) { return value.longValue()/1000; }
    };

}
