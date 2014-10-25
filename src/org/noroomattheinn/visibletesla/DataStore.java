/*
 * DataStore.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Nov 29, 2013
 */
package org.noroomattheinn.visibletesla;

import org.noroomattheinn.visibletesla.dialogs.DateRangeDialog;
import com.google.common.collect.Range;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListMap;
import javafx.scene.control.Dialogs;
import javafx.stage.FileChooser;
import jxl.Workbook;
import jxl.write.WritableSheet;
import jxl.write.WritableWorkbook;
import jxl.write.WriteException;
import org.noroomattheinn.visibletesla.stats.Stat;
import org.noroomattheinn.visibletesla.stats.StatsPublisher;
import org.noroomattheinn.visibletesla.stats.StatsRepository;

/**
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public abstract class DataStore implements StatsPublisher {

/*------------------------------------------------------------------------------
 *
 * Constants and Enums
 * 
 *----------------------------------------------------------------------------*/
    public static String LastExportDirKey = "APP_LAST_EXPORT_DIR";
    
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    protected AppContext appContext;
    protected StatsRepository repo;
    protected NavigableMap<Long,Map<String,Double>> rows = new ConcurrentSkipListMap<>();
    protected boolean dataLoaded = false;
    protected String[] publishedKeys;
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public DataStore(AppContext appContext, File locationFile, String[] keys)
            throws IOException {
        this.appContext = appContext;
        this.repo = new StatsRepository(locationFile);
        this.publishedKeys = keys;
    }
    
    public void close() {
        repo.close();
        rows = null;
        dataLoaded = false;
    }
    
    public StatsRepository getRepo() { return repo; }
    
    public final void load(Range<Long> period) {
        appContext.tm.launch(new Loader(period), "DataLoader");
    }
    
    public final NavigableMap<Long,Map<String,Double>> getData() {
        return dataLoaded ? rows : null;
    }
    
    public void exportCSV() {
        if (!dataLoaded) {
            Dialogs.showWarningDialog(
                    appContext.stage,
                    "Your data hasn't completed loading yet.\n" +
                    "Please try again momentarily",
                    "Data Export Process", "Not Ready for Export");
            return;
        }
        
        String initialDir = appContext.persistentState.get(
                LastExportDirKey, System.getProperty("user.home"));
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Data");
        fileChooser.setInitialDirectory(new File(initialDir));

        File file = fileChooser.showSaveDialog(appContext.stage);
        if (file != null) {
            String enclosingDirectory = file.getParent();
            if (enclosingDirectory != null)
                appContext.persistentState.put(LastExportDirKey, enclosingDirectory);
            Range<Long> exportPeriod = DateRangeDialog.getExportPeriod(appContext.stage);
            if (exportPeriod == null)
                return;
            doExport(file, exportPeriod);
        }
    }
    
/*------------------------------------------------------------------------------
 *
 * Implementation of the StatsPublisher interface
 * 
 *----------------------------------------------------------------------------*/
    
    @Override public List<Stat.Sample> valuesForRange(String type, long start, long end) {
        Long low = rows.floorKey(start);
        Long high = rows.ceilingKey(end);
        List<Stat.Sample> emptyList = new ArrayList<>(1);
        emptyList.add(new Stat.Sample(start, 0.0));
        
        if (low == null || high == null) return emptyList;
        
        NavigableMap<Long, Map<String, Double>> subMap = rows.subMap(low, true, high, true);
        if (subMap == null || subMap.size() == 0) return emptyList;
        
        List<Stat.Sample> results = new ArrayList<>(subMap.size());
        for (Map.Entry<Long, Map<String, Double>> e : subMap.entrySet()) {
            long time = e.getKey();
            Double val = e.getValue().get(type);
            if (val != null)
                results.add(new Stat.Sample(time, val));
        }
        return (results.isEmpty())  ? emptyList : results;
    }
        
    @Override public List<String> getStatTypes() {
       return  Arrays.asList(publishedKeys);
    }
    
/*------------------------------------------------------------------------------
 *
 * PRIVATE - Methods related to loading existing samples
 * 
 *----------------------------------------------------------------------------*/
    
    private final Set<String> keysEncountered = new TreeSet<>();   // Keep them sorted
    
    private void addToLiveData(long time, String type, double val) {
        if (rows == null) return;   // We're still starting up or we're shutting down!
        Map<String,Double> row = rows.get(time);
        if (row == null) {
            row = new HashMap<>();
            rows.put(time, row);
        }
        row.put(type, val);
        keysEncountered.add(type);
    }
    
    protected void storeItem(String type, long timestamp, double value) {
        repo.storeElement(type, timestamp, value);
        addToLiveData(timestamp, type, value);
    }
    
    private class Loader implements Runnable {
        Range<Long>period;
        
        Loader(Range<Long> period) { this.period = period; }
        
        @Override public void run() {
            repo.loadExistingData(new StatsRepository.Recorder() {
                @Override public void recordElement(long time, String type, double val) {
                    addToLiveData(time, type, val);
                }
            }, period);
            
            dataLoaded = true;
        }
    }

