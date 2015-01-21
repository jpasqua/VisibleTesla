/*
 * NotifierController.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Dec 6, 2013
 */

package org.noroomattheinn.visibletesla;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.net.URLConnection;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Slider;
import jfxtras.labs.scene.control.BigDecimalField;
import org.apache.commons.lang3.StringUtils;
import org.noroomattheinn.tesla.ChargeState;
import org.noroomattheinn.tesla.StreamState;
import org.noroomattheinn.tesla.Vehicle;
import org.noroomattheinn.utils.GeoUtils;
import org.noroomattheinn.utils.MailGun;
import org.noroomattheinn.utils.ThreadManager;
import org.noroomattheinn.utils.Utils;
import org.noroomattheinn.visibletesla.dialogs.ChooseLocationDialog;
import org.noroomattheinn.visibletesla.dialogs.NotifyOptionsDialog;
import org.noroomattheinn.visibletesla.trigger.DeviationTrigger;
import org.noroomattheinn.visibletesla.trigger.GenericTrigger;
import org.noroomattheinn.visibletesla.trigger.StationaryTrigger;

import static org.noroomattheinn.tesla.Tesla.logger;

/**
 * NotifierController
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */

public class NotifierController extends BaseController {

/*------------------------------------------------------------------------------
 *
 * Constants, Enums, and Types
 * 
 *----------------------------------------------------------------------------*/
    
    private static class GeoTrigger {
        GenericTrigger<GeoUtils.CircularArea> trigger;
        MessageTarget        messageTarget;
        ObjectProperty<GeoUtils.CircularArea> prop =
                        new SimpleObjectProperty<>(new GeoUtils.CircularArea());
        Button               defineArea;
        Button               optionsButton;
        CheckBox             enabled;
        GeoTrigger(Button options, Button defArea, CheckBox enabled) {
            this.optionsButton = options;
            this.defineArea = defArea;
            this.enabled = enabled;
        }
    }
    
    private static class StringList extends ArrayList<String> {
        StringList(String s) { super(); add(s); }
        StringList() { super();  }
    }
    
    private static final long TypicalDebounce = 10 * 60 * 1000; // 10 Minutes
    private static final long SpeedDebounce = 30 * 60 * 1000;   // 30 Minutes
    private static final long GeoDebounce = 30 * 1000;          // 30 Seconds
    private static final long UnlockedThreshold = 10;           // 10 Minutes
    
    private static final String NotifySEKey = "NOTIFY_SE";
    private static final String NotifyCSKey = "NOTIFY_CS";
    private static final String NotifyCAKey = "NOTIFY_CA";
    private static final String NotifyULKey = "NOTIFY_UL";
    private static final String NotifyULValKey = "NOTIFY_UL_VAL";
    private static final String NotifySpeedKey = "NOTIFY_SPEED";
    private static final String NotifySOCHitsKey = "NOTIFY_SOC_HITS";
    private static final String NotifySOCFallsKey = "NOTIFY_SOC_FALLS";
    private static final String NotifyEnterKey = "NOTIFY_ENTER_AREA";
    private static final String NotifyLeftKey = "NOTIFY_LEFT_AREA";
    private static final String NotifyOdoKey = "NOTIFY_ODO";
    private static final String OdoCheckKey = "LAST_ODO_CHECK";
    
    private static final String UnlockedSubj = "Unlocked at {{TIME}}";
    private static final String UnlockedMsg = "Unlocked at {{TIME}} for {{CUR}} minutes";
    private static final String OdoHitsSubj = "Odometer: {{CUR}}";
    private static final String OdoHitsMsg =
            "Odometer Past: {{TARGET}} {{D_UNITS}} ({{CUR}})";
    private static final String SOCHitSubj = "SOC: {{CUR}}%";
    private static final String SOCHitMsg = "SOC Hit or Exceeded: {{TARGET}}% ({{CUR}}%)";
    private static final String SOCFellSubj = "SOC: {{CUR}}%";
    private static final String SOCFellMsg = "SOC Fell Below: {{TARGET}}% ({{CUR}}%)";
    private static final String SpeedHitSubj = "Speed: {{SPEED}} {{S_UNITS}}";
    private static final String SpeedHitMsg = 
            "Speed Hit or Exceeded: {{TARGET}} {{S_UNITS}} ({{SPEED}})";
    private static final String SchedEventSubj = "Scheduled Event: {{CUR}}";
    private static final String SchedEventMsg = "Scheduled Event: {{CUR}}";
    private static final String ChargeStateSubj = "Charge State: {{CHARGE_STATE}}";
    private static final String ChargeStateMsg =
        "Charge State: {{CHARGE_STATE}}" +
        "\nSOC: {{SOC}}%" +
        "\nRange: {{RATED}} {{D_UNITS}}" +
        "\nEstimated Range: {{ESTIMATED}} {{D_UNITS}}" +
        "\nIdeal Range: {{IDEAL}} {{D_UNITS}}";
    private static final String ChargeAnomalySubj = "Charge Anomaly";
    private static final String ChargeAnomalyMsg =
            "{{A_CT}} current deviated significantly from baseline" +
            "\nBaseline: {{TARGET}}A" +
            "\nMost Recent: {{CUR}}A";
    private static final String EnterAreaSubj = "Entered {{TARGET}}";
    private static final String EnterAreaMsg = "Entered {{TARGET}}";
    private static final String LeftAreaSubj = "Left {{TARGET}}";
    private static final String LeftAreaMsg = "Left {{TARGET}}";


/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private GenericTrigger<BigDecimal> speedHitsTrigger;
    private MessageTarget       shMessageTarget;
    
