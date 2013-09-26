/*
 * HVACController.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Jul 22, 2013
 */

package org.noroomattheinn.visibletesla;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.chart.XYChart.Data;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialogs;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.AnchorPane;
import javafx.stage.FileChooser;
import jxl.Workbook;
import jxl.write.WritableSheet;
import jxl.write.WritableWorkbook;
import jxl.write.WriteException;
import org.noroomattheinn.tesla.ChargeState;
import org.noroomattheinn.tesla.GUIState;
import org.noroomattheinn.tesla.SnapshotState;
import org.noroomattheinn.tesla.Vehicle;
import org.noroomattheinn.utils.Utils;
import org.noroomattheinn.visibletesla.chart.TimeBasedChart;
import org.noroomattheinn.visibletesla.chart.Variable;
import org.noroomattheinn.visibletesla.chart.VariableSet;

// TO DO:
//

// To add a new variable:
// 1. If the variable requires a new state object:
//    1.1 Add the declaration of the object
//    1.2 Initialize the object in prepForVehicle
//    1.3 In getAndRecordStats: refresh the object and addElement on each variable
// 2. Add the corresponding checkbox
//    2.1 Add a decalration for the checkbox and compile this source
//    2.2 Open GraphUI.fxml and add a checkbox to the dropdown list
// 3. Register the new variable in prepVariables()

public class GraphController extends BaseController implements StatsRepository.Recorder {
    
/*------------------------------------------------------------------------------
 *
 * Constants and Enums
 * 
 *----------------------------------------------------------------------------*/

    private static final long DefaultInterval = 2 * 60 * 1000;  // 2 Minutes
    private static final long MinInterval     =     30 * 1000;  // 30 Seconds
    private static final long MaxInterval     = 5 * 60 * 1000;  // 5 Minutes
    
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private ChargeState chargeState;
    private SnapshotState snapshotState;
    private StatsRepository repo;
    private VariableSet variables;
    private Map<String,Integer> valueMap = null;
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
    @FXML private AnchorPane arrow;
    
    private RadioMenuItem displayLinesMI;
    private RadioMenuItem displayMarkersMI;
    private RadioMenuItem displayBothMI;
    
    private TimeBasedChart chart;
          
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
              
    void exportCSV() {
        if (variables == null) {
            Dialogs.showWarningDialog(
                    appContext.stage,
                    "Your graph data hasn't been loaded yet.\n" +
                    "Please select the Graphs tab then try again");
            return;
        }
                
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Graph Data");
        fileChooser.setInitialDirectory(new File(System.getProperty("user.home")));

        final File file = fileChooser.showSaveDialog(appContext.stage);
        if (file == null)
            return;
        
        if (valueMap == null)
            createValueMap();
        
        TreeMap<Long,Double[]> table = collectIntoTable(valueMap);
        
        try {
            WritableWorkbook workbook = Workbook.createWorkbook(file);
            WritableSheet sheet = workbook.createSheet("Sheet1", 0);
            
            int row = 0;
            
            // Add the header row
            jxl.write.Label label = new jxl.write.Label(0, row, "TIMESTAMP"); 
            sheet.addCell(label);
            sheet.setColumnView(0, 12); // Big enough for a timestamp;
            for (Map.Entry<String,Integer>entry : valueMap.entrySet()) {
                String type = entry.getKey();
                int column = entry.getValue();
                label = new jxl.write.Label(column+1, row, type); 
                sheet.addCell(label);
            }
            sheet.setColumnView(valueMap.size()+1, 16); // Big enough for a Date string;
            
            jxl.write.WritableCellFormat dateFormat = new jxl.write.WritableCellFormat(
                        new jxl.write.DateFormat("M/d/yy H:mm:ss"));
            
            // Run through the table and add each row...
            for (Map.Entry<Long,Double[]> entry : table.entrySet()) {
                row++;
                long time = entry.getKey().longValue();
                jxl.write.Number timeCell = new jxl.write.Number(0, row, time); 
                sheet.addCell(timeCell);
                
                Double[] vals = entry.getValue();
                for (int i = 0; i < vals.length; i++) {
                    double val = vals[i];
                    if (!Double.isNaN(val)) {
                        jxl.write.Number valueCell = new jxl.write.Number(i+1, row, val);
                        sheet.addCell(valueCell);
                    }
                }
                // Create a column which converts the UNIX timestamp to a Date
                TimeZone tz = Calendar.getInstance().getTimeZone();
                long offset = (tz.getOffset(System.currentTimeMillis()))/(1000*60*60);
                String dateFormula = String.format("(A%d/86400)+25569+(%d/24)",row+1, offset);
                jxl.write.Formula f = new jxl.write.Formula(vals.length+1, row, dateFormula, dateFormat);
                sheet.addCell(f);
            }
            workbook.write(); 
            workbook.close();
            
            Dialogs.showInformationDialog(
                    appContext.stage, "Your data has been exported");
        } catch (IOException | WriteException ex) {
            Dialogs.showErrorDialog(
                    appContext.stage, "Unable to save to: " + file);
        }
    }
          
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
        CheckBox cb = (CheckBox)event.getSource();
        Variable var = variables.getByKey(cb);
        var.visible = cb.isSelected();
        variables.assignToChart(chart.getChart());
        // Remember the value for next time we start up
        appContext.prefs.putBoolean(prefKey(var.type), var.visible);
    }    
    
