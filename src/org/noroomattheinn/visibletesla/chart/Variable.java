/*
 * Variable.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Aug 25, 2013
 */

package org.noroomattheinn.visibletesla.chart;

import javafx.collections.ObservableList;
import javafx.scene.chart.XYChart;
import javafx.scene.control.CheckBox;
import org.noroomattheinn.utils.Utils;

/**
 * Variable
 * 
 * NOTES
 * - The color that is passed to the constructor is currently ignored!
 *   All colors are set via the CSS and not programmatically. This is 
 *   because I can't find a way to set the symbol color. There is an easy
 *   way to set the line color and CheckBox color.
 * 
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */

public class Variable implements Comparable {
    
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    XYChart.Series<Number, Number> series;
    XYChart.Series<Number, Number> emptySeries;
    String color;
    Transform xform;
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public String type;
    public ObservableList<XYChart.Data<Number, Number>> seriesData;
    public boolean visible;
    public CheckBox cb;
    public double minVal, maxVal;
    private boolean linesVisible;
    
    public Variable(CheckBox cb, String type, String color, Transform xform) {
        this.cb = cb;
        this.type = type;
        this.color = color;
        this.xform = xform;
        series = new XYChart.Series<>();
        emptySeries = new XYChart.Series<>();
        seriesData = series.getData();
        visible = true;
        minVal = maxVal = 0;
        linesVisible = true;
    }

// NOTE:
//          We don't establish color programatically any more. This is left here
//          as documentation in case we ever do so in the future
//    public void establishColor(int seriesNumber) {
//        //series.getNode().setStyle("-fx-stroke: " + color + ";");
//        //series.getNode().setStyle("-fx-stroke: transparent;");
//        //cb.setStyle("-fx-text-fill: " + color + ";");
//        cb.getStyleClass().clear();
//        cb.getStyleClass().add("cb"+seriesNumber);
//    }
    
    public void reflectLineVisibility() {
        if (linesVisible) {
            series.getNode().setStyle("");
        } else{
            series.getNode().setStyle("-fx-stroke: transparent;");
        }
    }
    
    public void setLineVisibility(boolean displayLines) {
        linesVisible = displayLines;
        reflectLineVisibility();
    }
    
    public void addToSeries(long time, double value) {
        if (value < minVal) minVal = value;
        if (value > maxVal) maxVal = value;
        seriesData.add(new XYChart.Data<Number, Number>(time/(1000), xform.transform(value)));
    }

    @Override public int compareTo(Object o) {
        return type.compareTo(((Variable)o).type);
    }

/*------------------------------------------------------------------------------
 *
 * The Transform Interface and several interesting instances
 * 
 *----------------------------------------------------------------------------*/

    public interface Transform {
        double transform(double value);
    }
    
    public static Transform idTransform = new Transform() {
        public double transform(double value) { return value; }
    };
            
    public static Transform cToFTransform = new Transform() {
        public double transform(double value) { return Utils.cToF(value); }
    };
    
    public static Transform fToCTransform = new Transform() {
        public double transform(double value) { return Utils.fToC(value); }
    };
    
    public static Transform mToKTransform = new Transform() {
        public double transform(double value) { return Utils.mToK(value); }
    };
    
    public static Transform kToMTransform = new Transform() {
        public double transform(double value) { return Utils.kToM(value); }
    };

}
