/*
 * ScheduleItem.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Sep 7, 2013
 */

package org.noroomattheinn.visibletesla;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import it.sauronsoftware.cron4j.Scheduler;
import java.util.Map;
import java.util.prefs.Preferences;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.HBox;
import org.noroomattheinn.utils.Utils;




class ScheduleItem implements EventHandler<ActionEvent> {

/*------------------------------------------------------------------------------
 *
 * Constants and Enums
 * 
 *----------------------------------------------------------------------------*/

    public enum Command {
        HVAC_ON, HVAC_OFF, CHARGE_ON, CHARGE_OFF, AWAKE, SLEEP, DAYDREAM,
        CHARGE_STD, CHARGE_MAX, CHARGE_MIN, None}
    private static final BiMap<Command, String> commandMap = HashBiMap.create();
    static {
        commandMap.put(Command.HVAC_ON, "HVAC: On");
        commandMap.put(Command.HVAC_OFF, "HVAC: Off");
        commandMap.put(Command.CHARGE_ON, "Charge: Start");
        commandMap.put(Command.CHARGE_OFF, "Charge: Stop");
        commandMap.put(Command.AWAKE, "Awake");
        commandMap.put(Command.SLEEP, "Sleep");
        commandMap.put(Command.DAYDREAM, "Daydream");
        commandMap.put(Command.CHARGE_STD, "Charge: Std");
        commandMap.put(Command.CHARGE_MAX, "Charge: Max");
        commandMap.put(Command.CHARGE_MIN, "Charge: Low");
    }
    // the following map is here to keep track of any changes to the command names
    // We store the command names in the prefs file (MISTAKE) so we need to track
    // any changes so that when we internalize, we get the new names, not the old ones.
    private static final Map<String,String> UpdatedCommandNames = Utils.newHashMap(
        "HVAC On", "HVAC: On",
        "HVAC Off", "HVAC: Off",
        "Start Charging", "Charge: Start",
        "Stop Charging", "Charge: Stop"
    );
    
    public interface ScheduleOwner {
        public String getExternalKey();
        public Preferences getPreferences();
        public void runCommand(ScheduleItem.Command command, boolean minCharge);
    }

/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/

    // The UI Elements comprising a ScheduleItem
    private CheckBox enabled;
    private CheckBox[] days = new CheckBox[7];
    private TimeSelector time;
    private CheckBox minCharge;
    private ComboBox<String> command;
    
    private int id; // Externally assigned ID of this instance
    private String schedulerID = null;  // ID of the cron4j instance
    private ScheduleOwner owner;
    
    private static final Scheduler scheduler = new Scheduler();
    static {
        // Register with appContext so we can quit it upon exit
        scheduler.setDaemon(true);
    }

/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    /**
     * Instantiate a ScheduleItem object with the specified ID and a 
     * row of UI elements representing the controls for the scheduler
     * The controls are each associated with a unique int which is the key
     * to the row map. The elements are:
     * 0: The enabled CheckBox
     * 1-7: The days of the week CheckBoxes (Sun=0 ... Sat=7)
     * 8: An HBox containing three ComboBoxes: Hour, Min, AM/PM
     * 9: The "minimum charge required" CheckBox
     * 10: The command ComboBox 
     * @param id    The externally assigned ID of this row of controls
     * @param row   A map containing the row of controls as described above
     */
    public ScheduleItem(int id, Map<Integer,Node> row, ScheduleOwner owner) {
        this.id = id;
        this.owner = owner;
        
        enabled = (CheckBox)prepNode(row.get(0));

        for (int i = 0; i < 7; i++) {
            days[i] = (CheckBox)prepNode(row.get(i+1));
        }
        HBox hbox = (HBox)row.get(8);
        time = new TimeSelector(
                Utils.<ComboBox<String>>cast(prepNode(hbox.getChildren().get(0))),
                Utils.<ComboBox<String>>cast(prepNode(hbox.getChildren().get(1))),
                Utils.<ComboBox<String>>cast(prepNode(hbox.getChildren().get(2))));
        minCharge = (CheckBox)prepNode(row.get(9));
        command = Utils.<ComboBox<String>>cast(prepNode(row.get(10)));        
    }
    
    /**
     * We're shutting down, do any necessary cleanup
     */
    public void shutDown() {
        if (schedulerID != null) {
            scheduler.deschedule(schedulerID);
        }
    }
    
    public void loadExistingSchedule() {
        // Load any saved value for this ScheduleItem
        String key = getFullKey();
        String encoded = owner.getPreferences().get(key, null);
        if (encoded != null) {
            internalize(encoded);
            startScheduler();
        }

    }
    
    public static Command nameToCommand(String commandName) {
        Command cmd = commandMap.inverse().get(commandName);
        return (cmd == null) ? Command.None : cmd;
    }
    
    public static String commandToName(Command cmd) {
        return commandMap.get(cmd);
    }
    
/*------------------------------------------------------------------------------
 *
 * PRIVATE Methods for storing/loading schedules to/from external storage
 * 
 *----------------------------------------------------------------------------*/
    