/*------------------------------------------------------------------------------
 *
 * Variable Handling
 * 
 *----------------------------------------------------------------------------*/
    
    private void prepVariables() {
        GUIState gs = vehicle.cachedGUIState();
        Variable.Transform distTransform = gs.distanceUnits().startsWith("mi") ?
                Variable.idTransform : Variable.mToKTransform;
        variables.clear();
        // NOTE: the colors specified below are ignored!!
        // They are currently set via CSS rather than programmatically. I left the
        // color specifications here just in case I figure out how to make the
        // programmatic application of colors work. Right now I can't apply
        // colors to series symbols. That's why it's done via CSS
        variables.register(new Variable(voltageCheckbox, "C_VLT", "violet", Variable.idTransform));
        variables.register(new Variable(currentCheckbox, "C_AMP", "aqua", Variable.idTransform));
        variables.register(new Variable(rangeCheckbox, "C_EST", "red", distTransform));
        variables.register(new Variable(socCheckbox, "C_SOC", "salmon", Variable.idTransform));
        variables.register(new Variable(rocCheckbox, "C_ROC", "blue", distTransform));
        variables.register(new Variable(powerCheckbox, "S_PWR", "gray", Variable.idTransform));
        variables.register(new Variable(speedCheckbox, "S_SPD", "green", distTransform));
        variables.register(new Variable(batteryCurrentCheckbox, "C_BAM", "black", Variable.idTransform));
    }
    
    private void restoreLastSettings() {
        // Restore the last settings of the checkboxes
        for (Variable var : variables.set()) {
            boolean selected = appContext.prefs.getBoolean(prefKey(var.type), true);
            var.cb.setSelected((var.visible = selected));
        }
        
        // Restore the last settings for the display settings (display lines,
        // markers, or both)
        displayLines = appContext.prefs.getBoolean(prefKey("DISPLAY_LINES"), true);
        displayMarkers = appContext.prefs.getBoolean(prefKey("DISPLAY_MARKERS"), true);
        
        variables.assignToChart(chart.getChart());
        reflectDisplayOptions();
    }
    
/*------------------------------------------------------------------------------
 *
 * Methods overridden from BaseController
 * 
 *----------------------------------------------------------------------------*/
    
    protected void prepForVehicle(Vehicle v) {
        if (differentVehicle(chargeState, v)) {
            chargeState = new ChargeState(v);
            snapshotState = new SnapshotState(v);
            variables = new VariableSet();
            
            pauseRefresh();
            prepVariables();
            if (repo != null) repo.close();
            repo = new StatsRepository(v.getVIN());
            repo.loadExistingData(this);
            variables.assignToChart(chart.getChart());
            restoreLastSettings();
            ensureRefreshThread();  // If thread already exists, it unpauses
        }
    }
    
    protected void refresh() { }

    protected void reflectNewState() { }

    protected void fxInitialize() {
        refreshButton.setDisable(true);
        refreshButton.setVisible(false);
        progressIndicator.setVisible(false);
        progressLabel.setVisible(false);
        chart = new TimeBasedChart(root, readout);  
        createContextMenu();
        showItemList(false);
    }
    
    protected void appInitialize() {
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
        
        if (displayLines && displayMarkers) displayBothMI.setSelected(true);
        else if (displayLines) displayLinesMI.setSelected(true);
        else if (displayMarkers) displayMarkersMI.setSelected(true);
        
        contextMenu.getItems().addAll(displayLinesMI, displayMarkersMI, displayBothMI);
        chart.addContextMenu(contextMenu);
    }
    
    private EventHandler<ActionEvent> displayMIHandler = new EventHandler<ActionEvent>(){
        @Override public void handle(ActionEvent event) {
            RadioMenuItem target = (RadioMenuItem)event.getTarget();
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
            
            appContext.prefs.putBoolean(prefKey("DISPLAY_LINES"), displayLines);
            appContext.prefs.putBoolean(prefKey("DISPLAY_MARKERS"), displayMarkers);
            }
    };
    
    private void reflectDisplayOptions() {
        chart.getChart().setCreateSymbols(displayMarkers);
        variables.setLineVisibility(displayLines);
        if (displayLines && displayMarkers) displayBothMI.setSelected(true);
        else if (displayLines) displayLinesMI.setSelected(true);
        else if (displayMarkers) displayMarkersMI.setSelected(true);
    }
    
