/*
 * ScheduleItem.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Sep 7, 2013
 */

package org.noroomattheinn.visibletesla;

import org.noroomattheinn.visibletesla.dialogs.SetChargeDialog;
import org.noroomattheinn.visibletesla.dialogs.SetTempDialog;
import org.noroomattheinn.visibletesla.dialogs.DialogUtils;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import it.sauronsoftware.cron4j.Scheduler;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.prefs.Preferences;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.HBox;
import static org.noroomattheinn.tesla.Tesla.logger;
import org.noroomattheinn.utils.Utils;
import org.noroomattheinn.visibletesla.dialogs.NotifyOptionsDialog;
import org.noroomattheinn.visibletesla.fxextensions.TimeSelector;



/**
 * ScheduleItem: Represents a single item to be scheduled.
 * TO DO: Right now this class directly persists state to the preference
 * store. It gets the appropriate key and the pref store from the "owner".
 * Instead it should just be calling the owner and asking it to read and write 
 * persistent state.
 * @author joe at NoRoomAtTheInn dot org
 */
class ScheduleItem implements EventHandler<ActionEvent> {

/*------------------------------------------------------------------------------
 *
 * Constants and Enums
 * 
 *----------------------------------------------------------------------------*/

    public enum Command {
        HVAC_ON, HVAC_OFF, CHARGE_ON, CHARGE_OFF, CHARGE_SET, AWAKE, SLEEP,
        UNPLUGGED, SET, MESSAGE, None}
    private static final BiMap<Command, String> commandMap = HashBiMap.create();
    static {
        commandMap.put(Command.HVAC_ON, "HVAC: On");
        commandMap.put(Command.HVAC_OFF, "HVAC: Off");
        commandMap.put(Command.CHARGE_SET, "Charge: Set");
        commandMap.put(Command.CHARGE_ON, "Charge: Start");
        commandMap.put(Command.CHARGE_OFF, "Charge: Stop");
        commandMap.put(Command.AWAKE, "Awake");
        commandMap.put(Command.SLEEP, "Sleep");
        commandMap.put(Command.UNPLUGGED, "Unplugged?");
        commandMap.put(Command.MESSAGE, "Message");
        commandMap.put(Command.SET, "Set Value");
    }
    
    // the following map is here to keep track of any updates to the command names.
    // We store the command names in the prefs file so we need to track
    // any changes so that when we internalize, we get the new names, not the old ones.
    private static final Map<String,String> UpdatedCommandNames = Utils.newHashMap(
        "HVAC On", "HVAC: On",
        "HVAC Off", "HVAC: Off",
        "Start Charging", "Charge: Start",
        "Stop Charging", "Charge: Stop",
        "Charge: Std", "None",              // Obsolete command
        "Charge: Max", "None",              // Obsolete command
        "Charge: Low", "None",              // Obsolete command
        "Daydream", "None"
    );
    
    private static final String SchedulerMsgKey = "SCHEDMSG";
    private static final String DefaultSubject = "Scheduled Message";
    private static final String DefaultMessage = "SOC: {{SOC}}%\n{{LOC}}\n";
            
    public interface ScheduleOwner {
        public String getExternalKey();
        public Preferences getPreferences();
        public void runCommand(ScheduleItem.Command command, double value, MessageTarget mt);
        public AppContext getAppContext();
    }

/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/

    // The UI Elements comprising a ScheduleItem
    private final CheckBox enabled;
    private final CheckBox[] days = new CheckBox[7];
    private final TimeSelector time;
    private final CheckBox once;
    private final ComboBox<String> command;
    private final Button options;
    
    private final int id; // Externally assigned ID of this instance
    private String schedulerID = null;  // ID of the cron4j instance
    private final ScheduleOwner owner;
    private double targetValue = -1;
    private MessageTarget messageTarget = null;
    private boolean internalizing = false;
    
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
     * 9: The "Once" CheckBox
     * 10: An HBox containing the command ComboBox and a "+" button for options
     * @param id    The externally assigned ID of this row of controls
     * @param row   A map containing the row of controls as described above
     */
    public ScheduleItem(int id, Map<Integer,Node> row, final ScheduleOwner owner) {
        this.id = id;
        this.owner = owner;
        
        enabled = (CheckBox)prepNode(row.get(0));
        for (int i = 0; i < 7; i++) { days[i] = (CheckBox)prepNode(row.get(i+1)); }
        HBox hbox = (HBox)row.get(8);
        time = new TimeSelector(
                Utils.<ComboBox<String>>cast(prepNode(hbox.getChildren().get(0))),
                Utils.<ComboBox<String>>cast(prepNode(hbox.getChildren().get(1))),
                Utils.<ComboBox<String>>cast(prepNode(hbox.getChildren().get(2))));
        once = (CheckBox)prepNode(row.get(9));
        hbox = (HBox)row.get(10);
        command = Utils.<ComboBox<String>>cast(prepNode(hbox.getChildren().get(0)));       
        options = (Button)(hbox.getChildren().get(1));
        
        enabled.selectedProperty().addListener(new ChangeListener<Boolean>() {
            @Override public void changed(ObservableValue<? extends Boolean> ov, Boolean t, Boolean t1) {
                enableItems(t1);
            } });
        
        prepOptionsHandler(command.valueProperty().get());
        command.valueProperty().addListener(new ChangeListener<String>() {
            @Override public void changed(ObservableValue<? extends String> ov, String t, String t1) {
                prepOptionsHandler(t1);
            }
        });
        
        enableItems(enabled.isSelected());
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
        internalizing = true;
        // Load any saved value for this ScheduleItem
        String key = getFullKey();
        String encoded = owner.getPreferences().get(key, null);
        if (encoded != null) {
            internalize(encoded);
            startScheduler();
        }
        internalizing = false;
    }
    
