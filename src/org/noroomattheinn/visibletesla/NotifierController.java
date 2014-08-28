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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import jfxtras.labs.scene.control.BigDecimalField;
import org.noroomattheinn.tesla.ChargeState;
import org.noroomattheinn.tesla.SnapshotState;
import org.noroomattheinn.tesla.Tesla;
import org.noroomattheinn.utils.Utils;
import org.noroomattheinn.visibletesla.dialogs.ChooseLocationDialog;
import org.noroomattheinn.visibletesla.dialogs.DialogUtils;
import org.noroomattheinn.visibletesla.dialogs.NotifyOptionsDialog;
import org.noroomattheinn.visibletesla.trigger.DeviationTrigger;
import org.noroomattheinn.visibletesla.trigger.Predicate;
import org.noroomattheinn.visibletesla.trigger.Trigger;
import org.noroomattheinn.visibletesla.trigger.RW;

/**
 * NotifierController
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */

public class NotifierController extends BaseController {

/*------------------------------------------------------------------------------
 *
 * Constants and Enums
 * 
 *----------------------------------------------------------------------------*/

    private static final long TypicalDebounce = 10 * 60 * 1000; // 10 Minutes
    private static final long SpeedDebounce = 30 * 60 * 1000;   // 30 Minutes
    private static final long GeoDebounce = 30 * 1000;          // 30 Seconds

    private static final String NotifySEKey = "NOTIFY_SE";
    private static final String NotifyCSKey = "NOTIFY_CS";
    private static final String NotifyCAKey = "NOTIFY_CA";
    private static final String NotifySpeedKey = "NOTIFY_SPEED";
    private static final String NotifySOCHitsKey = "NOTIFY_SOC_HITS";
    private static final String NotifySOCFallsKey = "NOTIFY_SOC_FALLS";
    private static final String NotifyEnterKey = "NOTIFY_ENTER_AREA";
    private static final String NotifyLeftKey = "NOTIFY_LEFT_AREA";
    
    private static final String SOCHitSubj = "SOC: {{CUR}}%";
    private static final String SOCHitMsg = "SOC Hit or Exceeded: {{TARGET}}% ({{CUR}}%)";
    private static final String SOCFellSubj = "SOC: {{CUR}}%";
    private static final String SOCFellMsg = "SOC Fell Below: {{TARGET}}% ({{CUR}}%)";
    private static final String SpeedHitSubj = "Speed: {{SPEED}} {{S_UNITS}}";
    private static final String SpeedHitMsg = "Speed Hit or Exceeded: {{TARGET}} {{S_UNITS}} ({{SPEED}})";
    private static final String SchedEventSubj = "Scheduled Event: {{CUR}}";
    private static final String SchedEventMsg = "Scheduled Event: {{CUR}})";
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
    
    private Trigger<BigDecimal> speedHitsTrigger;
    private MessageTarget       shMessageTarget;
    
    private Trigger<String>     seTrigger;
    private MessageTarget       seMessageTarget;
    
    private Trigger<BigDecimal> socHitsTrigger;
    private MessageTarget       socHitsMessageTarget;

    private Trigger<BigDecimal> socFallsTrigger;
    private MessageTarget       socFallsMessageTarget;
    
    private Trigger<String>     csTrigger;
    private MessageTarget       csMessageTarget;

    private List<Trigger>       allTriggers = new ArrayList<>();
    private boolean             useMiles = true;
    
    private Trigger<Area>       enteredTrigger;
    private MessageTarget       enteredMessageTarget;
    private ObjectProperty<Area> enterAreaProp = new SimpleObjectProperty<>(new Area());
            
    private Trigger<Area>       leftTrigger;
    private MessageTarget       leftMessageTarget;
    private ObjectProperty<Area> leftAreaProp = new SimpleObjectProperty<>(new Area());
    
    // Charge Anomoly Triggers
    private DeviationTrigger    ccTrigger;
    private DeviationTrigger    pcTrigger;
    private MessageTarget       caMessageTarget;
    
/*------------------------------------------------------------------------------
 *
 * UI Elements
 * 
 *----------------------------------------------------------------------------*/
        
    @FXML private CheckBox chargeState;
    @FXML private ComboBox<String> csOptions;
    @FXML private Button chargeBecomesOptions;
    
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

    @FXML private CheckBox speedHits;
    @FXML private BigDecimalField speedHitsField;
    @FXML private Slider speedHitsSlider;
    @FXML private Label speedUnitsLabel;
    @FXML private Button speedHitsOptions;

    @FXML private CheckBox carEntered;
    @FXML private Button defineEnterButton;
    @FXML private Button carEnteredOptions;
    
    @FXML private CheckBox carLeft;
    @FXML private Button defineLeftButton;
    @FXML private Button carLeftOptions;
    