    private GenericTrigger<BigDecimal> odoHitsTrigger;
    private MessageTarget       ohMessageTarget;
    private double              lastOdoCheck;
    
    private GenericTrigger<String>     seTrigger;
    private MessageTarget       seMessageTarget;
    
    private GenericTrigger<BigDecimal> socHitsTrigger;
    private MessageTarget       socHitsMessageTarget;

    private GenericTrigger<BigDecimal> socFallsTrigger;
    private MessageTarget       socFallsMessageTarget;
    
    private GenericTrigger<StringList> csTrigger;
    private MessageTarget       csMessageTarget;
    private ObjectProperty<StringList>
                                csSelectProp = new SimpleObjectProperty<>(new StringList());
            
    private boolean             useMiles = true;
    
    private GeoTrigger[]        geoTriggers = new GeoTrigger[8];
    
    // Charge Anomoly Triggers
    private DeviationTrigger    ccTrigger;
    private DeviationTrigger    pcTrigger;
    private MessageTarget       caMessageTarget;
    
    // Unlocked Trigger
    private StationaryTrigger   unlockedTrigger;
    private MessageTarget       ulMessageTarget;
    private boolean             checkForUnlocked;
    
    
/*------------------------------------------------------------------------------
 *
 * UI Elements
 * 
 *----------------------------------------------------------------------------*/
        
    @FXML private CheckBox chargeState;
    @FXML private Button chargeBecomesOptions;
    @FXML private RadioButton csbAny, csbCharging, csbComplete, csbDisconnected,
                              csbNoPower, csbStarting, csbStopped;
    
    @FXML private CheckBox schedulerEvent;
    @FXML private Button seOptions;

    @FXML private CheckBox socFalls;
    @FXML private BigDecimalField socFallsField;
    @FXML private Slider socFallsSlider;
    @FXML private Button socFallsOptions;

    @FXML private CheckBox socHits;
    @FXML private BigDecimalField socHitsField;
    @FXML private Slider socHitsSlider;
    @FXML private Button socHitsOptions;

    @FXML private CheckBox odoHits;
    @FXML private BigDecimalField odoHitsField;
    @FXML private Label odoHitsLabel;
    @FXML private Button odoHitsOptions;

    @FXML private CheckBox speedHits;
    @FXML private BigDecimalField speedHitsField;
    @FXML private Slider speedHitsSlider;
    @FXML private Label speedUnitsLabel;
    @FXML private Button speedHitsOptions;

    @FXML private CheckBox carEntered1, carEntered2, carEntered3, carEntered4;
    @FXML private Button defineEnterButton1, defineEnterButton2, defineEnterButton3, defineEnterButton4;
    @FXML private Button carEnteredOptions1, carEnteredOptions2, carEnteredOptions3, carEnteredOptions4;
    
    @FXML private CheckBox carLeft1, carLeft2, carLeft3, carLeft4;
    @FXML private Button defineLeftButton1, defineLeftButton2, defineLeftButton3, defineLeftButton4;
    @FXML private Button carLeftOptions1, carLeftOptions2, carLeftOptions3, carLeftOptions4;
    
    @FXML private CheckBox chargeAnomaly;
    @FXML private Button caOptions;

    @FXML private CheckBox unlocked;
    @FXML private Button unlockedOptions;
    @FXML private BigDecimalField unlockedDoorsField;
    @FXML private Slider unlockedDoorsSlider;

/*------------------------------------------------------------------------------
 *
 * UI Action Handlers
 * 
 *----------------------------------------------------------------------------*/