    private MessageTarget loadMessageTarget() {
        String baseKey = String.format("%s%02d", SchedulerMsgKey, id);
        return new MessageTarget(
            owner.getAppContext(), baseKey, DefaultSubject, DefaultMessage);
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
 * PRIVATE Methods for handling options of various types
 * 
 *----------------------------------------------------------------------------*/
    
    private void prepOptionsHandler(String forCommand) {
        if (forCommand.equals(commandMap.get(Command.HVAC_ON))) {
            options.setVisible(true);
            options.setOnAction(getTempOptions);
        } else if (forCommand.equals(commandMap.get(Command.CHARGE_SET))) {
            options.setVisible(true);
            options.setOnAction(getChargeOptions);
        } else if (forCommand.equals(commandMap.get(Command.CHARGE_ON))) {
            options.setVisible(true);
            options.setOnAction(getChargeOptions);
        } else if (forCommand.equals(commandMap.get(Command.MESSAGE))) {
            options.setVisible(true);
            options.setOnAction(getMessageTarget);
        } else {
            options.setVisible(false);
        }
    }

    private EventHandler<ActionEvent> getMessageTarget = new EventHandler<ActionEvent>() {
        @Override public void handle(ActionEvent t) {
            if (messageTarget == null) messageTarget = loadMessageTarget();
            Map<Object, Object> props = new HashMap<>();
            props.put("EMAIL", messageTarget.getEmail());
            props.put("SUBJECT", messageTarget.getSubject());
            props.put("MESSAGE", messageTarget.getMessage());

            DialogUtils.DialogController dc = DialogUtils.displayDialog(
                    getClass().getResource("dialogs/NotifyOptionsDialog.fxml"),
                    "Message Options", owner.getAppContext().stage, props);
            if (dc == null) {
                logger.warning("Can't display \"Message Options\" dialog");
                messageTarget.setEmail(null); 
                messageTarget.setSubject(null);
                messageTarget.setMessage(null);
                messageTarget.externalize();
                return;
            }

            NotifyOptionsDialog nod = Utils.cast(dc);
            if (!nod.cancelled()) {
                if (!nod.useDefault()) {
                    messageTarget.setEmail(nod.getEmail());
                    messageTarget.setSubject(nod.getSubject());
                    messageTarget.setMessage(nod.getMessage());
                } else {
                    messageTarget.setEmail(null);
                    messageTarget.setSubject(null);
                    messageTarget.setMessage(null);
                }
                messageTarget.externalize();
            }
        }
    };

    EventHandler<ActionEvent> getChargeOptions = new EventHandler<ActionEvent>() {
        @Override public void handle(ActionEvent e) {
            Map<Object, Object> props = new HashMap<>();
            boolean useDegreesF = owner.getAppContext().lastGUIState.get().
                                        temperatureUnits.equalsIgnoreCase("F");
            props.put("INIT_CHARGE", targetValue);
            props.put("USE_DEGREES_F", useDegreesF);

            DialogUtils.DialogController dc = DialogUtils.displayDialog(
                    getClass().getResource("dialogs/SetChargeDialog.fxml"),
                    "Target Charge Level", owner.getAppContext().stage, props);
            if (dc == null) {
                logger.severe("Can't display \"Target Charge\" dialog");
                targetValue = -1; 
                return;
            }

            SetChargeDialog scd = Utils.cast(dc);
            if (!scd.cancelled()) {
                if (!scd.useCarsValue()) {
                    targetValue = scd.getValue();
                } else {
                    targetValue = -1;
                }
                doExternalize();
            }
        }
    };
    
    EventHandler<ActionEvent> getTempOptions = new EventHandler<ActionEvent>() {
        @Override public void handle(ActionEvent e) {
            Map<Object, Object> props = new HashMap<>();
            props.put("USE_DEGREES_F",
                    owner.getAppContext().lastGUIState.get().temperatureUnits.equalsIgnoreCase("F"));
            props.put("INIT_TEMP", targetValue);

            DialogUtils.DialogController dc = DialogUtils.displayDialog(
                    getClass().getResource("dialogs/SetTempDialog.fxml"),
                    "Target Temperature", owner.getAppContext().stage, props);
            if (dc == null) {
                logger.severe("Can't display \"Target Temperature\" dialog");
                targetValue = -1; 
                return;
            }

            SetTempDialog std = Utils.cast(dc);
            if (!std.cancelled()) {
                if (!std.useCarsValue()) {
                    targetValue = std.getValue();
                } else {
                    targetValue = -1;
                }
                doExternalize();
            }
        }
    };
 
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
        sb.append(onOff(once.isSelected()));
        sb.append('_');
        sb.append(command.getSelectionModel().selectedItemProperty().getValue());
        sb.append("_");
        sb.append(String.format(Locale.US, "%3.1f", targetValue));
        
        return sb.toString();
    }

