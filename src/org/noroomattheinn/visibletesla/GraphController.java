/*
 * GraphController.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Jul 22, 2013
 */
package org.noroomattheinn.visibletesla;

import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.AnchorPane;
import org.noroomattheinn.tesla.GUIState;
import org.noroomattheinn.tesla.Vehicle;
import org.noroomattheinn.visibletesla.chart.VTLineChart;
import org.noroomattheinn.visibletesla.chart.TimeBasedChart;
import org.noroomattheinn.visibletesla.chart.VTSeries;
import org.noroomattheinn.visibletesla.stats.Stat;

/**
 * GraphController: Handles the capture and display of vehicle statistics
 * 
 * NOTES:
 * To add a new Series:
 * 1. If the series requires a new state object:
 *    1.1 Add the declaration of the object
 *    1.2 Initialize the object in prepForVehicle
 *    1.3 In getAndRecordStats: refresh the object and addElement on each series
 * 2. Add the corresponding checkbox
 *    2.1 Add a decalration for the checkbox and compile this source
 *    2.2 Open GraphUI.fxml and add a checkbox to the dropdown list
 * 3. Register the new series in prepSeries()
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class GraphController extends BaseController {

/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private Map<String, Integer> valueMap = null;
    private boolean displayLines = true;
    private boolean displayMarkers = true;
    
/*------------------------------------------------------------------------------
 *
 * UI Elements
 * 
 *----------------------------------------------------------------------------*/
    
    @FXML private Label readout;
    @FXML private CheckBox voltageCheckbox;
    @FXML private CheckBox currentCheckbox;
    @FXML private CheckBox rangeCheckbox;
    @FXML private CheckBox socCheckbox;
    @FXML private CheckBox rocCheckbox;
    @FXML private CheckBox powerCheckbox;
    @FXML private CheckBox batteryCurrentCheckbox;
    @FXML private CheckBox speedCheckbox;
    @FXML private AnchorPane itemListContent;
    @FXML private Button showItemsButton;
    @FXML private Button nowButton;
    @FXML private AnchorPane arrow;
    
    private RadioMenuItem displayLinesMI;
    private RadioMenuItem displayMarkersMI;
    private RadioMenuItem displayBothMI;
    private TimeBasedChart chart;
    private VTLineChart lineChart = null;
    
/*------------------------------------------------------------------------------
 *
 * This section implements UI Actionhandlers
 * 
 *----------------------------------------------------------------------------*/
    
    private void showItemList(boolean visible) {
        itemListContent.setVisible(visible);
        itemListContent.setMouseTransparent(!visible);
        arrow.setStyle(visible ? "-fx-rotate: 0;" : "-fx-rotate: -90;");
    }

    @FXML void nowHandler(ActionEvent event) {
        chart.centerTime(System.currentTimeMillis());

    }

    @FXML void showItemsHandler(ActionEvent event) {
        boolean isVisible = itemListContent.isVisible();
        showItemList(!isVisible);   // Flip whether it's visible
    }

    @FXML void optionCheckboxHandler(ActionEvent event) {
        CheckBox cb = (CheckBox) event.getSource();
        boolean visible = cb.isSelected();
        VTSeries series = cbToSeries.get(cb);
        lineChart.setVisible(series, visible);
        lineChart.refreshChart();

        // Remember the value for next time we start up
        appContext.persistentState.putBoolean(prefKey(series.getName()), visible);
    }