/*------------------------------------------------------------------------------
 *
 * PRIVATE - Utility Methods for storing stats in memory and on disk
 * 
 *----------------------------------------------------------------------------*/
    
    
    private void addElement(Variable variable, long time, double value) {
        if (Double.isNaN(value) || Double.isInfinite(value))
            value = 0;
        // Round to 1 decimal place. The rest isn't really significant
        value = Math.round(value * 10.0)/10.0;
        recordElement(variable, time, value);
        repo.storeElement(variable.type, time, value);
    }
    
    // Implement StatsRepository.Recorder - Called when old stats are read in
    // by the StatsRepository. All it does is record the data in memory.
    @Override public void recordElement(long time, String type, double val) {
        recordElement(variables.get(type), time, val);
    }

    private void recordElement(final Variable variable, final long time, final double value) {
        // This can be called from a background thread. If we update the chart's
        // series data directly, that could cause a UI refresh which would
        // be happening from a non-UI thread. This can, and has, resulted
        // in a concurrentModificationException.
        // Bottom Line: Do it later on the app thread.
        //Platform.runLater(new ElementRecorder(variable, time, value));
        Platform.runLater(new Runnable() {
            @Override public void run() {
                variable.addToSeries(time, value);
            }
        });
    }
    
    // 
    /**
     * Called by the background thread to gather and record a new set of samples
     * @return  Boolean(true) if the vehicle is in motion
     *          Boolean(false) if it's not
     *          null if we don't know
     */
    private Boolean getAndRecordStats() {
        chargeState.refresh();
        snapshotState.refresh();
        double speed = Double.NaN;
        
        long time = System.currentTimeMillis();
        if (chargeState.hasValidData()) {
            addElement(variables.get("C_VLT"), time, chargeState.chargerVoltage());
            addElement(variables.get("C_AMP"), time, chargeState.chargerActualCurrent());
            addElement(variables.get("C_EST"), time, chargeState.range());
            addElement(variables.get("C_SOC"), time, chargeState.batteryPercent());
            addElement(variables.get("C_ROC"), time, chargeState.chargeRate());
            addElement(variables.get("C_BAM"), time, chargeState.batteryCurrent());
        }
        if (snapshotState.hasValidData()) {
            speed = snapshotState.speed();
            addElement(variables.get("S_PWR"), time, snapshotState.power());
            addElement(variables.get("S_SPD"), time, speed);
        }
        repo.flushElements();
        
        if (Double.isNaN(speed))
            return false;
        return speed > 1.0;
    }

    private String prefKey(String key) { return vehicle.getVIN()+"_"+key; }
    
    
/*------------------------------------------------------------------------------
 *
 * PRIVATE - Utility methods for exporting data to an Excel file
 * 
 *----------------------------------------------------------------------------*/

    private void createValueMap() {
        valueMap = new HashMap<>();
        
        int i = 0;
        Set<Variable> sorted = new TreeSet<>(variables.set());
        for (Variable v : sorted) {
            valueMap.put(v.type, i);
            i++;
        }

    }
    
    private TreeMap<Long,Double[]> collectIntoTable(Map<String,Integer> typeToIndex) {
        TreeMap<Long,Double[]> table = new TreeMap<>();
        int nVars = variables.set().size();
        for (Variable v : variables.set()) {
            int valueIndex = typeToIndex.get(v.type);
            for (Data<Number, Number> data : v.seriesData) {
                long timeIndex = data.getXValue().longValue();
                Double[] vals = table.get(timeIndex);
                if (vals == null) {
                    vals = new Double[nVars];
                    for (int i = 0; i < nVars; i++) { vals[i] = Double.NaN; }
                    table.put(timeIndex, vals);
                }
                vals[valueIndex] = data.getYValue().doubleValue();
            }
        }
        return table;
    }
    

/*------------------------------------------------------------------------------
 *
 * This section has the code pertaining to the background thread that
 * is responsible for collecting stats on a regular basis
 * 
 *----------------------------------------------------------------------------*/
    
    private static Thread refreshThread = null;
    private static boolean collectionPaused = true;
    
    private void pauseRefresh() { collectionPaused = true; }
    
    private void ensureRefreshThread() {
        collectionPaused = false;
        if (refreshThread == null) {
            refreshThread = appContext.launchThread(new AutoCollect(), "00 AutoCollect");
        }
    }

    private class AutoCollect implements Runnable, ChangeListener<Boolean> {
        private boolean asleep = false;
        
        @Override public void 
        changed(ObservableValue<? extends Boolean> o, Boolean ov, Boolean nv) {
            asleep = nv;
        }
        
        @Override public void run() {
            appContext.shouldBeSleeping.addListener(this);
            asleep = appContext.shouldBeSleeping.get();
            long collectionInterval = DefaultInterval;
            while (true) {
                if (!collectionPaused && !asleep) {
                    Boolean inMotion = getAndRecordStats();
                    if (inMotion != null) {
                        if (inMotion)
                            collectionInterval = MinInterval;
                        else
                            collectionInterval = (long)Math.min(
                                collectionInterval*1.5, MaxInterval);
                    }
                }
                Utils.sleep(collectionInterval);
                if (appContext.shuttingDown.get())
                    return;
            }
        }
    }

}