    @FXML private CheckBox chargeAnomaly;
    @FXML private Button caOptions;

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
        
        if (b == defineEnterButton) {
            showAreaDialog(enterAreaProp);
        } else if (b == defineLeftButton) {
            showAreaDialog(leftAreaProp);
        } else {
            Tesla.logger.warning("Unexpected button: " + b.toString());
        }
    }

    @FXML void optionsButton(ActionEvent event) {
        Button b = (Button)event.getSource();
        // Show the options Dialog
        
        if (b == chargeBecomesOptions) {
            showDialog(csMessageTarget);
        } else if (b == seOptions) {
            showDialog(seMessageTarget);
        } else if (b == socFallsOptions) {
            showDialog(socFallsMessageTarget);
        } else if (b == socHitsOptions) {
            showDialog(socHitsMessageTarget);
        } else if (b == speedHitsOptions) {
            showDialog(shMessageTarget);
        } else if (b == carEnteredOptions) {
            showDialog(enteredMessageTarget);
        } else if (b == carLeftOptions) {
            showDialog(leftMessageTarget);
        } else if (b == caOptions) {
            showDialog(caMessageTarget);
        } else {
            Tesla.logger.warning("Unexpected button: " + b.toString());
        }
    }
    
    private ChangeListener<Boolean> caListener = new ChangeListener<Boolean>() {
        @Override public void changed(
                ObservableValue<? extends Boolean> ov, Boolean old, Boolean cur) {
            appContext.persistentState.putBoolean(prefKey(NotifyCAKey), cur);
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
        chargeAnomaly.selectedProperty().addListener(caListener);
    }

    @Override protected void initializeState() {
            socHitsTrigger = new Trigger<>(
                appContext, socHits.selectedProperty(), RW.bdHelper,
                "SOC", NotifySOCHitsKey,  Predicate.Type.HitsOrExceeds,
                socHitsField.numberProperty(), new BigDecimal(88.0), TypicalDebounce);
            socHitsMessageTarget = new MessageTarget(
                    appContext, NotifySOCHitsKey, SOCHitSubj, SOCHitMsg);
            
            socFallsTrigger = new Trigger<>(
                appContext, socFalls.selectedProperty(), RW.bdHelper,
                "SOC", NotifySOCFallsKey, Predicate.Type.FallsBelow,
                socFallsField.numberProperty(), new BigDecimal(50.0), TypicalDebounce);
            socFallsMessageTarget = new MessageTarget(
                    appContext, NotifySOCFallsKey, SOCFellSubj, SOCFellMsg);
            
            speedHitsTrigger = new Trigger<>(
                appContext, speedHits.selectedProperty(), RW.bdHelper,
                "Speed", NotifySpeedKey, Predicate.Type.HitsOrExceeds,
                speedHitsField.numberProperty(), new BigDecimal(70.0), SpeedDebounce);
            shMessageTarget = new MessageTarget(
                    appContext, NotifySpeedKey, SpeedHitSubj, SpeedHitMsg);
            
            seTrigger = new Trigger<>(
                appContext, schedulerEvent.selectedProperty(), RW.stringHelper,
                "Scheduler", NotifySEKey, Predicate.Type.AnyChange,
                new SimpleObjectProperty<>("Anything"), "Anything", 0L);
            seMessageTarget = new MessageTarget(
                    appContext, NotifySEKey, SchedEventSubj, SchedEventMsg);

            csTrigger = new Trigger<>(
                appContext, chargeState.selectedProperty(), RW.stringHelper,
                "Charge State", NotifyCSKey, Predicate.Type.Becomes,
                csOptions.valueProperty(), csOptions.itemsProperty().get().get(0), 0L);
            csMessageTarget = new MessageTarget(
                    appContext, NotifyCSKey, ChargeStateSubj, ChargeStateMsg);
            
            enteredTrigger = new Trigger<>(
                appContext, carEntered.selectedProperty(), RW.areaHelper,
                "Enter Area", NotifyEnterKey, Predicate.Type.HitsOrExceeds,
                enterAreaProp, new Area(), GeoDebounce);
            this.enteredMessageTarget = new MessageTarget(
                    appContext, NotifyEnterKey, EnterAreaSubj, EnterAreaMsg);
            
            leftTrigger = new Trigger<>(
                appContext, carLeft.selectedProperty(), RW.areaHelper,
                "Left Area", NotifyLeftKey, Predicate.Type.FallsBelow,
                leftAreaProp, new Area(), GeoDebounce);
            this.leftMessageTarget = new MessageTarget(
                    appContext, NotifyLeftKey, LeftAreaSubj, LeftAreaMsg);
            
            allTriggers.addAll(Arrays.asList(
                    speedHitsTrigger, socHitsTrigger, socFallsTrigger,
                    csTrigger, seTrigger, enteredTrigger, leftTrigger));
            
            for (Trigger t : allTriggers) { t.init(); }

            // Other types of trigger
            ccTrigger = new DeviationTrigger(0.19, 5 * 60 * 1000);
            pcTrigger = new DeviationTrigger(0.19, 5 * 60 * 1000);
            caMessageTarget = new MessageTarget(
                    appContext, NotifyCAKey, ChargeAnomalySubj, ChargeAnomalyMsg);
            chargeAnomaly.setSelected(
                    appContext.persistentState.getBoolean(prefKey(NotifyCAKey),
                    false));
            
            startListening();
    }
    
    @Override protected void activateTab() {
        if (appContext.utils.unitType() == Utils.UnitType.Imperial) {
            speedUnitsLabel.setText("mph");
            speedHitsSlider.setMin(0);
            speedHitsSlider.setMax(100);
            speedHitsSlider.setMajorTickUnit(25);
            speedHitsSlider.setMinorTickCount(4);
        } else {
            speedUnitsLabel.setText("km/h");
            speedHitsSlider.setMin(0);
            speedHitsSlider.setMax(160);
            speedHitsSlider.setMajorTickUnit(30);
            speedHitsSlider.setMinorTickCount(2);
        }
    }

    @Override protected void refresh() { }

/*------------------------------------------------------------------------------
 *
 * PRIVATE - Methods related to getting a geographic area from the user
 * 
 *----------------------------------------------------------------------------*/
    
    private void showAreaDialog(ObjectProperty<Area> areaProp) {
        Map<Object, Object> props = new HashMap<>();
        props.put(ChooseLocationDialog.AREA_KEY, areaProp.get());
        props.put(ChooseLocationDialog.API_KEY,
                appContext.prefs.useCustomGoogleAPIKey.get() ?
                    appContext.prefs.googleAPIKey.get() :
                    AppContext.GoogleMapsAPIKey);

        DialogUtils.DialogController dc = DialogUtils.displayDialog(
                getClass().getResource("dialogs/ChooseLocation.fxml"),
                "Select an Area", appContext.stage, props);

        ChooseLocationDialog cld = Utils.cast(dc);
        if (!cld.cancelled()) {
            areaProp.set(cld.getArea());
        }
    }

    private void showDialog(MessageTarget mt) {
        Map<Object, Object> props = new HashMap<>();
        props.put("EMAIL", mt.getEmail());
        props.put("SUBJECT", mt.getSubject());
        props.put("MESSAGE", mt.getMessage());

        DialogUtils.DialogController dc = DialogUtils.displayDialog(
                getClass().getResource("dialogs/NotifyOptionsDialog.fxml"),
                "Message Options", appContext.stage, props);
        if (dc == null) {
            Tesla.logger.warning("Can't display \"Message Options\" dialog");
            mt.setEmail(null); 
            mt.setSubject(null);
            mt.setMessage(null);
            mt.externalize();
            return;
        }

        NotifyOptionsDialog nod = Utils.cast(dc);
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
        appContext.lastKnownChargeState.addListener(csListener);
        appContext.lastKnownSnapshotState.addListener(ssListener);
        appContext.schedulerActivityReport.addListener(schedListener);
    }
    
    private ChangeListener<String> schedListener = new ChangeListener<String>() {
        @Override public void changed(
                ObservableValue<? extends String> ov, String old, String cur) {
            if (cur == null) return;
            Trigger.Result result = seTrigger.evalPredicate(cur);
            if (result != null)
                notifyUser(result, seMessageTarget);
        }
    };

    private ChangeListener<ChargeState.State> csListener = new ChangeListener<ChargeState.State>() {
        @Override public synchronized void  changed(
                ObservableValue<? extends ChargeState.State> ov,
                ChargeState.State old, ChargeState.State cur) {
            Trigger.Result result = csTrigger.evalPredicate(cur.chargingState.name());
            if (result != null) {
                notifyUser(result, csMessageTarget);
            }
            // Handle other triggers
            if (!cur.fastChargerPresent && chargeAnomaly.isSelected()) {
                if (ccTrigger.handleSample(cur.chargerActualCurrent)) {
                    Map<String,String> contextSpecific = Utils.newHashMap(
                        "CUR", String.format("%d", cur.chargerActualCurrent),
                        "TARGET", String.format("%.0f", ccTrigger.getBaseline()),
                        "A_CT", "Charge");
                    notifyUser(contextSpecific, caMessageTarget);
                }
                if (pcTrigger.handleSample(cur.chargerPilotCurrent)) {
                    Map<String,String> contextSpecific = Utils.newHashMap(
                        "CUR", String.format("%d", cur.chargerPilotCurrent),
                        "TARGET", String.format("%.0f", pcTrigger.getBaseline()),
                        "A_CT", "Pilot");
                    notifyUser(contextSpecific, caMessageTarget);
                }
            }
        }
    };
            
    private ChangeListener<SnapshotState.State> ssListener = new ChangeListener<SnapshotState.State>() {
        long lastSpeedNotification = 0;
        
        @Override public void changed(
                ObservableValue<? extends SnapshotState.State> ov,
                SnapshotState.State old, SnapshotState.State cur) {
            Trigger.Result result = socHitsTrigger.evalPredicate(new BigDecimal(cur.soc));
            if (result != null) { notifyUser(result, socHitsMessageTarget); }
            
            result = socFallsTrigger.evalPredicate(new BigDecimal(cur.soc));
            if (result != null) { notifyUser(result, socFallsMessageTarget); }
            
            double speed = useMiles ? cur.speed : Utils.mToK(cur.speed);
            result = speedHitsTrigger.evalPredicate(new BigDecimal(speed));
            if (result != null) { notifyUser(result, shMessageTarget); }
            
            Area curLoc = new Area(cur.estLat, cur.estLng, 0, "Current Location");
            result = enteredTrigger.evalPredicate(curLoc);
            if (result != null) { notifyUser(result, enteredMessageTarget); }
            result = leftTrigger.evalPredicate(curLoc);
            if (result != null) { notifyUser(result, leftMessageTarget); }
        }
    };
    
    private void notifyUser(Map<String,String> contextSpecific, MessageTarget target) {
        String addr = target.getActiveEmail();
        String lower = addr.toLowerCase();  // Don't muck with the original addr.
                                            // URLs are case sensitive
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            (new HTTPAsyncGet(addr)).exec();
        } else {
            MessageTemplate mt = new MessageTemplate(appContext, target.getActiveMsg());
            MessageTemplate st = new MessageTemplate(appContext, target.getActiveSubj());
            appContext.utils.sendNotification(
                addr, st.getMessage(contextSpecific),
                mt.getMessage(contextSpecific));
        }
    }
    
    private void notifyUser(Trigger.Result r, MessageTarget target) {
        Map<String,String> contextSpecific = Utils.newHashMap(
            "CUR", r.getCurrentValue(),
            "TARGET", r.getTarget());
        notifyUser(contextSpecific, target);
    }

    private void bindBidrectional(final BigDecimalField bdf, final Slider slider) {
        bdf.setFormat(new DecimalFormat("##0.0"));
        bdf.setStepwidth(BigDecimal.valueOf(0.5));
        bdf.setNumber(new BigDecimal(osd(slider.getValue())));

        slider.valueProperty().addListener(new ChangeListener<Number>() {
            @Override public void changed(
                    ObservableValue<? extends Number> ov, Number t, Number t1) {
                double val = osd(t1.doubleValue());
                slider.setValue(val);
                bdf.setNumber(new BigDecimal(val));
            }
        });

        bdf.numberProperty().addListener(new ChangeListener<BigDecimal>() {
            @Override public void changed(
                    ObservableValue<? extends BigDecimal> ov, BigDecimal t, BigDecimal t1) {
                double val = osd(t1.doubleValue());
                slider.setValue(val);
                bdf.setNumber(new BigDecimal(val));
            }
        });
    }

    private double osd(double val) {
        return Math.round(val * 10.0)/10.0;
    }
    
    
    private class HTTPAsyncGet implements Runnable {
        private static final int timeout = 5 * 1000;
        
        private URLConnection connection;
        
        HTTPAsyncGet(String urlString) {
            try {
                Tesla.logger.info("HTTPAsyncGet new with url: " + urlString);
                URL url = new URL(urlString);
                
                connection = url.openConnection();
                connection.setConnectTimeout(timeout);
                if (url.getUserInfo() != null) {
                    String basicAuth = "Basic " +  encode(url.getUserInfo().getBytes());
                    connection.setRequestProperty("Authorization", basicAuth);
                }
            } catch (IOException ex) {
                Tesla.logger.warning("Problem with URL: " + ex);
            }
        }
        
        void exec() {
            Tesla.logger.info("HTTPAsyncGet exec with url: " + connection.getURL());
            appContext.tm.launch(this, "HTTPAsyncGet");
        }
        
        @Override public void run() {
            try {
                Tesla.logger.info("HTTPAsyncGet run with url: " + connection.getURL());
                connection.getInputStream();
            } catch (IOException ex) {
                Tesla.logger.warning("Problem with HTTP Get: " + ex);
            }
        }
        
        private String encode(byte[] bytes) {
            return javax.xml.bind.DatatypeConverter.printBase64Binary(bytes);
        }
    }
}