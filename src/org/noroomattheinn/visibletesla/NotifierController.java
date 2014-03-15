/*
 * NotifierController.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Dec 6, 2013
 */

package org.noroomattheinn.visibletesla;

import java.math.BigDecimal;
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
import org.noroomattheinn.tesla.Vehicle;
import org.noroomattheinn.utils.Utils;
import org.noroomattheinn.visibletesla.dialogs.DialogUtils;
import org.noroomattheinn.visibletesla.dialogs.GeoOptionsDialog;
import org.noroomattheinn.visibletesla.dialogs.NotifyOptionsDialog;
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

    private static final String NotifySEKey = "NOTIFY_SE";
    private static final String NotifyCSKey = "NOTIFY_CS";
    private static final String NotifySpeedKey = "NOTIFY_SPEED";
    private static final String NotifySOCHitsKey = "NOTIFY_SOC_HITS";
    private static final String NotifySOCFallsKey = "NOTIFY_SOC_FALLS";
    private static final String NotifyEnterKey = "NOTIFY_ENTER_AREA";
    private static final String NotifyLeftKey = "NOTIFY_LEFT_AREA";
    
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
        } else {
            Tesla.logger.warning("Unexpected button: " + b.toString());
        }
    }

    
/*------------------------------------------------------------------------------
 *
 * Methods overriden from BaseController
 * 
 *----------------------------------------------------------------------------*/

    @Override protected void fxInitialize() {
        bindBidrectional(speedHitsField, speedHitsSlider);
        bindBidrectional(socHitsField, socHitsSlider);
        bindBidrectional(socFallsField, socFallsSlider);
    }

    @Override protected void prepForVehicle(Vehicle v) {
        if (differentVehicle()) {
            // TO DO: Remove old triggers!

            socHitsTrigger = new Trigger<>(
                appContext, socHits.selectedProperty(), RW.bdHelper,
                "SOC", NotifySOCHitsKey,  Predicate.Type.HitsOrExceeds,
                socHitsField.numberProperty(), new BigDecimal(88.0));
            socHitsMessageTarget = new MessageTarget(appContext, NotifySOCHitsKey);
            
            
            socFallsTrigger = new Trigger<>(
                appContext, socFalls.selectedProperty(), RW.bdHelper,
                "SOC", NotifySOCFallsKey, Predicate.Type.FallsBelow,
                socFallsField.numberProperty(), new BigDecimal(50.0));
            socFallsMessageTarget = new MessageTarget(appContext, NotifySOCFallsKey);
            
            speedHitsTrigger = new Trigger<>(
                appContext, speedHits.selectedProperty(), RW.bdHelper,
                "Speed", NotifySpeedKey, Predicate.Type.HitsOrExceeds,
                speedHitsField.numberProperty(), new BigDecimal(70.0));
            shMessageTarget = new MessageTarget(appContext, NotifySpeedKey);
            
            seTrigger = new Trigger<>(
                appContext, schedulerEvent.selectedProperty(), RW.stringHelper,
                "Scheduler", NotifySEKey, Predicate.Type.AnyChange,
                new SimpleObjectProperty<>("Anything"), "Anything");
            seMessageTarget = new MessageTarget(appContext, NotifySEKey);

            csTrigger = new Trigger<>(
                appContext, chargeState.selectedProperty(), RW.stringHelper,
                "Charge State", NotifyCSKey, Predicate.Type.Becomes,
                csOptions.valueProperty(), csOptions.itemsProperty().get().get(0));
            csMessageTarget = new MessageTarget(appContext, NotifyCSKey);
            
            enteredTrigger = new Trigger<>(
                appContext, carEntered.selectedProperty(), RW.areaHelper,
                "Enter Area", NotifyEnterKey, Predicate.Type.HitsOrExceeds,
                enterAreaProp, new Area());
            this.enteredMessageTarget = new MessageTarget(appContext, NotifyEnterKey);
            
            leftTrigger = new Trigger<>(
                appContext, carLeft.selectedProperty(), RW.areaHelper,
                "Left Area", NotifyLeftKey, Predicate.Type.FallsBelow,
                leftAreaProp, new Area());
            this.leftMessageTarget = new MessageTarget(appContext, NotifyLeftKey);
            
            allTriggers.addAll(Arrays.asList(
                    speedHitsTrigger, socHitsTrigger, socFallsTrigger,
                    csTrigger, seTrigger, enteredTrigger, leftTrigger));
            
            for (Trigger t : allTriggers) { t.init(); }

            startListening();
        }
        
        useMiles = appContext.lastKnownGUIState.get().distanceUnits.equalsIgnoreCase("mi/hr");
        if (appContext.simulatedUnits.get() != null)
            useMiles = (appContext.simulatedUnits.get() == Utils.UnitType.Imperial);
        if (useMiles) {
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

    @Override protected void reflectNewState() {}

/*------------------------------------------------------------------------------
 *
 * PRIVATE - Methods related to getting a geographic area from the user
 * 
 *----------------------------------------------------------------------------*/
    
    private void showAreaDialog(ObjectProperty<Area> areaProp) {
        Map<Object, Object> props = new HashMap<>();
        props.put("AREA", areaProp.get());
        props.put("APP_CONTEXT", appContext);

        DialogUtils.DialogController dc = DialogUtils.displayDialog(
                getClass().getResource("dialogs/GeoOptionsDialog.fxml"),
                "Select an Area", appContext.stage, props);

        GeoOptionsDialog god = Utils.cast(dc);
        if (!god.cancelled()) {
            areaProp.set(god.getArea());
        }
    }

    private void showDialog(MessageTarget mt) {
        Map<Object, Object> props = new HashMap<>();
        props.put("EMAIL", mt.getEmail());
        props.put("SUBJECT", mt.getSubject());

        DialogUtils.DialogController dc = DialogUtils.displayDialog(
                getClass().getResource("dialogs/NotifyOptionsDialog.fxml"),
                "Message Options", appContext.stage, props);
        if (dc == null) {
            Tesla.logger.warning("Can't display \"Message Options\" dialog");
            mt.setEmail(null); 
            mt.setSubject(null);
            mt.externalize();
            return;
        }

        NotifyOptionsDialog nod = Utils.cast(dc);
        if (!nod.cancelled()) {
            if (!nod.useDefault()) {
                mt.setEmail(nod.getEmail());
                mt.setSubject(nod.getSubject());
            } else {
                mt.setEmail(null);
                mt.setSubject(null);
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
            if (result != null)
                notifyUser(result, csMessageTarget);
        }
    };
            
    private ChangeListener<SnapshotState.State> ssListener = new ChangeListener<SnapshotState.State>() {
        long lastSpeedNotification = 0;
        
        @Override public void changed(
                ObservableValue<? extends SnapshotState.State> ov,
                SnapshotState.State old, SnapshotState.State cur) {
            Trigger.Result result = socHitsTrigger.evalPredicate(new BigDecimal(cur.soc));
            if (result != null)
                notifyUser(result, socHitsMessageTarget);
            
            result = socFallsTrigger.evalPredicate(new BigDecimal(cur.soc));
            if (result != null)
                notifyUser(result, socFallsMessageTarget);
            
            double speed = useMiles ? cur.speed : Utils.mToK(cur.speed);
            result = speedHitsTrigger.evalPredicate(new BigDecimal(speed));
            if (result != null) {
                if (System.currentTimeMillis() - lastSpeedNotification > 30 * 60 * 1000) {
                    notifyUser(result, shMessageTarget);
                    lastSpeedNotification = System.currentTimeMillis();
                }
            }
            
            Area curLoc = new Area(cur.estLat, cur.estLng, 0, "Current Location");
            result = enteredTrigger.evalPredicate(curLoc);
            if (result != null) {
                Area a = enterAreaProp.get();
                String msg = String.format("Arrived at [%s] within %3.0f meters", a.name, a.radius);
                notifyUser(msg, enteredMessageTarget);
            }
            result = leftTrigger.evalPredicate(curLoc);
            if (result != null) {
                Area a = leftAreaProp.get();
                String msg = String.format("Left [%s] within %3.0f meters", a.name, a.radius);
                notifyUser(msg, leftMessageTarget);
            }
        }
    };
    
    private void notifyUser(String msg, MessageTarget mt) {
        String addr = mt.address == null ? appContext.prefs.notificationAddress.get() : mt.address;
        if (mt.tag == null) {
            appContext.sendNotification(addr, msg);
        } else {
            appContext.sendNotification(addr, mt.tag, msg);
        }
    }

    private void notifyUser(Trigger.Result result, MessageTarget mt) {
        notifyUser(result.defaultMessage(), mt);
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
    
    private class MessageTarget {
        private String address;
        private String tag;
        
        private AppContext ac;
        private String theKey;
        
        MessageTarget(AppContext ac, String baseKey) {
            this.ac = ac;
            this.theKey = key(baseKey);
            this.internalize();
        }
        
        String getEmail() { return address; }
        String getSubject() { return tag; }
        void setEmail(String email) {  address = email; }
        void setSubject(String subject) { tag = subject; }
        
        final void externalize() {
            String encoded = String.format("%s_%s",
                    address == null ? "null" : encodeUnderscore(address),
                    tag == null ? "null" : encodeUnderscore(tag));
            ac.persistentState.put(theKey, encoded);
        }
        
        final void internalize() {
            String encoded = ac.persistentState.get(theKey, "");
            if (encoded.isEmpty()) {    // lookupAndShowmessage target has been set
                address = null;
                tag = null;
                return;
            }
            String[] elements = encoded.split("_");
            if (elements.length != 2) {
                Tesla.logger.warning("Malformed MessageTarget String: " + encoded);
                address = null;
                tag = null;
            } else {
                address = elements[0].equals("null") ? null : decodeUnderscore(elements[0]);
                tag = elements[1].equals("null") ? null : decodeUnderscore(elements[1]);
            }
        }
        
        private String encodeUnderscore(String input) {
            return input.replace("_", "&#95;");
        }
        
        private String decodeUnderscore(String input) {
            return input.replace("&#95;", "_");
        }
        
        private String key(String base) {
            return vehicle.getVIN()+"_MT_"+base;
        }

    }
}