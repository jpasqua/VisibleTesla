/*
 * VariableSet.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Aug 25, 2013
 */

package org.noroomattheinn.visibletesla.chart;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import javafx.scene.chart.LineChart;

/**
 * VariableSet
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */

public class VariableSet {
    
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private Map<Integer,Variable> variables = new HashMap<>();
    private Map<Object, Variable> objectToVariable = new HashMap<>();
    private Map<String, Variable> typeToVariable = new HashMap<>();
    private int nSeries = 0;    // The number of variables (series) we've got
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public void clear() {
        variables.clear();
        objectToVariable.clear();
        typeToVariable.clear();
        nSeries = 0;
    }

    public void register(Variable v) {
        variables.put(nSeries, v);
        objectToVariable.put(v.cb, v);
        typeToVariable.put(v.type, v);
        // Establish the color of the CheckBox associated with this series
        v.cb.getStyleClass().add("cb"+nSeries);
        nSeries++;
    }

    public void assignToChart(LineChart<Number,Number> lineChart) {
        lineChart.getData().clear();
        for (int i = 0; i < nSeries; i++) {
            Variable var = variables.get(i);
            lineChart.getData().add(var.visible ? var.series : var.emptySeries);
            var.reflectLineVisibility();
        }
    }

    public Variable get(String type) {
        return typeToVariable.get(type);
    }

    public Variable getByKey(Object key) {
        return objectToVariable.get(key);
    }
    
    public Collection<Variable> set() { return variables.values(); }
    
    public void setLineVisibility(Variable.LineType lineType) {
        for (Variable v : variables.values()) { v.setLineVisibility(lineType); }
    }
    
    public static class Range {
        double min;
        double max;
        Range(double min, double max) { this.min = min; this.max = max; }
    }
    
    public Range getRange(boolean visibleOnly) {
        double minVal = 0;
        double maxVal = 0;
        for (Variable v : variables.values()) {
            if (visibleOnly && !v.visible)
                continue;
            
            if (minVal < v.minVal)
                minVal = v.minVal;
            if (maxVal > v.maxVal)
                maxVal = v.maxVal;
        }
        return new Range(minVal, maxVal);
    }
}
