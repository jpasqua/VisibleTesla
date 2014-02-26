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
import org.noroomattheinn.tesla.ActionController;
import org.noroomattheinn.tesla.ChargeState;
import org.noroomattheinn.tesla.Result;
import org.noroomattheinn.tesla.Tesla;
import org.noroomattheinn.tesla.Vehicle;
import org.noroomattheinn.utils.Utils;
import org.noroomattheinn.visibletesla.AppContext.InactivityType;
import org.noroomattheinn.visibletesla.ScheduleItem.Command;

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
    
    private ChargeState charge;
    private org.noroomattheinn.tesla.ChargeController chargeController;
    private org.noroomattheinn.tesla.HVACController hvacController;

    private final List<ScheduleItem> schedulers = new ArrayList<>();
    private int maxCharge, stdCharge;
    
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
    @Override public Preferences getPreferences() { return appContext.persistentState; }
    @Override public AppContext getAppContext() { return appContext; }
    
    @Override public void runCommand(ScheduleItem.Command command, double value) {
        if (command != ScheduleItem.Command.SLEEP) {
            if (!wakeAndGetChargeState()) {
                logActivity("Can't wake vehicle - aborting");
                return;
            }
        }
        if (!safeToRun(command)) return;
        
        if (!tryCommand(command, value)) {
            tryCommand(command, value);  // Try it again in case of transient errors
        }
    }
    
    private boolean tryCommand(ScheduleItem.Command command, double value) {
        String name = ScheduleItem.commandToName(command);
        Result r = Result.Succeeded;
        switch (command) {
            case CHARGE_SET:
            case CHARGE_ON:
                if (value > 0) {
                    r = chargeController.setChargePercent((int)value);
                    if (!(r.success || r.explanation.equals("already_set"))) {
                        logActivity("Unable to set charge target: " + r.explanation);
                    }
                }
                if (command == Command.CHARGE_ON)
                    r = chargeController.startCharing();
                break;
            case CHARGE_OFF: r = chargeController.stopCharing(); break;
            case HVAC_ON:
                if (value > 0) {    // Set the target temp first
                    if (appContext.lastKnownGUIState.get().temperatureUnits.equalsIgnoreCase("F"))
                        r = hvacController.setTempF(value, value);
                    else
                        r = hvacController.setTempC(value, value);
                    if (!r.success) break;
                }
                r = hvacController.startAC();
                break;
            case HVAC_OFF: r = hvacController.stopAC();break;
            case AWAKE: appContext.requestInactivityMode(InactivityType.Awake); break;
            case SLEEP: appContext.requestInactivityMode(InactivityType.Sleep); break;
            case DAYDREAM: appContext.requestInactivityMode(InactivityType.Daydream); break;
            case UNPLUGGED: r = unpluggedTrigger(); break;
        }
        if (value > 0) name = String.format("%s (%3.1f)", name, value);
        String entry = String.format("%s: %s", name, r.success ? "succeeded" : "failed");
        if (!r.success) entry = entry + ", " + r.explanation;
        logActivity(entry);
        return r.success;
    }
    
    private boolean safeToRun(ScheduleItem.Command command) {
        String name = ScheduleItem.commandToName(command);
        if (command == ScheduleItem.Command.HVAC_ON) {
            if (appContext.prefs.safeIncludesMinCharge.get() &&
                charge.state.batteryPercent < Safe_Threshold) {
                String entry = String.format(
                        "%s: Insufficient charge - aborted", name);
                logActivity(entry);
                return false;
            }
            
            if (appContext.prefs.safeIncludesPluggedIn.get()) {
                if (charge.state.chargerPilotCurrent < 1) {
                    charge.refresh();  // Be double sure!
                    if (charge.state.chargerPilotCurrent < 1) {
                        String entry = String.format(
                                "%s: Vehicle not plugged in - aborted", name);
                        logActivity(entry);
                        return false;
                    }
                }
            }
        }
        return true;
    }
    
    private boolean wakeAndGetChargeState() {
        appContext.inactivityState.set(InactivityType.Awake);
        if (charge.refresh()) return true;
        
        ActionController a = new ActionController(vehicle);
        for (int i = 0; i < 20; i++) {
            a.wakeUp();
            if (charge.refresh())
                return true;
            Utils.sleep(5000);
        }
        return false;
    }
    
    private synchronized Result unpluggedTrigger() {
        if (charge.state.chargerPilotCurrent < 1) {
            // Unfortunately the charge state can be flakey. It might read a pilot
            // current of 0 first, then change to the proper value. For that reason,
            // fetch the charge state again and double check.
            Utils.sleep(1000);
            if (charge.refresh() && charge.state.chargerPilotCurrent < 1) {
                appContext.sendNotification(
                    appContext.prefs.notificationAddress.get(),
                    "Your car is not plugged in!");
                return new Result(true, "Vehicle is unplugged. Notification sent");
            }
        }
        return new Result(true, "Vehicle is plugged-in. No notification sent");
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
            if (rowNumber < 0)
                continue;   // -1 isn't in the grid
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
    
    @Override protected void fxInitialize() {
        // Deep-Six the refresh button and progress indicator
        refreshButton.setDisable(true);
        refreshButton.setVisible(false);
        progressIndicator.setVisible(false);
        progressLabel.setVisible(false);

        prepareSchedulerUI(gridPane);
    }

    @Override protected void prepForVehicle(Vehicle v) {
        if (differentVehicle()) {
            appContext.schedulerActivityReport.set("");

            charge = new ChargeState(v);
            chargeController = new org.noroomattheinn.tesla.ChargeController(v);
            hvacController = new org.noroomattheinn.tesla.HVACController(v);

            if (charge.refresh()) {
                maxCharge = charge.state.chargeLimitSOCMax;
                stdCharge = charge.state.chargeLimitSOCStd;
            } else {
                Tesla.logger.log(Level.WARNING,
                    "Unable to get charge information for use by scheduler. Using defaults");
                maxCharge = 100;
                stdCharge = 90;
            }
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
        appContext.schedulerActivityReport.set(entry);
    }
    

}

