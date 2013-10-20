/*
 * VTSeries.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Aug 25, 2013
 */

package org.noroomattheinn.visibletesla.chart;

import java.util.ArrayList;
import java.util.List;
import javafx.scene.chart.XYChart;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import org.noroomattheinn.utils.Utils;

/**
 * VTSeries: Adds some small additional functionality to an XYChart.VTSeries. It
 * should be a subclass, but unfortunately it's declared final
 * 
 * NOTES
 * 
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */

public class VTSeries implements Comparable {
/*------------------------------------------------------------------------------
 *
 * Constants and Enums
 * 
 *----------------------------------------------------------------------------*/
    
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private final XYChart.Series<Number,Number> series; // The real underlying series
    private final Transform<Number> xXform;             // Transform for X values
    private final Transform<Number> yXform;             // Transform for Y Values
    private final Object seriesLock;                    // Concurrency control
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    
    public VTSeries(String name, Transform<Number> xXform, Transform<Number> yXform) {
        this.xXform = xXform;
        this.yXform = yXform;
        series = new XYChart.Series<>();
        series.setName(name);
        seriesLock = new Object();
    }

/*------------------------------------------------------------------------------
 *
 * "Getters" for various fields
 * 
 *----------------------------------------------------------------------------*/

    public XYChart.Series<Number,Number> getSeries() { return series; }
    
    public String getName() { return series.getName(); }
        
/*------------------------------------------------------------------------------
 *
 * Adding data to the underlying series
 * 
 *----------------------------------------------------------------------------*/
    
    public void addAll(List<Number> times, List<Number> values, boolean atBeginning) {
        int size = times.size();
        List<XYChart.Data<Number,Number>> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            Number time = times.get(i);
            Number value = values.get(i);
            XYChart.Data<Number,Number> data = xform(time, value);
            data.setNode(newMarker());
            entries.add(data);
        }
        
        synchronized (seriesLock) {
            if (atBeginning) series.getData().addAll(0, entries);
            else series.getData().addAll(entries);
        }
    }
    
    public void addToSeries(Number time, Number value, boolean atBeginning) {
        XYChart.Data<Number,Number> data = xform(time, value);
        data.setNode(newMarker());
        
        synchronized (seriesLock) {
            if (atBeginning) series.getData().add(0, data);
            else series.getData().add(data);
        }
    }

/*------------------------------------------------------------------------------
 *
 * Methods overriden from Object
 * 
 *----------------------------------------------------------------------------*/
    
    @Override public int compareTo(Object o) {
        return series.getName().compareTo(((VTSeries)o).series.getName());
    }
    
/*------------------------------------------------------------------------------
 *
 * PRIVATE - Utility Methods
 * 
 *----------------------------------------------------------------------------*/
    
    private XYChart.Data<Number,Number> xform(Number time, Number value) {
        return new XYChart.Data<>(xXform.transform(time), yXform.transform(value));
    }
    
    private Shape newMarker() {
        return new Rectangle(3,3);
        //return new Circle(2);
    }
    
/*------------------------------------------------------------------------------
 *
 * The Transform Interface and several interesting instances
 * 
 *----------------------------------------------------------------------------*/

    public interface Transform<T> {
        T transform(T value);
    }
    
    public static Transform<Number> idTransform = new Transform<Number>() {
        @Override
        public Number transform(Number value) { return value; }
    };
            
    public static Transform<Number> cToFTransform = new Transform<Number>() {
        @Override
        public Number transform(Number value) { return Utils.cToF(value.doubleValue()); }
    };
    
    public static Transform<Number> fToCTransform = new Transform<Number>() {
        @Override
        public Number transform(Number value) { return Utils.fToC(value.doubleValue()); }
    };
    
    public static Transform<Number> mToKTransform = new Transform<Number>() {
        @Override
        public Double transform(Number value) { return Utils.mToK(value.doubleValue()); }
    };
    
    public static Transform<Number> kToMTransform = new Transform<Number>() {
        @Override
        public Double transform(Number value) { return Utils.kToM(value.doubleValue()); }
    };

    public static Transform<Number> millisToSeconds = new Transform<Number>() {
        @Override
        public Long transform(Number value) { return value.longValue()/1000; }
    };

}