    @FXML void enabledEvent(ActionEvent event) {
        // TO DO: Remove this. This should happen automatically by the
        // Trigger which is listening for change events on the property
        // associated with the checkbox
    }
    
    @FXML void defineArea(ActionEvent event) {
        Button b = (Button)event.getSource();
        
        for (GeoTrigger g: geoTriggers) {
            if (b == g.defineArea)  { showAreaDialog(g.prop); return; }
        }
        logger.warning("Unexpected button: " + b.toString());
    }

    @FXML void optionsButton(ActionEvent event) {
        Button b = (Button)event.getSource();
        // Show the options Dialog
        
        if (b == odoHitsOptions) {
            showDialog(ohMessageTarget);
        } else if (b == chargeBecomesOptions) {
            showDialog(csMessageTarget);
        } else if (b == seOptions) {
            showDialog(seMessageTarget);
        } else if (b == socFallsOptions) {
            showDialog(socFallsMessageTarget);
        } else if (b == socHitsOptions) {
            showDialog(socHitsMessageTarget);
        } else if (b == speedHitsOptions) {
            showDialog(shMessageTarget);
        } else if (b == caOptions) {
            showDialog(caMessageTarget);
        } else if (b == unlockedOptions) {
            showDialog(ulMessageTarget);
        } else {
            for (GeoTrigger g: geoTriggers) {
                if (b == g.optionsButton)  { showDialog(g.messageTarget); return; }
            }
            logger.warning("Unexpected button: " + b.toString());
        }
    }
    
    @FXML void csbItemClicked(ActionEvent event) {
        RadioButton rb = (RadioButton)event.getSource();
        StringList s = new StringList();
        if (rb == csbAny && csbAny.isSelected()) {
            s.add("Anything");
            csbCharging.setSelected(false);
            csbComplete.setSelected(false);
            csbDisconnected.setSelected(false);
            csbNoPower.setSelected(false);
            csbStarting.setSelected(false);
            csbStopped.setSelected(false);
        } else {
            if (csbCharging.isSelected()) s.add("Charging");
            if (csbComplete.isSelected()) s.add("Complete");
            if (csbDisconnected.isSelected()) s.add("Disconnected");
            if (csbNoPower.isSelected()) s.add("No Power");
            if (csbStarting.isSelected()) s.add("Starting");
            if (csbStopped.isSelected()) s.add("Stopped");
            csbAny.setSelected(false);
        }
        this.csSelectProp.set(s);
    }
    
    private ChangeListener<StringList> csPropListener = new ChangeListener<StringList>() {
        @Override public void changed(ObservableValue<? extends StringList> ov, StringList t, StringList t1) {
            if (t1.contains("Anything")) {
                csbAny.setSelected(true);
                csbCharging.setSelected(false);
                csbComplete.setSelected(false);
                csbDisconnected.setSelected(false);
                csbNoPower.setSelected(false);
                csbStarting.setSelected(false);
                csbStopped.setSelected(false);
            } else {
                if (t1.contains("Charging")) csbCharging.setSelected(true);
                if (t1.contains("Complete")) csbComplete.setSelected(true);
                if (t1.contains("Disconnected")) csbDisconnected.setSelected(true);
                if (t1.contains("No Power")) csbNoPower.setSelected(true);
                if (t1.contains("Starting")) csbStarting.setSelected(true);
                if (t1.contains("Stopped")) csbStopped.setSelected(true);
            }
        }
    };

    private ChangeListener<Boolean> caListener = new ChangeListener<Boolean>() {
        @Override public void changed(
                ObservableValue<? extends Boolean> ov, Boolean old, Boolean cur) {
            prefs.storage().putBoolean(vinKey(NotifyCAKey), cur);
        }
    };
    
    private ChangeListener<Boolean> ulListener = new ChangeListener<Boolean>() {
        @Override public void changed(
                ObservableValue<? extends Boolean> ov, Boolean old, Boolean cur) {
            prefs.storage().putBoolean(vinKey(NotifyULKey), cur);
        }
    };
    
    private ChangeListener<BigDecimal> ulvListener = new ChangeListener<BigDecimal>() {
        @Override public void changed(
                ObservableValue<? extends BigDecimal> ov, BigDecimal old, BigDecimal cur) {
            prefs.storage().putLong(vinKey(NotifyULValKey), cur.longValue());
        }
    };
    
/*------------------------------------------------------------------------------
 *
 * Methods overriden from BaseController
 * 
 *----------------------------------------------------------------------------*/