    private String externalize() {
        StringBuilder sb = new StringBuilder();

        sb.append(onOff(enabled.isSelected()));
        sb.append('_');
        for (int i = 0; i < 7; i++) {
            sb.append(onOff(days[i].isSelected()));
            sb.append('_');
        }
        sb.append(String.format("%04d_", time.getHoursAndMinutes()));
        sb.append(onOff(minCharge.isSelected()));
        sb.append('_');
        sb.append(command.getSelectionModel().selectedItemProperty().getValue());

        return sb.toString();
    }

    private void internalize(String encoded) {
        //  0     1     2     3     4     5     6     7    8     9    10
        //  On?   Mon   Tue   Wed   Thu   Fri   Sat   Sun  Time  Min  Command
        // {0|1}_{0|1}_{0|1}_{0|1}_{0|1}_{0|1}_{0|1}_{0|1}_HHMM_{0|1}_COMMAND
        String[] elements = encoded.split("_");
        enabled.setSelected(elements[0].equals("1"));
        for (int i = 0; i < 7; i++) {
            days[i].setSelected(elements[i + 1].equals("1"));
        }
        time.setHoursAndMinutes(Integer.valueOf(elements[8]));
        minCharge.setSelected(elements[9].equals("1"));
        command.getSelectionModel().select(properCommandName(elements[10]));
    }
    
    private String properCommandName(String cmd) {
        String newName = UpdatedCommandNames.get(cmd);
        if (newName == null) return cmd;
        return newName;
    }
    
    private String onOff(boolean b) { return b ? "1" : "0"; }

/*------------------------------------------------------------------------------
 *
 * PRIVATE Methods that interface with the actual scheduler library (cron4j)
 * 
 *----------------------------------------------------------------------------*/
    
    private void startScheduler() {
        if (schedulerID != null) {
            scheduler.deschedule(schedulerID);
        }

        if (!enabled.isSelected()) {
            return;
        }

        String pattern = getSchedulePattern();
        if (pattern.isEmpty()) {
            return;
        }
        
        schedulerID = scheduler.schedule(pattern, new Runnable() {
            @Override public void run() {
                Command cmd = nameToCommand(command.getValue());
                if (!enabled.isSelected() || cmd == Command.None)
                    return;
                
                owner.runCommand(cmd, minCharge.isSelected());
            }
        });
        
        if (!scheduler.isStarted())
            scheduler.start();
    }
    
    private String getSchedulePattern() {
        StringBuilder sb = new StringBuilder();
        int theTime = time.getHoursAndMinutes();
        sb.append(theTime % 100);
        sb.append(' ');
        sb.append(theTime / 100);
        sb.append(" * * ");
        boolean hasDays = false;
        for (int i = 0; i < 7; i++) {
            if (days[i].isSelected()) {
                sb.append(i);
                sb.append(',');
                hasDays = true;
            }
        }
        if (!hasDays) {
            return "";
        }
        if (sb.charAt(sb.length() - 1) == ',') {
            sb.deleteCharAt(sb.length() - 1);
        }

        return sb.toString();
    }

/*------------------------------------------------------------------------------
 *
 * Private Utility Methods - General
 * 
 *----------------------------------------------------------------------------*/
    
    private String getFullKey() {
        return String.format("%s_SCHED_%02d", owner.getExternalKey(), id);
    }
    
    @Override public void handle(ActionEvent event) {
        String key = getFullKey();
        String encoded = externalize();
        owner.getPreferences().put(key, encoded);   // Save the updated ScheduleItem
        startScheduler();   // Start (or restart) the scheduler 
    }


    private Node prepNode(Node n) {
        if (n instanceof ComboBox) {
            ComboBox<String> cbs = Utils.<ComboBox<String>>cast(n);
            cbs.setOnAction(this);
            cbs.setVisibleRowCount(12);
        } else if (n instanceof ButtonBase) {
            ((ButtonBase) n).setOnAction(this);
        }
        n.setStyle("-fx-focus-color: transparent;");
        return n;
    }
    
    
}


/*------------------------------------------------------------------------------
 *
 * Private Utility Classes
 * 
 *----------------------------------------------------------------------------*/

class TimeSelector {
    ComboBox<String> hour;
    ComboBox<String> min;
    ComboBox<String> amPM;

    TimeSelector(ComboBox<String> hour, ComboBox<String> min, ComboBox<String> amPM) {
        this.hour = hour; this.min = min; this.amPM = amPM;
    }

    int getHoursAndMinutes() {
        int h = Integer.valueOf(hour.getValue());
        int m = Integer.valueOf(min.getValue());
        if (amPM.getValue().equals("PM")) {
            if (h != 12)
                h = (h + 12) % 24;
        } else {    // It's AM
            if (h == 12)
                h = 0;
        }
        return h * 100 + m;
    }

    void setHoursAndMinutes(int hhmm) {
        int hours = hhmm / 100;
        int minutes = hhmm % 100;
        minutes = (minutes/5) * 5;
        boolean isPM = (hours >= 12);
        if (hours == 0) {
            hours = 12;
        }
        if (isPM && !(hours == 12))
            hours -= 12;
        hour.getSelectionModel().select(String.format("%02d", hours));
        min.getSelectionModel().select(String.format("%02d", minutes));
        amPM.getSelectionModel().select(isPM ? "PM" : "AM");
    }
}