/*------------------------------------------------------------------------------
 *
 * PRIVATE - Utility Methods
 * 
 *----------------------------------------------------------------------------*/
    
    private void doExport(File file, Range<Long> exportPeriod) {
        NavigableMap<Long,Map<String,Double>> rowsForExport = loadForExport(exportPeriod);
        int columnNumberForExport = 0;
        Map<String, Integer> keyToColumn = new HashMap<>();
        for (String key : keysEncountered) { keyToColumn.put(key, columnNumberForExport++); }

        
        try {
            WritableWorkbook workbook = Workbook.createWorkbook(file);
            WritableSheet sheet = workbook.createSheet("Sheet1", 0);

            int nDataColumns = keyToColumn.size();
            
            int column;
            jxl.write.WritableCellFormat dateFormat = new jxl.write.WritableCellFormat(
                    new jxl.write.DateFormat("M/d/yy H:mm:ss"));
            TimeZone tz = Calendar.getInstance().getTimeZone();
            long offset = (tz.getOffset(System.currentTimeMillis())) / (1000 * 60 * 60);
            
            addTableHeader(sheet, keyToColumn);
            

            // Run through the table and add each row...
            int rowNum = 1;
            for (Map.Entry<Long,Map<String,Double>> row : rowsForExport.entrySet()) {
                long time = row.getKey()/1000;
                Map<String,Double> values = row.getValue();
                
                jxl.write.Number timeCell = new jxl.write.Number(0, rowNum, time);
                sheet.addCell(timeCell);

                for (Map.Entry<String,Integer> entry : keyToColumn.entrySet()) {
                    String key = entry.getKey();
                    column = entry.getValue()+1;
                    Double value = values.get(key);
                    if (value != null) {
                        sheet.addCell(new jxl.write.Number(column, rowNum, value));
                    }
                }

                // Create a column which converts the UNIX timestamp to a Date
                String dateFormula = 
                        String.format("(A%d/86400)+25569+(%d/24)", rowNum + 1, offset);
                jxl.write.Formula f = 
                        new jxl.write.Formula(nDataColumns+1, rowNum, dateFormula, dateFormat);
                sheet.addCell(f);
                
                rowNum++;
            }
            workbook.write();
            workbook.close();

            Dialogs.showInformationDialog(
                    appContext.stage, "Your data has been exported",
                    "Data Export Process" , "Export Complete");
        } catch (IOException | WriteException ex) {
            Dialogs.showErrorDialog(
                    appContext.stage, "Unable to save to: " + file,
                    "Data Export Process" , "Export Failed");
        }
        
    }
    
    private void addTableHeader(WritableSheet sheet, Map<String,Integer> keyToColumn)
            throws WriteException {
            // Starting with the timestamp column
            jxl.write.Label label = new jxl.write.Label(0, 0, "TIMESTAMP");
            sheet.addCell(label);
            sheet.setColumnView(0, 12); // Big enough for a timestamp;
            
            // Now handle the data columns
            for (Map.Entry<String,Integer> entry : keyToColumn.entrySet()) {
                String key = entry.getKey();
                int column = entry.getValue();
                label = new jxl.write.Label(column+1, 0, key);
                sheet.addCell(label);
            }
            
            // Now add the synthetic Date column
            int dateColumn = keyToColumn.size()+1;
            label = new jxl.write.Label(dateColumn, 0, "Date");
            sheet.addCell(label);
            sheet.setColumnView(dateColumn, 16); // Big enough for a Date string;
            
            // Make the header row stationary
            sheet.getSettings().setVerticalFreeze(1);
    }
    
    private NavigableMap<Long,Map<String,Double>> loadForExport(Range<Long> exportPeriod) {
        final NavigableMap<Long,Map<String,Double>> rowsToExport = new ConcurrentSkipListMap<>();
        
        repo.loadExistingData(new StatsRepository.Recorder() {
            @Override public void recordElement(long time, String type, double val) {
                Map<String, Double> row = rowsToExport.get(time);
                if (row == null) {
                    row = new HashMap<>();
                    rowsToExport.put(time, row);
                }
                row.put(type, val);
                keysEncountered.add(type);
            }
        }, exportPeriod);
        return rowsToExport;
    }
}
