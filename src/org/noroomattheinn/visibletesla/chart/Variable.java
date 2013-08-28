/*
 * Variable.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Aug 25, 2013
 */

package org.noroomattheinn.visibletesla.chart;

import javafx.collections.ObservableList;
import javafx.scene.chart.XYChart;
import javafx.scene.control.CheckBox;

/**
 * Variable
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */

public class Variable {
    public String type;
    public ObservableList<XYChart.Data<Number, Number>> seriesData;
    public boolean visible;

    XYChart.Series<Number, Number> series;
    XYChart.Series<Number, Number> emptySeries;
    String color;
    CheckBox cb;

    public Variable(CheckBox cb, String type, String color) {
        this.cb = cb;
        this.type = type;
        this.color = color;
        series = new XYChart.Series<>();
        emptySeries = new XYChart.Series<>();
        seriesData = series.getData();
        visible = true;
    }

    public void establishColor() {
        series.getNode().setStyle("-fx-stroke: " + color + ";");
//          cb.setStyle("-fx-background-color: " + color + ";");
        cb.setStyle("-fx-text-fill: " + color + ";");
    }
}