    @Override protected void fxInitialize() {
        bindBidrectional(speedHitsField, speedHitsSlider);
        bindBidrectional(socHitsField, socHitsSlider);
        bindBidrectional(socFallsField, socFallsSlider);
        bindBidrectional(unlockedDoorsField, unlockedDoorsSlider);
        
        csSelectProp.addListener(csPropListener);
        
        chargeAnomaly.selectedProperty().addListener(caListener);
        unlocked.selectedProperty().addListener(ulListener);
        unlockedDoorsField.numberProperty().addListener(ulvListener);
        geoTriggers[0] = new GeoTrigger(carEnteredOptions1, defineEnterButton1, carEntered1);
        geoTriggers[1] = new GeoTrigger(carEnteredOptions2, defineEnterButton2, carEntered2);
        geoTriggers[2] = new GeoTrigger(carEnteredOptions3, defineEnterButton3, carEntered3);
        geoTriggers[3] = new GeoTrigger(carEnteredOptions4, defineEnterButton4, carEntered4);
        geoTriggers[4] = new GeoTrigger(carLeftOptions1, defineLeftButton1, carLeft1);
        geoTriggers[5] = new GeoTrigger(carLeftOptions2, defineLeftButton2, carLeft2);
        geoTriggers[6] = new GeoTrigger(carLeftOptions3, defineLeftButton3, carLeft3);
        geoTriggers[7] = new GeoTrigger(carLeftOptions4, defineLeftButton4, carLeft4);
    }

    @Override protected void initializeState() {
            lastOdoCheck = prefs.storage().getDouble(OdoCheckKey, 0);
                    
            socHitsTrigger = new GenericTrigger<>(
                socHits.selectedProperty(), bdHelper,
                "SOC", NotifySOCHitsKey,  GenericTrigger.Predicate.HitsOrExceeds,
                socHitsField.numberProperty(), new BigDecimal(88.0), TypicalDebounce);
            socHitsMessageTarget = new MessageTarget(
                    prefs, vinKey("MT_"+NotifySOCHitsKey), SOCHitSubj, SOCHitMsg);
            
            socFallsTrigger = new GenericTrigger<>(
                socFalls.selectedProperty(), bdHelper,
                "SOC", NotifySOCFallsKey, GenericTrigger.Predicate.FallsBelow,
                socFallsField.numberProperty(), new BigDecimal(50.0), TypicalDebounce);
            socFallsMessageTarget = new MessageTarget(
                    prefs, vinKey("MT_"+NotifySOCFallsKey), SOCFellSubj, SOCFellMsg);
            
            speedHitsTrigger = new GenericTrigger<>(
                speedHits.selectedProperty(), bdHelper,
                "Speed", NotifySpeedKey, GenericTrigger.Predicate.HitsOrExceeds,
                speedHitsField.numberProperty(), new BigDecimal(70.0), SpeedDebounce);
            shMessageTarget = new MessageTarget(
                    prefs, vinKey("MT_"+NotifySpeedKey), SpeedHitSubj, SpeedHitMsg);
            
            odoHitsTrigger = new GenericTrigger<>(
                odoHits.selectedProperty(), bdHelper,
                "Odometer", NotifyOdoKey, GenericTrigger.Predicate.GT,
                odoHitsField.numberProperty(), new BigDecimal(14325), TypicalDebounce);
            ohMessageTarget = new MessageTarget(
                    prefs, vinKey("MT_"+NotifyOdoKey), OdoHitsSubj, OdoHitsMsg);
            
            seTrigger = new GenericTrigger<>(
                schedulerEvent.selectedProperty(), stringHelper,
                "Scheduler", NotifySEKey, GenericTrigger.Predicate.AnyChange,
                new SimpleObjectProperty<>("Anything"), "Anything", 0L);
            seMessageTarget = new MessageTarget(
                    prefs, vinKey("MT_"+NotifySEKey), SchedEventSubj, SchedEventMsg);
            
            csTrigger = new GenericTrigger<>(
                chargeState.selectedProperty(), stringListHelper,
                "Charge State", NotifyCSKey, GenericTrigger.Predicate.Becomes,
                csSelectProp, new StringList("Anything"), 0L);
            csMessageTarget = new MessageTarget(
                    prefs, vinKey("MT_"+NotifyCSKey), ChargeStateSubj, ChargeStateMsg);
            
            for (int i = 0; i < 4; i++) {
                GeoTrigger gt = geoTriggers[i];
                gt.trigger = new GenericTrigger<>(
                    gt.enabled.selectedProperty(), areaHelper,
                    "Enter GeoUtils.CircularArea", NotifyEnterKey+i, GenericTrigger.Predicate.HitsOrExceeds,
                    gt.prop, new GeoUtils.CircularArea(), GeoDebounce);
                gt.messageTarget = new MessageTarget(
                        prefs, vinKey("MT_"+NotifyEnterKey+i), EnterAreaSubj, EnterAreaMsg);
            }
            for (int i = 4; i < 8; i++) {
                GeoTrigger gt = geoTriggers[i];
                gt.trigger = new GenericTrigger<>(
                    gt.enabled.selectedProperty(), areaHelper,
                    "Left GeoUtils.CircularArea", NotifyLeftKey+i, GenericTrigger.Predicate.FallsBelow,
                    gt.prop, new GeoUtils.CircularArea(), GeoDebounce);
                gt.messageTarget = new MessageTarget(
                        prefs, vinKey("MT_"+NotifyLeftKey+i), LeftAreaSubj, LeftAreaMsg);
            }
            for (final GeoTrigger g : geoTriggers) {
                String name = g.prop.get().name;
                if (name != null && !name.isEmpty()) { g.enabled.setText(name); }
                g.prop.addListener(new ChangeListener<GeoUtils.CircularArea>() {
                    @Override public void changed(ObservableValue<? extends GeoUtils.CircularArea> ov, GeoUtils.CircularArea t, GeoUtils.CircularArea t1) {
                        if (t1.name != null && !t1.name.isEmpty()) {
                            g.enabled.setText(t1.name);
                        }
                    }
                });
            }
            
            // Other types of trigger
            ccTrigger = new DeviationTrigger(0.19, 5 * 60 * 1000);
            pcTrigger = new DeviationTrigger(0.19, 5 * 60 * 1000);
            caMessageTarget = new MessageTarget(
                    prefs, vinKey("MT_"+NotifyCAKey), ChargeAnomalySubj, ChargeAnomalyMsg);
            chargeAnomaly.setSelected(
                    prefs.storage().getBoolean(vinKey(NotifyCAKey),
                    false));
            
            unlockedTrigger = new StationaryTrigger(
                    unlocked.selectedProperty(),
                    unlockedDoorsField.numberProperty());
            ulMessageTarget = new MessageTarget(
                    prefs, vinKey("MT_"+NotifyULKey), UnlockedSubj, UnlockedMsg);
            unlocked.setSelected(
                    prefs.storage().getBoolean(vinKey(NotifyULKey),
                    false));
            unlockedDoorsField.numberProperty().set(
                    new BigDecimal(prefs.storage().getLong(
                        vinKey(NotifyULValKey), UnlockedThreshold)));
            checkForUnlocked = false;
            
            startListening();
    }
    