/*------------------------------------------------------------------------------
 *
 * VTSeries Handling
 * 
 *----------------------------------------------------------------------------*/
    
    private Map<CheckBox,VTSeries> cbToSeries = new LinkedHashMap<>(); // Preserves insertion order
    private Map<String,VTSeries> typeToSeries = new HashMap<>();
    
    private void prepSeries() {
        GUIState.State guiState = appContext.lastKnownGUIState.get();
        VTSeries.Transform<Number> distTransform = guiState.distanceUnits.startsWith("mi")
                ? VTSeries.idTransform : VTSeries.mToKTransform;
        lineChart.clearSeries();

        cbToSeries.put(voltageCheckbox, lineChart.register(
                new VTSeries(StatsStore.VoltageKey, VTSeries.millisToSeconds, VTSeries.idTransform)));
        cbToSeries.put(currentCheckbox, lineChart.register(
                new VTSeries(StatsStore.CurrentKey, VTSeries.millisToSeconds, VTSeries.idTransform)));
        cbToSeries.put(rangeCheckbox, lineChart.register(
                new VTSeries(StatsStore.EstRangeKey, VTSeries.millisToSeconds, distTransform)));
        cbToSeries.put(socCheckbox, lineChart.register(
                new VTSeries(StatsStore.SOCKey, VTSeries.millisToSeconds, VTSeries.idTransform)));
        cbToSeries.put(rocCheckbox, lineChart.register(
                new VTSeries(StatsStore.ROCKey, VTSeries.millisToSeconds, distTransform)));
        cbToSeries.put(powerCheckbox, lineChart.register(
                new VTSeries(StatsStore.PowerKey, VTSeries.millisToSeconds, VTSeries.idTransform)));
        cbToSeries.put(speedCheckbox, lineChart.register(
                new VTSeries(StatsStore.SpeedKey, VTSeries.millisToSeconds, distTransform)));
        cbToSeries.put(batteryCurrentCheckbox, lineChart.register(
                new VTSeries(StatsStore.BatteryAmpsKey, VTSeries.millisToSeconds, VTSeries.idTransform)));
        
        // Make the checkbox colors match the series colors
        int seriesNumber = 0;
        for (Map.Entry<CheckBox,VTSeries> me: cbToSeries.entrySet()) {
            CheckBox cb = me.getKey();
            VTSeries s = me.getValue();
            cb.getStyleClass().add("cb"+seriesNumber++);
            typeToSeries.put(s.getName(), s);
        }
    }

    private void restoreLastSettings() {
        // Restore the last settings of the checkboxes
        for (CheckBox cb : cbToSeries.keySet()) {
            VTSeries s = cbToSeries.get(cb);
            boolean selected = appContext.persistentState.getBoolean(prefKey(s.getName()), true);
            cb.setSelected(selected);
            lineChart.setVisible(s, selected);
        }

        // Restore the last display settings (display lines, markers, or both)
        displayLines = appContext.persistentState.getBoolean(prefKey("DISPLAY_LINES"), true);
        displayMarkers = appContext.persistentState.getBoolean(prefKey("DISPLAY_MARKERS"), true);

        reflectDisplayOptions();
    }

/*------------------------------------------------------------------------------
 *
 * Methods overridden from BaseController
 * 
 *----------------------------------------------------------------------------*/
    
    @Override protected void prepForVehicle(Vehicle v) {
        if (differentVehicle()) {
            prepSeries();
            loadExistingData();
            // Register for additions to the data
            appContext.statsStore.newestVoltage.addListener(statHandler);
            appContext.statsStore.newestCurrent.addListener(statHandler);
            appContext.statsStore.newestEstRange.addListener(statHandler);
            appContext.statsStore.newestSOC.addListener(statHandler);
            appContext.statsStore.newestROC.addListener(statHandler);
            appContext.statsStore.newestBatteryAmps.addListener(statHandler);
            appContext.statsStore.newestPower.addListener(statHandler);
            appContext.statsStore.newestSpeed.addListener(statHandler);
        }
    }

    @Override protected void refresh() { }

    @Override protected void reflectNewState() {    }

    
    @Override protected void fxInitialize() {
        nowButton.setVisible(false);
        refreshButton.setDisable(true);
        refreshButton.setVisible(false);
        progressIndicator.setVisible(false);
        progressLabel.setVisible(false);
        chart = new TimeBasedChart(root, readout);
        lineChart = chart.getChart();
        createContextMenu();
        showItemList(false);
        root.getChildren().add(0, lineChart);
        nowButton.setVisible(true);
    }

    @Override protected void appInitialize() {
        // This is a hack!! For some reason this is the only way I can get styles
        // to work for ToolTips. I should be able to just choose the appropriate
        // css class decalratively, but that doesn't work and no one seems to
        // know why. This is a workaround.
        URL url = getClass().getClassLoader().getResource("org/noroomattheinn/styles/tooltip.css");
        appContext.stage.getScene().getStylesheets().add(url.toExternalForm());
    }

