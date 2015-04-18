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
import org.noroomattheinn.tesla.Vehicle;
import org.noroomattheinn.utils.MailGun;
import org.noroomattheinn.utils.ThreadManager;
import org.noroomattheinn.utils.ThreadManager.Stoppable;
import org.noroomattheinn.visibletesla.ScheduleItem.Command;

import static org.noroomattheinn.tesla.Tesla.logger;

/**
 * FXML Controller class
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class SchedulerController extends BaseController
    implements ScheduleItem.ScheduleOwner, Stoppable {

    private static final int Safe_Threshold = 25;
    
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/

    @FXML private GridPane gridPane;
    @FXML private TextArea activityLog;
    
    private Vehicle v;
    
    private final List<ScheduleItem> schedulers = new ArrayList<>();
    
/*------------------------------------------------------------------------------
 *
 * Implementation of the Stoppable interface
 * 
 *----------------------------------------------------------------------------*/
    
    @Override public void stop() {
        for (ScheduleItem si : schedulers) {
            si.shutDown();
        }
        ScheduleItem.stop();
    }

/*------------------------------------------------------------------------------
 *
 * Implementation of the ScheduleOwner interface
 * 
 *----------------------------------------------------------------------------*/
    
    @Override public String getExternalKey() { return v.getVIN(); }
    @Override public Preferences getPreferences() { return prefs.storage(); }
    @Override public App app() { return app; }
    @Override public Prefs prefs() { return prefs; }
    @Override public boolean useDegreesF() { return vtVehicle.useDegreesF(); }
    
    @Override public void runCommand(
            ScheduleItem.Command command, double value,
            MessageTarget messageTarget) {
        if (command != ScheduleItem.Command.SLEEP) {
            if (!vtVehicle.forceWakeup()) {
                logActivity("Can't wake vehicle - aborting", true);
                return;
            }
            app.api.setActive();
        }
        if (!safeToRun(command)) return;
        
        if (!tryCommand(command, value, messageTarget)) {
            tryCommand(command, value, messageTarget);  // Retry to avoid transient errors
        }
    }
    
    private boolean tryCommand(
            ScheduleItem.Command command, double value,
            MessageTarget messageTarget) {
        String name = ScheduleItem.commandToName(command);
        Result r = Result.Succeeded;
        boolean reportActvity = true;
        switch (command) {
            case CHARGE_SET:
            case CHARGE_ON:
                if (value > 0) {
                    r = v.setChargePercent((int)value);
                    if (!(r.success || r.explanation.equals("already_set"))) {
                        logActivity("Unable to set charge target: " + r.explanation, true);
                    }
                }
                if (command == Command.CHARGE_ON)
                    r = v.startCharging();
                break;
            case CHARGE_OFF: r = v.stopCharging(); break;
            case HVAC_ON:
                if (value > 0) {    // Set the target temp first
                    if (vtVehicle.useDegreesF())
                        r = v.setTempF(value, value);
                    else
                        r = v.setTempC(value, value);
                    if (!r.success) break;
                }
                r = v.startAC();
                break;
            case HVAC_OFF: r = v.stopAC();break;
            case AWAKE: app.api.stayAwake(); break;
            case SLEEP: app.api.allowSleeping(); break;
            case UNPLUGGED: r = unpluggedTrigger(); reportActvity = false; break;
            case MESSAGE: r = sendMessage(messageTarget); reportActvity = false; break;
        }
        if (value > 0) name = String.format("%s (%3.1f)", name, value);
        String entry = String.format("%s: %s", name, r.success ? "succeeded" : "failed");
        if (!r.success) entry = entry + ", " + r.explanation;
        logActivity(entry, reportActvity);
        return r.success;
    }
    
    private Result sendMessage(MessageTarget messageTarget) {
        if (messageTarget == null) {
            MailGun.get().send(
                prefs.notificationAddress.get(),
                "No subject was specified",
                "No body was specified");
            return Result.Succeeded;
        }
        MessageTemplate body = new MessageTemplate(messageTarget.getActiveMsg());
        MessageTemplate subj = new MessageTemplate(messageTarget.getActiveSubj());
        boolean sent = MailGun.get().send(
            messageTarget.getActiveEmail(),             // To
            subj.getMessage(app.api, vtVehicle, null),      // Subject
            body.getMessage(app.api, vtVehicle, null));     // Body
        return sent ? Result.Succeeded : Result.Failed;
    } 
    
    private boolean requiresSafeMode(ScheduleItem.Command command) {
        return (command == ScheduleItem.Command.HVAC_ON);
    }
    
    private boolean safeToRun(ScheduleItem.Command command) {
        if (!requiresSafeMode(command)) return true;
        
        String name = ScheduleItem.commandToName(command);
        if (prefs.safeIncludesMinCharge.get()) {
            if (vtVehicle.chargeState.get().batteryPercent < Safe_Threshold) {
                String entry = String.format(
                        "%s: Insufficient charge - aborted", name);
                logActivity(entry, true);
                return false;
            }
        }

        if (prefs.safeIncludesPluggedIn.get()) {
            String msg;

            switch (vtVehicle.chargeState.get().chargingState) {
                case Unknown:
                    msg = String.format("%s: Can't tell if car is plugged in - aborted", name);
                    logActivity(msg, true);
                    return false;
                case Disconnected:
                    msg = String.format("%s: Car is not plugged in - aborted", name);
                    logActivity(msg, true);
                    return false;
                default:
                    return true;
            }
        }
        
        return true;
    }
    
    private synchronized Result unpluggedTrigger() {
        ChargeState charge = vtVehicle.chargeState.get();
        ChargeState.Status status = charge.chargingState;
        if (status == ChargeState.Status.Disconnected) {
            MailGun.get().send(
                prefs.notificationAddress.get(),
                "Your car is not plugged in. Range = " + (int)charge.range);
            return new Result(true, "Vehicle is unplugged. Notification sent");
        } else if (status == ChargeState.Status.Unknown) {
            MailGun.get().send(
                prefs.notificationAddress.get(),
                "Can't determine if your car is plugged in. Please check");
            return new Result(true, "Can't tell if car is plugged in. Warning sent");
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

        prepareSchedulerUI(gridPane);
    }

    @Override protected void initializeState() {
        v = vtVehicle.getVehicle();
        ThreadManager.get().addStoppable(this);
    }
    
    @Override protected void activateTab() {
        for (ScheduleItem item : schedulers) { item.loadExistingSchedule(); }
    }
    
    @Override protected void refresh() { }

    
/*------------------------------------------------------------------------------
 *
 * PRIVATE - Utility Methods
 * 
 *----------------------------------------------------------------------------*/
    
    private void logActivity(String entry, boolean report) {
        Date now = new Date();
        String previousEntries = activityLog.getText();
        String datedEntry = String.format(
            "[%1$tm/%1$td/%1$ty %1$tH:%1$tM] %2$s\n%3$s", now, entry, previousEntries);
        activityLog.setText(datedEntry);
        logger.log(Level.FINE, entry);
        if (report) { app.schedulerActivity.set(entry); }
    }

}

