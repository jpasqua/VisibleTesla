/*
 * VampireLossResults.java - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Apr 05, 2014
 */
package org.noroomattheinn.visibletesla.dialogs;

import java.net.URL;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import javafx.util.converter.TimeStringConverter;
import org.noroomattheinn.utils.Utils;
import org.noroomattheinn.visibletesla.VampireStats;

/**
 * VampireLossResults: Display statistics about vampire loss.
 * 
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class VampireLossResults  implements DialogUtils.DialogController {
    private static final int TimeOffset = 0;
    
    private Stage myStage;
    private String units;
    
    @FXML private ResourceBundle resources;
    @FXML private URL location;

    @FXML private TextArea rawDataField;
    @FXML private LineChart<Number, Number> chart;
    @FXML private LineChart<Number, Number> sequenceChart;

    @FXML void initialize() { }

    @Override public void setStage(Stage stage) { this.myStage = stage; }
    @Override public void setProps(Map props) {
        List<VampireStats.Rest> restPeriods = Utils.cast(props.get("REST_PERIODS"));
        if (restPeriods == null || restPeriods.isEmpty()) return;
        units = (String)props.get("UNITS");
        
        // Hack to make tooltip styles works. No one knows why.
        URL url = getClass().getClassLoader().getResource("org/noroomattheinn/styles/tooltip.css");
        myStage.getScene().getStylesheets().add(url.toExternalForm());
        
        // ----- Set up time-based chart
        
        chart.setTitle("Vampire Loss Data");
        chart.setLegendVisible(false);
        Node chartBackground = chart.lookup(".chart-plot-background");
        chartBackground.setStyle("-fx-background-color: white;");
        NumberAxis xAxis = (NumberAxis)chart.getXAxis();
        xAxis.setAutoRanging(false);
        xAxis.setLowerBound(0.0);
        xAxis.setUpperBound(24.0);        
        xAxis.setTickLabelFormatter(new NumberAxis.DefaultFormatter(xAxis) {
            @Override public String toString(Number hr) {
                int adjusted = (hr.intValue() + (24-TimeOffset)) % 24;
                return String.format("%2d", adjusted);
            }
        });
        
        for (VampireStats.Rest r : restPeriods) {
            XYChart.Series<Number,Number> series = new XYChart.Series<>();
            ObservableList<XYChart.Data<Number, Number>> data = series.getData();

            addPeriod(data, r);
            chart.getData().add(series);
            series.getNode().setStyle("-fx-opacity: 0.25; -fx-stroke-width: 3px;");
        }
        
        
        final double overallAverage = (Double)props.get("OVERALL_AVG");
        XYChart.Series<Number,Number> avg = new XYChart.Series<>();
        avg.setName("Average");
        
        final String tip = String.format("Average Loss: %3.2f %s/hr", overallAverage, units);
        final XYChart.Data<Number,Number> p1 = new XYChart.Data<Number,Number>(0.2, overallAverage);
        p1.setExtraValue(tip); avg.getData().add(p1); addTooltip(p1);
        
        final XYChart.Data<Number,Number> p2 = new XYChart.Data<Number,Number>(23.8, overallAverage);
        p2.setExtraValue(tip); avg.getData().add(p2); addTooltip(p2);
        
        chart.getData().add(avg);
        avg.getNode().setStyle("-fx-opacity: 0.5; -fx-stroke-width: 10px;");


        // ----- Set up the Scatter Chart
        // Scale down to seconds from ms. Using ms seems to cause difficulty
        // for the Chart facility
        sequenceChart.setTitle("Vampire Loss Data");
        sequenceChart.setLegendVisible(false);
        chartBackground = sequenceChart.lookup(".chart-plot-background");
        chartBackground.setStyle("-fx-background-color: white;");
        xAxis = (NumberAxis)sequenceChart.getXAxis();
        xAxis.setAutoRanging(false);
        long s = restPeriods.get(0).startTime/1000L;
        long e = restPeriods.get(restPeriods.size()-1).endTime/1000L;
        xAxis.setLowerBound(s); xAxis.setUpperBound(e);
        xAxis.setTickUnit(24*60*60);    // A day worth of seconds
        xAxis.setTickLabelFormatter(new DateLabelGenerator());
        
        XYChart.Series<Number,Number> series = new XYChart.Series<>();
        series.setName("Vampire Loss");
        ObservableList<XYChart.Data<Number, Number>> data = series.getData();
        for (VampireStats.Rest r : restPeriods) {
            final XYChart.Data<Number,Number> dataPoint =
                    new XYChart.Data<Number,Number>(r.startTime/1000L, r.avgLoss());
            addTooltip(dataPoint);
            dataPoint.setExtraValue(r);
            dataPoint.setNode(getMarker(nHours(r)));
            data.add(dataPoint);
        }
        sequenceChart.getData().add(series);
        series.getNode().setStyle("-fx-opacity: 0.0; -fx-stroke-width: 0px;");

        avg = new XYChart.Series<>();
        avg.setName("Average");
        
        XYChart.Data<Number,Number> a1 = new XYChart.Data<Number,Number>(s, overallAverage);
        a1.setExtraValue(tip); avg.getData().add(a1); addTooltip(a1);
        
        XYChart.Data<Number,Number> a2 = new XYChart.Data<Number,Number>(e, overallAverage);
        a2.setExtraValue(tip); avg.getData().add(a2); addTooltip(a2);
        
        sequenceChart.getData().add(avg);
        avg.getNode().setStyle("-fx-opacity: 0.5; -fx-stroke-width: 10px;");
        
        // ----- Set up the raw data text area

        String rawResults = (String) props.get("RAW_RESULTS");
        if (rawResults == null) rawResults = "";
        rawDataField.setText(rawResults);
    }
    
    private double nHours(VampireStats.Rest r) {
        long diff = r.endTime - r.startTime;
        long seconds = diff/1000;
        double minutes = seconds/60;
        double hours = minutes/60;
        return hours;
    }
    
    private Node getMarker(double hrs) {
        double size = ((Math.log(hrs/3.0)/Math.log(2.0))+1)*3;
        if (size < 3) size = 3;

        Circle c = new Circle(size);
        c.setFill(Color.web("#0000ff", 0.5));
        c.setStroke(Color.web("#0000ff"));
        c.setStrokeWidth(1.0);
        return c;
    }
    
    private void addPeriod(ObservableList<XYChart.Data<Number, Number>> data, VampireStats.Rest r) {
        addPoint(data, r, r.startTime);
        addPoint(data, r, r.endTime);
    }
    
    private void addPoint(ObservableList<XYChart.Data<Number, Number>> data,
                          VampireStats.Rest r, long timestamp) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(timestamp);
        double time = c.get(Calendar.HOUR_OF_DAY) + ((double)c.get(Calendar.MINUTE))/60;
        time = (time + TimeOffset) % 24;
        
        final XYChart.Data<Number,Number> dataPoint =
                new XYChart.Data<Number,Number>(time, r.avgLoss());
        dataPoint.setExtraValue(r);
        data.add(dataPoint);
        addTooltip(dataPoint);
    }
    
    private void addTooltip(final XYChart.Data<Number,Number> dataPoint) {
        dataPoint.nodeProperty().addListener(new ChangeListener<Node>() {
            @Override public void changed(ObservableValue<? extends Node> observable,
                    Node oldValue, Node newValue) {
                if (newValue != null) {
                    String tip =  (dataPoint.getExtraValue() instanceof String) ?
                            (String)dataPoint.getExtraValue() : 
                            genTooltip((VampireStats.Rest)dataPoint.getExtraValue());
                    Tooltip.install(newValue, new Tooltip(tip));
                    dataPoint.nodeProperty().removeListener(this);
                }
            }
        });
    }
    
    private double hours(long millis) {return ((double)(millis))/(60 * 60 * 1000); }
    
    private String genTooltip(VampireStats.Rest rest) {
        double period = hours(rest.endTime - rest.startTime);
        double loss = rest.startRange - rest.endRange;
        String date = String.format("%1$tm/%1$td %1$tH:%1$tM", new Date(rest.startTime));
        return String.format(
                "Date: %s\n" + 
                "Elapsed (HH:MM): %02d:%02d\n" + 
                "Loss: %3.2f %s\n" +
                "Loss/hr: %3.2f",
                date, (int)period, (int)((period%1)*60), loss, units, loss/period  );
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
}