    @Override protected void activateTab() {
        if (vtVehicle.unitType() == Utils.UnitType.Imperial) {
            speedUnitsLabel.setText("mph");
            speedHitsSlider.setMin(0);
            speedHitsSlider.setMax(100);
            speedHitsSlider.setMajorTickUnit(25);
            speedHitsSlider.setMinorTickCount(4);
            odoHitsLabel.setText("miles");
        } else {
            speedUnitsLabel.setText("km/h");
            speedHitsSlider.setMin(0);
            speedHitsSlider.setMax(160);
            speedHitsSlider.setMajorTickUnit(30);
            speedHitsSlider.setMinorTickCount(2);
            odoHitsLabel.setText("km");
        }
    }

    @Override protected void refresh() { }

/*------------------------------------------------------------------------------
 *
 * PRIVATE - Methods related to getting a geographic area from the user
 * 
 *----------------------------------------------------------------------------*/
    
    private void showAreaDialog(ObjectProperty<GeoUtils.CircularArea> areaProp) {
        String apiKey = prefs.useCustomGoogleAPIKey.get() ?
                    prefs.googleAPIKey.get() :
                    Prefs.GoogleMapsAPIKey;

        ChooseLocationDialog cld = ChooseLocationDialog.show(app.stage, areaProp.get(), apiKey);
        if (!cld.cancelled()) {
            areaProp.set(cld.getArea());
        }
    }

    private void showDialog(MessageTarget mt) {
        NotifyOptionsDialog nod = NotifyOptionsDialog.show(
                "Message Options", app.stage, mt.getEmail(), mt.getSubject(), mt.getMessage());
        if (!nod.cancelled()) {
            if (!nod.useDefault()) {
                mt.setEmail(nod.getEmail());
                mt.setSubject(nod.getSubject());
                mt.setMessage(nod.getMessage());
            } else {
                mt.setEmail(null);
                mt.setSubject(null);
                mt.setMessage(null);
            }
            mt.externalize();
        }
    }
    
/*------------------------------------------------------------------------------
 *
 * PRIVATE - Methods for detecting changes and testing triggers
 * 
 *----------------------------------------------------------------------------*/
    