/*------------------------------------------------------------------------------
 *
 * PRIVATE - Utility Methods for attaching a ContextMenu to the LineChart
 * 
 *----------------------------------------------------------------------------*/
    
    private void createContextMenu() {
        final ContextMenu contextMenu = new ContextMenu();
        final ToggleGroup toggleGroup = new ToggleGroup();

        displayLinesMI = new RadioMenuItem("Display Only Lines");
        displayLinesMI.setOnAction(displayMIHandler);
        displayLinesMI.setSelected(displayLines);
        displayLinesMI.setToggleGroup(toggleGroup);
        displayMarkersMI = new RadioMenuItem("Display Only Markers");
        displayMarkersMI.setOnAction(displayMIHandler);
        displayMarkersMI.setSelected(displayMarkers);
        displayMarkersMI.setToggleGroup(toggleGroup);
        displayBothMI = new RadioMenuItem("Display Both");
        displayBothMI.setOnAction(displayMIHandler);
        displayBothMI.setSelected(displayMarkers);
        displayBothMI.setToggleGroup(toggleGroup);

        if (displayLines && displayMarkers) {   displayBothMI.setSelected(true);
        } else if (displayLines) {              displayLinesMI.setSelected(true);
        } else if (displayMarkers) {            displayMarkersMI.setSelected(true);
        }

        contextMenu.getItems().addAll(displayLinesMI, displayMarkersMI, displayBothMI);
        chart.addContextMenu(contextMenu);
    }
    
    private EventHandler<ActionEvent> displayMIHandler = new EventHandler<ActionEvent>() {
        @Override
        public void handle(ActionEvent event) {
            RadioMenuItem target = (RadioMenuItem) event.getTarget();
            if (target == displayLinesMI) {
                displayLines = true;
                displayMarkers = false;
            } else if (target == displayMarkersMI) {
                displayLines = false;
                displayMarkers = true;
            } else {
                displayLines = true;
                displayMarkers = true;
            }

            reflectDisplayOptions();

            appContext.persistentState.putBoolean(prefKey("DISPLAY_LINES"), displayLines);
            appContext.persistentState.putBoolean(prefKey("DISPLAY_MARKERS"), displayMarkers);
        }
    };

    private void reflectDisplayOptions() {        
        if (displayMarkers && displayLines) {
            displayBothMI.setSelected(true);
            lineChart.setDisplayMode(VTLineChart.DisplayMode.Both);
        } else if (displayLines) {
            displayLinesMI.setSelected(true);
            displayBothMI.setSelected(false);
            lineChart.setDisplayMode(VTLineChart.DisplayMode.LinesOnly);
        } else {
            displayMarkersMI.setSelected(true);
            displayBothMI.setSelected(false);
            lineChart.setDisplayMode(VTLineChart.DisplayMode.MarkersOnly);
        }
    }

/*------------------------------------------------------------------------------
 *
 * PRIVATE - Loading existing data into the Series
 * 
 *----------------------------------------------------------------------------*/
        
    private void loadExistingData() {
        Map<Long,Map<String,Double>> rows = appContext.statsStore.getData();
        Map<String,ObservableList<XYChart.Data<Number,Number>>> typeToList = new HashMap<>();
        
        for (String type : typeToSeries.keySet()) {
            ObservableList<XYChart.Data<Number,Number>> data =
                    FXCollections.<XYChart.Data<Number,Number>>observableArrayList();
            typeToList.put(type, data);
        }
        
        Map<String,Long> lastTimeForType = new HashMap<>();
        
        for (Map.Entry<Long,Map<String,Double>> row : rows.entrySet()) {
            long time = row.getKey();
            Map<String,Double> pairs = row.getValue();
            for (Map.Entry<String,Double> pair : pairs.entrySet()) {
                String type = pair.getKey();
                double value = pair.getValue();
                ObservableList<XYChart.Data<Number,Number>> data = typeToList.get(type);
                if (type != null) {
                    Long lastTime = lastTimeForType.get(type);
                    if (lastTime == null) lastTime = 0L;
                    if (time - lastTime >= 5 * 1000) {
                        VTSeries vts = typeToSeries.get(type);
                        data.add(new XYChart.Data<>(
                                vts.getXformX().transform(time), 
                                vts.getXformY().transform(value)));
                        lastTimeForType.put(type, time);
                    }
                }
            }
        }
        
        for (Map.Entry<String,VTSeries> entry : typeToSeries.entrySet()) {
            VTSeries vts = entry.getValue();
            String type = entry.getKey();
            vts.setData(typeToList.get(type));
        }
        
        lineChart.applySeriesToChart();
        restoreLastSettings();
    }
    
/*------------------------------------------------------------------------------
 *
 * PRIVATE - Listen for and add new data points to the graph
 * 
 *----------------------------------------------------------------------------*/
    
    private void addElement(final VTSeries series, final long time, double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) value = 0;
        double rounded = Math.round(value * 10.0) / 10.0;   // Limit to 1 decimal place
        series.addToSeries(time, rounded, false);
    }
    
    
    private final ChangeListener<Stat> statHandler = new ChangeListener<Stat>() {
        @Override public void changed(ObservableValue<? extends Stat> ov, Stat t, final Stat stat) {
            Platform.runLater(new Runnable() {
                @Override public void run() {
                    addElement(typeToSeries.get(stat.type), stat.sample.timestamp, stat.sample.value);
                }
            });
        };
    };

}