    private void internalize(String encoded) {
        //  0     1     2     3     4     5     6     7    8     9    10      [11]
        //  On?   Mon   Tue   Wed   Thu   Fri   Sat   Sun  Time  Min  Command [Value]
        // {0|1}_{0|1}_{0|1}_{0|1}_{0|1}_{0|1}_{0|1}_{0|1}_HHMM_{0|1}_COMMAND_[Double]
        String[] elements = encoded.split("_");
        enabled.setSelected(elements[0].equals("1"));
        for (int i = 0; i < 7; i++) {
            days[i].setSelected(elements[i + 1].equals("1"));
        }
        time.setHoursAndMinutes(Integer.valueOf(elements[8]));
        once.setSelected(elements[9].equals("1"));
        command.getSelectionModel().select(properCommandName(elements[10]));
        if (elements.length == 12) {
            try {
                targetValue = Double.valueOf(elements[11]);
            } catch (NumberFormatException e) {
                logger.severe("Bad value for command target: " + elements[11]);
                targetValue = -1;
            }
        }
    }
    
    private String properCommandName(String cmd) {
        String newName = UpdatedCommandNames.get(cmd);
        return (newName == null) ? cmd : newName;
    }
    
    private String onOff(boolean b) { return b ? "1" : "0"; }

/*------------------------------------------------------------------------------
 *
 * PRIVATE Methods that interface with the actual scheduler library (cron4j)
 * 
 *----------------------------------------------------------------------------*/
    
    private void startScheduler() {
        if (schedulerID != null) { scheduler.deschedule(schedulerID); }

        if (!enabled.isSelected()) { return; }

        String pattern = getSchedulePattern();
        if (pattern.isEmpty()) { return; }
        
        schedulerID = scheduler.schedule(pattern, new Runnable() {
            @Override public void run() {
                Command cmd = nameToCommand(command.getValue());
                if (!enabled.isSelected() || cmd == Command.None) return;
                
                if (!(cmd == Command.CHARGE_ON || cmd == Command.HVAC_ON || 
                      cmd == Command.CHARGE_SET))
                    targetValue = -1;
                if (cmd == Command.MESSAGE && messageTarget == null) {
                    messageTarget = loadMessageTarget();
                }
                owner.runCommand(cmd, targetValue, messageTarget);
                if (once.isSelected()) {
                    enabled.setSelected(false);
                    enableItems(false);
                    doExternalize();
                }
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
    
    private void doExternalize() {
        if (internalizing) return;
        String key = getFullKey();
        String encoded = externalize();
        owner.getPreferences().put(key, encoded);   // Save the updated ScheduleItem
    }
    
    @Override public void handle(ActionEvent event) {
        doExternalize();
        startScheduler();   // Start (or restart) the scheduler 
    }


    private Node prepNode(Node n) {
        if (n instanceof ComboBox) {
            ComboBox<String> cbs = Utils.<ComboBox<String>>cast(n);
            cbs.setOnAction(this);
            cbs.setVisibleRowCount(13);
        } else if (n instanceof ButtonBase) {
            ((ButtonBase) n).setOnAction(this);
        }
        n.setStyle("-fx-focus-color: transparent;");
        return n;
    }
    
    private void enableItems(boolean enable) {
        for (int i = 0; i < 7; i++) { days[i].setDisable(!enable); }
        time.enable(enable);
        once.setDisable(!enable);
        command.setDisable(!enable); 
        options.setDisable(!enable);
    }
    
    
}