    private void startListening() {
        vtVehicle.chargeState.addTracker(csListener);
        vtVehicle.streamState.addTracker(ssListener);
        vtVehicle.vehicleState.addTracker(vsListener);
        app.schedulerActivity.addTracker(schedListener);
    }
    
    private Runnable schedListener = new Runnable() {
        @Override public void run() {
            if (seTrigger.evalPredicate(app.schedulerActivity.get())) {
                notifyUser(seTrigger, seMessageTarget); }
        }
    };
    
    private Runnable csListener = new Runnable() {
        @Override public synchronized void run() {
            ChargeState cur = vtVehicle.chargeState.get();
            if (cur.chargingState != ChargeState.Status.Unknown) {
                if (csTrigger.evalPredicate(new StringList(cur.chargingState.name()))) {
                    notifyUser(csTrigger, csMessageTarget);
                }
            }
            // Handle other triggers
            if (!cur.fastChargerPresent && chargeAnomaly.isSelected()) {
                if (ccTrigger.evalPredicate(cur.chargerActualCurrent)) {
                    Map<String,String> contextSpecific = Utils.newHashMap(
                        "CUR", String.format("%d", cur.chargerActualCurrent),
                        "TARGET", String.format("%.0f", ccTrigger.getBaseline()),
                        "A_CT", "Charge");
                    notifyUser(contextSpecific, caMessageTarget);
                }
                if (pcTrigger.evalPredicate(cur.chargerPilotCurrent)) {
                    Map<String,String> contextSpecific = Utils.newHashMap(
                        "CUR", String.format("%d", cur.chargerPilotCurrent),
                        "TARGET", String.format("%.0f", pcTrigger.getBaseline()),
                        "A_CT", "Pilot");
                    notifyUser(contextSpecific, caMessageTarget);
                }
            }
        }
    };
            
    private Runnable ssListener = new Runnable() {
        @Override public void run() {
            StreamState cur = vtVehicle.streamState.get();
            if (socHitsTrigger.evalPredicate(new BigDecimal(cur.soc))) {
                notifyUser(socHitsTrigger, socHitsMessageTarget);
            }
            
            if (socFallsTrigger.evalPredicate(new BigDecimal(cur.soc))) {
                notifyUser(socFallsTrigger, socFallsMessageTarget);
            }
            
            double speed = useMiles ? cur.speed : Utils.milesToKm(cur.speed);
            if (speedHitsTrigger.evalPredicate(new BigDecimal(speed))) {
                notifyUser(speedHitsTrigger, shMessageTarget);
            }
            
            if (vtVehicle.inProperUnits(lastOdoCheck) < odoHitsField.getNumber().doubleValue()) {
                double odo = vtVehicle.inProperUnits(cur.odometer);
                if (odoHitsTrigger.evalPredicate(new BigDecimal(odo))) {
                    notifyUser(odoHitsTrigger, ohMessageTarget);
                    // Store in miles, but convert & test relative to the GUI setting
                    prefs.storage().putDouble(OdoCheckKey, cur.odometer);
                    lastOdoCheck = cur.odometer;
                }
            }
            
            GeoUtils.CircularArea curLoc = new GeoUtils.CircularArea(cur.estLat, cur.estLng, 0, "Current Location");
            for (GeoTrigger g : geoTriggers) {
                if (g.trigger.evalPredicate(curLoc)) {
                    notifyUser(g.trigger, g.messageTarget);
                }
            }

            // Handle other triggers
            if (unlockedTrigger.evalPredicate(cur.speed, cur.shiftState())) {
                checkForUnlocked = true;
                updateState(Vehicle.StateType.Vehicle);
            }
        }
    };
    
    private Runnable vsListener = new Runnable() {
        @Override public void run() {
            if (!checkForUnlocked) { return; }
            if (!vtVehicle.vehicleState.get().locked) {
                int minutes = unlockedDoorsField.numberProperty().getValue().intValue();
                Map<String, String> contextSpecific = Utils.newHashMap(
                        "CUR", String.format("%d", minutes),
                        "TARGET", String.format("%d", minutes));
                notifyUser(contextSpecific, ulMessageTarget);
                checkForUnlocked = false;
            }
        }
    };

