/*
 * SchedulerController.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Sep 7, 2013
 */

package org.noroomattheinn.visibletesla;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.prefs.Preferences;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import org.noroomattheinn.tesla.ChargeState;
import org.noroomattheinn.tesla.Result;
import org.noroomattheinn.tesla.Tesla;
import org.noroomattheinn.tesla.Vehicle;

/**
 * FXML Controller class
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class SchedulerController extends BaseController implements ScheduleItem.ScheduleOwner {
    private static final int Safe_Threshold = 25;
    
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/

    @FXML private GridPane gridPane;
    @FXML private TextArea activityLog;
    
    private ChargeState chargeState;
    private org.noroomattheinn.tesla.ChargeController chargeController;
    private org.noroomattheinn.tesla.HVACController hvacController;

    private List<ScheduleItem> schedulers = new ArrayList<>();
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/

    public void shutDown() {
        for (ScheduleItem si : schedulers) {
            si.shutDown();
        }
    }

/*------------------------------------------------------------------------------
 *
 * Implementation of the ScheduleOwner interface
 * 
 *----------------------------------------------------------------------------*/
    
    @Override public String getExternalKey() { return vehicle.getVIN(); }
    @Override public Preferences getPreferences() { return appContext.prefs; }
    
    @Override public void runCommand(ScheduleItem.Command command, boolean minCharge) {
        // Only apply minCharge to commands that turn things on. Turning things
        // off or starting charging doesn't require a minCharge
        if (minCharge && (command == ScheduleItem.Command.HVAC_ON)) {
            chargeState.refresh();
            if (!chargeState.hasValidData() || chargeState.batteryPercent() < Safe_Threshold) {
                logActivity("Insufficient charge to execute command: " + command);
            }
        }        
        
        Result r = Result.Failed;
        switch (command) {
            case CHARGE_ON: r = chargeController.startCharing(); break;
            case CHARGE_OFF: r = chargeController.stopCharing(); break;
            case HVAC_ON: r = hvacController.startAC(); break;
            case HVAC_OFF: r = hvacController.stopAC(); break;
        }
        String entry = String.format(
                "%s: %s", ScheduleItem.commandToName(command),
                r.success ? "succeeded" : "failed");
        if (!r.success) entry = entry + ", " + r.explanation;
        logActivity(entry);
    }
    

/*------------------------------------------------------------------------------
 *
 * PRIVATE - Loading the UI Elements
 * 
 *----------------------------------------------------------------------------*/

    private void prepareSchedulerUI(GridPane gridPane) {
        Map<Integer,Map<Integer,Node>> rows = loadFromGrid(gridPane);
        
        for (Map.Entry<Integer, Map<Integer, Node>> rowEntry : rows.entrySet()) {
            int rowNum = rowEntry.getKey().intValue();
            Map<Integer, Node> row = rowEntry.getValue();
            schedulers.add(new ScheduleItem(rowNum, row, this));
        }
    }
    
    private Map<Integer,Map<Integer,Node>> loadFromGrid(GridPane gp) {
        Map<Integer,Map<Integer,Node>> rowMap = new HashMap<>();
        ObservableList<Node> kids = gp.getChildren();
        
        for (Node kid : kids) {
            ObservableMap<Object,Object> props = kid.getProperties();
            int columnNumber = getRowOrColumn(kid, false);
            int rowNumber = getRowOrColumn(kid, true);
            if (rowNumber <= 0)
                continue;   // Row 0 is the header, -1 isn't in the grid
            rowNumber--;
            Map<Integer,Node> thisRow = rowMap.get(rowNumber);
            if (thisRow == null) {
                thisRow = new HashMap<>();
                rowMap.put(rowNumber, thisRow);                
            }
            thisRow.put(columnNumber, kid);
        }
        return rowMap;
    }
    
    private int getRowOrColumn(Node node, boolean getRow) {
        ObservableMap<Object,Object> props = node.getProperties();
        String propName = getRow ? "gridpane-row" : "gridpane-column";
        Number prop = ((Number)props.get(propName));
        return (prop == null) ? -1 : prop.intValue();
    }

/*------------------------------------------------------------------------------
 *
 * Methods overridden from BaseController
 * 
 *----------------------------------------------------------------------------*/
    
    @Override
    protected void fxInitialize() {
        // Deep-Six the refresh button and progress indicator
        refreshButton.setDisable(true);
        refreshButton.setVisible(false);
        progressIndicator.setVisible(false);
        progressLabel.setVisible(false);

        prepareSchedulerUI(gridPane);
    }

    @Override
    protected void prepForVehicle(Vehicle v) {
        if (differentVehicle(chargeController, v)) {
            chargeState = new ChargeState(v);
            chargeController = new org.noroomattheinn.tesla.ChargeController(v);
            hvacController = new org.noroomattheinn.tesla.HVACController(v);
        }
        
        for (ScheduleItem item : schedulers) { item.loadExistingSchedule(); }
    }
    
    @Override
    protected void refresh() {
    }

    @Override
    protected void reflectNewState() {
    }

    
/*------------------------------------------------------------------------------
 *
 * PRIVATE - Utility Methods
 * 
 *----------------------------------------------------------------------------*/
    
    private void logActivity(String entry) {
        Date now = new Date();
        String previousEntries = activityLog.getText();
        String datedEntry = String.format(
            "[%1$tm/%1$td/%1$ty %1$tH:%1$tM] %2$s\n%3$s", now, entry, previousEntries);
        activityLog.setText(datedEntry);
        Tesla.logger.log(Level.FINE, entry);
    }
    

}

