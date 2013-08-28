/*
 * VariableSet.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Aug 25, 2013
 */

package org.noroomattheinn.visibletesla.chart;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javafx.scene.chart.LineChart;

/**
 * VariableSet
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */

public class VariableSet {

    private Set<Variable> variables = new HashSet<>();
    private Map<Object, Variable> objectToVariable = new HashMap<>();
    private Map<String, Variable> typeToVariable = new HashMap<>();

    public void clear() {
        variables.clear();
        objectToVariable.clear();
        typeToVariable.clear();
    }

    public void register(Variable v) {
        variables.add(v);
        objectToVariable.put(v.cb, v);
        typeToVariable.put(v.type, v);
    }

    public void assignToChart(LineChart<Number,Number> lineChart) {
        lineChart.getData().clear();
        for (Variable var : variables) {
            lineChart.getData().add(var.visible ? var.series : var.emptySeries);
            var.establishColor();
        }
    }

    public Variable get(String type) {
        return typeToVariable.get(type);
    }

    public Variable getByKey(Object key) {
        return objectToVariable.get(key);
    }
}