    private void notifyUser(Map<String,String> contextSpecific, MessageTarget target) {
        String addr = target.getActiveEmail();
        String lower = addr.toLowerCase();  // Don't muck with the original addr.
                                            // URLs are case sensitive
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            (new HTTPAsyncGet(addr)).exec();
        } if (lower.startsWith("command:")) {
            String command = StringUtils.remove(addr, "command:");
            String args = target.getSubject();
            if (args != null) args = (new MessageTemplate(args)).getMessage(
                    app.api, vtVehicle, contextSpecific);
            String stdin = target.getMessage();
            if (stdin != null) stdin = (new MessageTemplate(stdin)).getMessage(
                    app.api, vtVehicle, contextSpecific);
            ThreadManager.get().launchExternal(command, args, stdin, 60 * 1000);
            logger.info("Executing external command for notification: " + command);
        } else {
            MessageTemplate mt = new MessageTemplate(target.getActiveMsg());
            MessageTemplate st = new MessageTemplate(target.getActiveSubj());
            MailGun.get().send(
                addr, st.getMessage(app.api, vtVehicle, contextSpecific),
                mt.getMessage(app.api, vtVehicle, contextSpecific));
        }
    }
    
    private void notifyUser(GenericTrigger t, MessageTarget target) {
        Map<String,String> contextSpecific = Utils.newHashMap(
            "CUR", t.getCurrentVal(),
            "TARGET", t.getTargetVal());
        notifyUser(contextSpecific, target);
    }

    private void bindBidrectional(final BigDecimalField bdf, final Slider slider) {
        bdf.setFormat(new DecimalFormat("##0.0"));
        bdf.setStepwidth(BigDecimal.valueOf(0.5));
        bdf.setNumber(new BigDecimal(Utils.round(slider.getValue(), 1)));

        slider.valueProperty().addListener(new ChangeListener<Number>() {
            @Override public void changed(
                    ObservableValue<? extends Number> ov, Number t, Number t1) {
                double val = Utils.round(t1.doubleValue(), 1);
                slider.setValue(val);
                bdf.setNumber(new BigDecimal(val));
            }
        });

        bdf.numberProperty().addListener(new ChangeListener<BigDecimal>() {
            @Override public void changed(
                    ObservableValue<? extends BigDecimal> ov, BigDecimal t, BigDecimal t1) {
                double val = Utils.round(t1.doubleValue(), 1);
                slider.setValue(val);
                bdf.setNumber(new BigDecimal(val));
            }
        });
    }

    private static class HTTPAsyncGet implements Runnable {
        private static final int timeout = 5 * 1000;
        
        private URLConnection connection;
        
        HTTPAsyncGet(String urlString) {
            try {
                logger.info("HTTPAsyncGet new with url: " + urlString);
                URL url = new URL(urlString);
                
                connection = url.openConnection();
                connection.setConnectTimeout(timeout);
                if (url.getUserInfo() != null) {
                    String basicAuth = "Basic " +  encode(url.getUserInfo().getBytes());
                    connection.setRequestProperty("Authorization", basicAuth);
                }
            } catch (IOException ex) {
                logger.warning("Problem with URL: " + ex);
            }
        }
        
        void exec() {
            logger.info("HTTPAsyncGet exec with url: " + connection.getURL());
            ThreadManager.get().launch(this, "HTTPAsyncGet");
        }
        
        @Override public void run() {
            try {
                logger.info("HTTPAsyncGet run with url: " + connection.getURL());
                connection.getInputStream();
            } catch (IOException ex) {
                logger.warning("Problem with HTTP Get: " + ex);
            }
        }
        
        private String encode(byte[] bytes) { return Utils.toB64(bytes); }
    }
    
