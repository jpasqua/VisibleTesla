/*
 * GraphController.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Jul 22, 2013
 */
package org.noroomattheinn.visibletesla;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Range;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
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
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
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
import org.noroomattheinn.visibletesla.AppContext.InactivityType;
import org.noroomattheinn.visibletesla.StatsRepository.Recorder;
import org.noroomattheinn.visibletesla.chart.VTLineChart;
import org.noroomattheinn.visibletesla.chart.TimeBasedChart;
import org.noroomattheinn.visibletesla.chart.VTSeries;

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

 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class GraphController extends BaseController {

/*------------------------------------------------------------------------------
 *
 * Constants and Enums
 * 
 *----------------------------------------------------------------------------*/
    
    public enum LoadPeriod {Last7, Last14, Last30, ThisWeek, ThisMonth, All, None};
    
    public static final BiMap<String,LoadPeriod> nameToLoadPeriod = HashBiMap.create();
    static {
        nameToLoadPeriod.put("Last 7 days", LoadPeriod.Last7);
        nameToLoadPeriod.put("Last 14 days", LoadPeriod.Last14);
        nameToLoadPeriod.put("Last 30 days", LoadPeriod.Last30);
        nameToLoadPeriod.put("This week", LoadPeriod.ThisWeek);
        nameToLoadPeriod.put("This month", LoadPeriod.ThisMonth);
        nameToLoadPeriod.put("All", LoadPeriod.All);
        nameToLoadPeriod.put("None", LoadPeriod.None);
    }
    
    private static final long DefaultInterval = 2 * 60 * 1000;  // 2 Minutes
    private static final long MinInterval =         30 * 1000;  // 30 Seconds
    
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private ChargeState chargeState;
    private SnapshotState snapshotState;
    private StatsRepository repo;
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
    @FXML private ProgressBar lpb1;
    @FXML private ProgressBar lpb2;
    @FXML private HBox progressPane;
    
    private RadioMenuItem displayLinesMI;
    private RadioMenuItem displayMarkersMI;
    private RadioMenuItem displayBothMI;
    private TimeBasedChart chart;
    private VTLineChart lineChart = null;
    private boolean allDataLoaded = false;
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    void exportCSV() {
        if (!allDataLoaded) {
            Dialogs.showWarningDialog(
                    appContext.stage,
                    "Your graph data hasn't been loaded yet.\n"
                    + "Please select the Graphs tab then try again",
                    "Graph Data Export Process", "Not Ready for Export");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Graph Data");
        fileChooser.setInitialDirectory(new File(System.getProperty("user.home")));

        final File file = fileChooser.showSaveDialog(appContext.stage);
        if (file == null) {
            return;
        }

        if (valueMap == null) {
            createValueMap();
        }

        TreeMap<Long, Double[]> table = collectIntoTable(valueMap);

        try {
            WritableWorkbook workbook = Workbook.createWorkbook(file);
            WritableSheet sheet = workbook.createSheet("Sheet1", 0);

            int row = 0;

            // Add the header row
            jxl.write.Label label = new jxl.write.Label(0, row, "TIMESTAMP");
            sheet.addCell(label);
            sheet.setColumnView(0, 12); // Big enough for a timestamp;
            for (Map.Entry<String, Integer> entry : valueMap.entrySet()) {
                String type = entry.getKey();
                int column = entry.getValue();
                label = new jxl.write.Label(column + 1, row, type);
                sheet.addCell(label);
            }
            sheet.setColumnView(valueMap.size() + 1, 16); // Big enough for a Date string;

            jxl.write.WritableCellFormat dateFormat = new jxl.write.WritableCellFormat(
                    new jxl.write.DateFormat("M/d/yy H:mm:ss"));

            // Run through the table and add each row...
            for (Map.Entry<Long, Double[]> entry : table.entrySet()) {
                row++;
                long time = entry.getKey().longValue();
                jxl.write.Number timeCell = new jxl.write.Number(0, row, time);
                sheet.addCell(timeCell);

                Double[] vals = entry.getValue();
                for (int i = 0; i < vals.length; i++) {
                    double val = vals[i];
                    if (!Double.isNaN(val)) {
                        jxl.write.Number valueCell = new jxl.write.Number(i + 1, row, val);
                        sheet.addCell(valueCell);
                    }
                }
                // Create a column which converts the UNIX timestamp to a Date
                TimeZone tz = Calendar.getInstance().getTimeZone();
                long offset = (tz.getOffset(System.currentTimeMillis())) / (1000 * 60 * 60);
                String dateFormula = String.format("(A%d/86400)+25569+(%d/24)", row + 1, offset);
                jxl.write.Formula f = new jxl.write.Formula(vals.length + 1, row, dateFormula, dateFormat);
                sheet.addCell(f);
            }
            workbook.write();
            workbook.close();

            Dialogs.showInformationDialog(
                    appContext.stage, "Your data has been exported",
                    "Graph Data Export Process" , "Export Complete");
        } catch (IOException | WriteException ex) {
            Dialogs.showErrorDialog(
                    appContext.stage, "Unable to save to: " + file,
                    "Graph Data Export Process" , "Export Failed");
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
        CheckBox cb = (CheckBox) event.getSource();
        boolean visible = cb.isSelected();
        VTSeries series = cbToSeries.get(cb);
        lineChart.setVisible(series, visible);
        lineChart.refreshChart();

        // Remember the value for next time we start up
        appContext.prefs.putBoolean(prefKey(series.getName()), visible);
    }

/*------------------------------------------------------------------------------
 *
 * VTSeries Handling
 * 
 *----------------------------------------------------------------------------*/
    
    private Map<CheckBox,VTSeries> cbToSeries = new LinkedHashMap<>(); // Preserves insertion order
    private Map<String,VTSeries> typeToSeries = new HashMap<>();
    
    private void prepSeries() {
        GUIState gs = appContext.cachedGUIState;
        VTSeries.Transform<Number> distTransform = gs.distanceUnits().startsWith("mi")
                ? VTSeries.idTransform : VTSeries.mToKTransform;
        lineChart.clearSeries();

        cbToSeries.put(voltageCheckbox, lineChart.register(
                new VTSeries("C_VLT", VTSeries.millisToSeconds, VTSeries.idTransform)));
        cbToSeries.put(currentCheckbox, lineChart.register(
                new VTSeries("C_AMP", VTSeries.millisToSeconds, VTSeries.idTransform)));
        cbToSeries.put(rangeCheckbox, lineChart.register(
                new VTSeries("C_EST", VTSeries.millisToSeconds, distTransform)));
        cbToSeries.put(socCheckbox, lineChart.register(
                new VTSeries("C_SOC", VTSeries.millisToSeconds, VTSeries.idTransform)));
        cbToSeries.put(rocCheckbox, lineChart.register(
                new VTSeries("C_ROC", VTSeries.millisToSeconds, distTransform)));
        cbToSeries.put(powerCheckbox, lineChart.register(
                new VTSeries("S_PWR", VTSeries.millisToSeconds, VTSeries.idTransform)));
        cbToSeries.put(speedCheckbox, lineChart.register(
                new VTSeries("S_SPD", VTSeries.millisToSeconds, distTransform)));
        cbToSeries.put(batteryCurrentCheckbox, lineChart.register(
                new VTSeries("C_BAM", VTSeries.millisToSeconds, VTSeries.idTransform)));
        
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
            boolean selected = appContext.prefs.getBoolean(prefKey(s.getName()), true);
            cb.setSelected(selected);
            lineChart.setVisible(s, selected);
        }

        // Restore the last display settings (display lines, markers, or both)
        displayLines = appContext.prefs.getBoolean(prefKey("DISPLAY_LINES"), true);
        displayMarkers = appContext.prefs.getBoolean(prefKey("DISPLAY_MARKERS"), true);

        reflectDisplayOptions();
    }

/*------------------------------------------------------------------------------
 *
 * Methods overridden from BaseController
 * 
 *----------------------------------------------------------------------------*/
    
    @Override protected void prepForVehicle(Vehicle v) {
        if (differentVehicle(chargeState, v)) {
            chargeState = new ChargeState(v);
            snapshotState = new SnapshotState(v);

            pauseRefresh();
            prepSeries();
            if (repo != null) {
                repo.close();
            }
            repo = new StatsRepository(appContext.appFilesFolder, v.getVIN());
            loadExistingData(getLoadPeriod());
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

            appContext.prefs.putBoolean(prefKey("DISPLAY_LINES"), displayLines);
            appContext.prefs.putBoolean(prefKey("DISPLAY_MARKERS"), displayMarkers);
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
 * PRIVATE - Utility Methods for storing stats in memory and on disk
 * 
 *----------------------------------------------------------------------------*/
    
    private void addElement(VTSeries series, long time, double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            value = 0;
        }
        // Round to 1 decimal place. The rest isn't really significant
        value = Math.round(value * 10.0) / 10.0;
        recordLater(series, time, value);
        repo.storeElement(series.getName(), time, value);
    }
    
/*------------------------------------------------------------------------------
 *
 * PRIVATE - Loading existing data
 * 
 *----------------------------------------------------------------------------*/
        
    List<Entries> toProcess = new ArrayList<>();
    Map<String,Entries> collector = new HashMap<>();
    
    private class Entries {
        String type;
        List<Number> times = new ArrayList<>();
        List<Number> values = new ArrayList<>();
        Entries(String type) { this.type = type; }
        void addEntry(long time, double value) {
            times.add(time);
            values.add(value);
        }
    }
    
    private void loadExistingData(Range<Long> period) {
        lineChart.applySeriesToChart();
        restoreLastSettings();
        
        final int batchSize = appContext.thePrefs.incrementalLoad.get() ? 250 : 50000;

        repo.loadExistingData(new Recorder() {
            @Override public void recordElement(long time, String type, double val) {
                Entries e = collector.get(type);
                if (e == null) {e = new Entries(type); collector.put(type, e); }
                e.addEntry(time, val);
                if (e.times.size() > batchSize) {
                    toProcess.add(e);
                    collector.remove(type);
                }
            }
        }, period);
        for (Entries e : collector.values()) toProcess.add(e);
        collector.clear();
                
        ensureRefreshThread();  // If thread already exists, it unpauses

        // Load up the data
        Runnable trickler = new Runnable() {
            
            private void prepProgressBars(double ratio) {
                progressPane.setVisible(true);
                double progressPaneWidth = progressPane.getWidth();
                double newlb1 = progressPaneWidth * ratio;
                double newlb2 = progressPaneWidth - newlb1;
                lpb1.setPrefWidth(newlb1);
                lpb2.setPrefWidth(newlb2);
                lpb1.setProgress(0.0); lpb2.setProgress(0.0);
            }
            
            private void doAdd(
                    final Entries e, final double progress, final ProgressBar pb) {
                final VTSeries series = typeToSeries.get(e.type);
                Platform.runLater(new Runnable() {
                    @Override public void run() {
                        series.addAll(e.times, e.values, true);
                        pb.setProgress(progress);
                } });
                Utils.yieldFor(75);
            }

            @Override public void run() {
                int totalChunksToProcess = toProcess.size();
                List<Entries> secondary = new ArrayList<>();
                Iterator<Entries> iterator = toProcess.iterator();
                while (iterator.hasNext()) {
                    Entries e = iterator.next();
                    if (!lineChart.isVisible(typeToSeries.get(e.type))) {
                        secondary.add(e);
                        iterator.remove();
                    }
                }

                prepProgressBars(((double)toProcess.size())/totalChunksToProcess);

                double nProcessed = 1;
                for (int i = toProcess.size()-1; i >= 0; i--) {
                    final double pct = nProcessed/toProcess.size();
                    doAdd(toProcess.get(i), pct, lpb1);
                    nProcessed++;
                }

                nProcessed = 0;
                for (int i = secondary.size()-1; i >= 0; i--) {
                    final double pct = nProcessed/secondary.size();
                    doAdd(secondary.get(i), pct, lpb2);
                    nProcessed++;
                }
                
                // Queue this up to be performed when the loading finishes
                Platform.runLater(new Runnable() {
                    @Override public void run() {
                        allDataLoaded = true;
                        progressPane.setVisible(false);
                        toProcess = new ArrayList<>();
                        collector = new HashMap<>();
                    }
                });
            }
        };
        appContext.launchThread(trickler, "00 - VT Trickle");
    }
    
    void recordLater(final VTSeries series, final long time, final double value) {
        // This can be called from a background thread, hence the Platform.runLater()
        Platform.runLater(new Runnable() {
            @Override public void run() { series.addToSeries(time, value, false); } });
    }

    /**
     * Called by the background thread to gather and record a new set of samples
     *
     * @return Boolean(true) if the vehicle is in motion Boolean(false) if it's
     * not null if we don't know
     */
    private Boolean getAndRecordStats() {
        chargeState.refresh();
        snapshotState.refresh();
        double speed = Double.NaN;

        long time = System.currentTimeMillis();
        if (chargeState.hasValidData()) {
            addElement(typeToSeries.get("C_VLT"), time, chargeState.chargerVoltage());
            addElement(typeToSeries.get("C_AMP"), time, chargeState.chargerActualCurrent());
            addElement(typeToSeries.get("C_EST"), time, chargeState.range());
            addElement(typeToSeries.get("C_SOC"), time, chargeState.batteryPercent());
            addElement(typeToSeries.get("C_ROC"), time, chargeState.chargeRate());
            addElement(typeToSeries.get("C_BAM"), time, chargeState.batteryCurrent());
        }
        if (snapshotState.hasValidData()) {
            speed = snapshotState.speed();
            addElement(typeToSeries.get("S_PWR"), time, snapshotState.power());
            addElement(typeToSeries.get("S_SPD"), time, speed);
        }
        repo.flushElements();

        return Double.isNaN(speed) ? false : (speed > 1.0);
    }

    private Range<Long> getLoadPeriod() {
        Range<Long> loadPeriod = Range.closed(Long.MIN_VALUE, Long.MAX_VALUE);

        long now = System.currentTimeMillis();
        LoadPeriod period = nameToLoadPeriod.get(appContext.thePrefs.loadPeriod.get());
        if (period == null) {
            period = LoadPeriod.All;
            appContext.thePrefs.loadPeriod.set(nameToLoadPeriod.inverse().get(period));
        }
        switch (period) {
            case None:
                loadPeriod = Range.closed(now + 1000, now + 1000L); // Empty Range
                break;
            case Last7:
                loadPeriod = Range.closed(now - (7 * 24 * 60 * 60 * 1000L), now);
                break;
            case Last14:
                loadPeriod = Range.closed(now - (14 * 24 * 60 * 60 * 1000L), now);
                break;
            case Last30:
                loadPeriod = Range.closed(now - (30 * 24 * 60 * 60 * 1000L), now);
                break;
            case ThisWeek:
                Range<Date> thisWeek = getThisWeek();
                loadPeriod = Range.closed(
                        thisWeek.lowerEndpoint().getTime(),
                        thisWeek.upperEndpoint().getTime());
                break;
            case ThisMonth:
                Range<Date> thisMonth = getThisMonth();
                loadPeriod = Range.closed(
                        thisMonth.lowerEndpoint().getTime(),
                        thisMonth.upperEndpoint().getTime());
                break;
            case All:
            default:
                break;

        }
        return loadPeriod;
    }

    private Range<Date> getThisWeek() {
        return getDateRange(Calendar.DAY_OF_WEEK);
    }
    
    private Range<Date> getThisMonth() {
        return getDateRange(Calendar.DATE);
    }
    
    private Range<Date> getDateRange(int dateField) {
        Calendar cal = Calendar.getInstance();
        cal.set(dateField, 1);
        Date start = cal.getTime();
        cal.set(dateField, cal.getActualMaximum(dateField));
        Date end = cal.getTime();
        return Range.closed(start, end);
    }
    
/*------------------------------------------------------------------------------
 *
 * PRIVATE - Utility methods for exporting data to an Excel file
 * 
 *----------------------------------------------------------------------------*/
    
    private void createValueMap() {
        valueMap = new HashMap<>();

        int i = 0;
        Set<VTSeries> sorted = new TreeSet<>(lineChart.set());
        for (VTSeries v : sorted) {
            valueMap.put(v.getName(), i++);
        }

    }

    private TreeMap<Long, Double[]> collectIntoTable(Map<String, Integer> typeToIndex) {
        TreeMap<Long, Double[]> table = new TreeMap<>();
        int nSeries = lineChart.set().size();
        for (VTSeries s : lineChart.set()) {
            int valueIndex = typeToIndex.get(s.getName());
            for (Data<Number,Number> data : s.getSeries().getData()) {
                long timeIndex = data.getXValue().longValue();
                Double[] vals = table.get(timeIndex);
                if (vals == null) {
                    vals = new Double[nSeries];
                    for (int i = 0; i < nSeries; i++) {
                        vals[i] = Double.NaN;
                    }
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

    private void pauseRefresh() {
        collectionPaused = true;
    }

    private void ensureRefreshThread() {
        collectionPaused = false;
        if (refreshThread == null) {
            refreshThread = appContext.launchThread(new AutoCollect(), "00 AutoCollect");
        }
    }

    private class AutoCollect implements Runnable, ChangeListener<InactivityType> {
        private InactivityType inactivityState = AppContext.InactivityType.Awake;

        @Override
        public void changed(ObservableValue<? extends InactivityType> o, InactivityType ov, InactivityType nv) {
            inactivityState = nv;
        }

        @Override public void run() {
            appContext.inactivityState.addListener(this);
            inactivityState = appContext.inactivityState.get();
            long collectionInterval = DefaultInterval;
            int decay = 0;
            while (true) {
                if (!collectionPaused && inactivityState != InactivityType.Sleep) {
                    Boolean inMotion = getAndRecordStats();
                    if (inMotion != null) {
                        if (inMotion) {
                            decay = 3;
                            collectionInterval = MinInterval;
                        } else {
                            decay = Math.max(decay - 1, 0);
                            collectionInterval = decay > 0 ? MinInterval : DefaultInterval;
                        }
                    }
                }
                Utils.sleep(collectionInterval);
                if (appContext.shuttingDown.get()) {
                    return;
                }
            }
        }

    }
    
}