/*------------------------------------------------------------------------------
 *
 * Private Methods for internalizing, externalizing, and comparing target values
 * 
 *----------------------------------------------------------------------------*/
    
    private abstract class Persistence<T> implements GenericTrigger.RW<T> {
        @Override public void persist(String key, String value) {
            prefs.storage().put(vinKey(key), value);
        }

        @Override public String load(String key, String dflt) {
            return prefs.storage().get(vinKey(key), "0_" + dflt);
        }
    }
    
    private GenericTrigger.RW<StringList> stringListHelper = new StringListRW();
    private class StringListRW extends Persistence<StringList> {        
        @Override public int compare(StringList value, StringList candidates) {
            if (value == null || value.isEmpty()) return -1;
            if (candidates == null || candidates.isEmpty()) return -1;
            String curVal = value.get(0);
            for (String candidate : candidates) {
                if (candidate.equals(curVal)) return 0;
            }
            return -1;
        }
        
        @Override public String toExternal(StringList list) {
            StringBuilder sb = new StringBuilder();
            int nItems = list.size();
            for (int i = 0; i < nItems; i++) {
                sb.append(list.get(i));
                if (i < nItems-1) sb.append("^");
            }
            return sb.toString();
        }

        @Override public StringList fromExternal(String external) {
            StringList l = new StringList();
            if (external != null) {
                String[] items = external.split("\\^");
                l.addAll(Arrays.asList(items));
            }
            return l;
        }

        @Override public String formatted(StringList list) {
            StringBuilder sb = new StringBuilder();
            int nItems = list.size();
            sb.append('(');
            for (int i = 0; i < nItems; i++) {
                sb.append(list.get(i));
                if (i < nItems-1) sb.append(", ");
            }
            sb.append(')');
            return sb.toString();
        }

        @Override public boolean isAny(StringList value) {
            if (value == null || value.isEmpty()) return false;
            return value.get(0).equals("Anything");
        }
    };
    
    private GenericTrigger.RW<BigDecimal> bdHelper = new BigDecimalRW();
    private class BigDecimalRW extends Persistence<BigDecimal> {
        @Override public String toExternal(BigDecimal value) {
            return String.format(Locale.US, "%3.1f", value.doubleValue());
        }

        @Override public BigDecimal fromExternal(String external) {
            try {
                return new BigDecimal(Double.valueOf(external));
            } catch (NumberFormatException e) {
                logger.warning("Malformed externalized Trigger value: " + external);
                return new BigDecimal(Double.valueOf(50));
            }
        }

        @Override public String formatted(BigDecimal value) {
            return String.format("%3.1f", value.doubleValue());
        }

        @Override public int compare(BigDecimal o1, BigDecimal o2) {
            return o1.compareTo(o2);
        }

        @Override public boolean isAny(BigDecimal value) { return false; }
        
        @Override public void persist(String key, String value) {
            prefs.storage().put(vinKey(key), value);
        }

        @Override public String load(String key, String dflt) {
            return prefs.storage().get(vinKey(key), "0_" + dflt);
        }
    };
    
    private GenericTrigger.RW<String> stringHelper = new StringRW();
    private class StringRW extends Persistence<String> {
        
        @Override public String toExternal(String value) { return value; }
        @Override public String fromExternal(String external) { return external; }
        @Override public String formatted(String value) { return value; }
        @Override public int compare(String o1, String o2) {
            return o1.compareTo(o2);
        }
        @Override public boolean isAny(String value) { return false; }
        @Override public void persist(String key, String value) {
            prefs.storage().put(vinKey(key), value);
        }

        @Override public String load(String key, String dflt) {
            return prefs.storage().get(vinKey(key), "0_" + dflt);
        }
    };
    
    private GenericTrigger.RW<GeoUtils.CircularArea> areaHelper = new AreaRW();
    private class AreaRW extends Persistence<GeoUtils.CircularArea> {
        @Override public String toExternal(GeoUtils.CircularArea value) {
            return String.format(Locale.US, "%3.5f^%3.5f^%2.1f^%s",
                    value.lat, value.lng, value.radius, value.name);
        }

        @Override public GeoUtils.CircularArea fromExternal(String external) {
            String[] elements = external.split("\\^");
            if (elements.length != 4) {
                logger.severe("Malformed GeoUtils.CircularArea String: " + external);
                return new GeoUtils.CircularArea();
            }
            double lat, lng, radius;
            try {
                lat = Double.valueOf(elements[0]);
                lng = Double.valueOf(elements[1]);
                radius = Double.valueOf(elements[2]);
                return new GeoUtils.CircularArea(lat, lng, radius, elements[3]);
            } catch (NumberFormatException e) {
                logger.severe("Malformed GeoUtils.CircularArea String: " + external);
                return new GeoUtils.CircularArea();
            }
        }

        @Override public String formatted(GeoUtils.CircularArea value) {
            return String.format("[%s] within %2.1f meters", value.name, value.radius);
        }

        @Override public int compare(GeoUtils.CircularArea o1, GeoUtils.CircularArea o2) {
            return o1.compareTo(o2);
        }

        @Override public boolean isAny(GeoUtils.CircularArea value) { return false; }
        @Override public void persist(String key, String value) {
            prefs.storage().put(vinKey(key), value);
        }

        @Override public String load(String key, String dflt) {
            return prefs.storage().get(vinKey(key), "0_" + dflt);
        }
    };
}