/*
 * Prefs.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Nov 17, 2013
 */
package org.noroomattheinn.visibletesla;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;

/**
 * Prefs - Stores and Manages Preferences data for all of the tabs.
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class Prefs {
    
    private AppContext appContext;
    
    public Prefs(AppContext ac) {
        appContext = ac;
        loadGeneralPrefs();
        loadGraphPrefs();
        loadSchedulerPrefs();
    }
    
/*------------------------------------------------------------------------------
 *
 * General Application Preferences
 * 
 *----------------------------------------------------------------------------*/
    
    public IntegerProperty  idleThresholdInMinutes = new SimpleIntegerProperty();
    public BooleanProperty  storeFilesWithApp = new SimpleBooleanProperty();
    public BooleanProperty  wakeOnTabChange = new SimpleBooleanProperty();
    public BooleanProperty  offerExperimental = new SimpleBooleanProperty();
    public BooleanProperty  enableProxy = new SimpleBooleanProperty();
    public StringProperty   proxyHost = new SimpleStringProperty();
    public IntegerProperty  proxyPort = new SimpleIntegerProperty();
    
    private static final String AppFilesFolderKey = "APP_AFF";
    private static final String WakeOnTCKey = "APP_WAKE_ON_TC";
    private static final String IdleThresholdKey = "APP_IDLE_THRESHOLD";
    private static final String OfferExpKey = "APP_OFFER_EXP";
    private static final String EnableProxyKey = "APP_ENABLE_PROXY";
    private static final String ProxyHostKey = "APP_PROXY_HOST";
    private static final String ProxyPortKey = "APP_PROXY_PORT";
    
    private void loadGeneralPrefs() {
        booleanPref(AppFilesFolderKey, storeFilesWithApp, false);
        booleanPref(WakeOnTCKey, wakeOnTabChange, true);
        booleanPref(OfferExpKey, offerExperimental, false);
        integerPref(IdleThresholdKey, idleThresholdInMinutes, 15);
        booleanPref(EnableProxyKey, enableProxy, false);
        stringPref(ProxyHostKey, proxyHost, "");
        integerPref(ProxyPortKey, proxyPort, 8080);
    }
    
/*------------------------------------------------------------------------------
 *
 * Preferences related to the Graphs Tab
 * 
 *----------------------------------------------------------------------------*/
    
    public StringProperty   loadPeriod = new SimpleStringProperty();
    public BooleanProperty  incrementalLoad = new SimpleBooleanProperty();
    
    private static final String GraphPeriodPrefKey = "GRAPH_PERIOD";
    private static final String GraphIncLoadPrefKey = "GRAPH_INC_LOAD";
    
    private void loadGraphPrefs() {
        stringPref(GraphPeriodPrefKey, loadPeriod, GraphController.LoadPeriod.All.name());
        booleanPref(GraphIncLoadPrefKey, incrementalLoad, true);
    }
    
/*------------------------------------------------------------------------------
 *
 * Preferences related to the Scheduler Tab
 * 
 *----------------------------------------------------------------------------*/
    
    public IntegerProperty lowChargeValue = new SimpleIntegerProperty();
    public BooleanProperty safeIncludesMinCharge = new SimpleBooleanProperty();
    public BooleanProperty safeIncludesPluggedIn = new SimpleBooleanProperty();
    
    private static final String SchedMinChargeKey = "SCHED_MIN_CHARGE";
    private static final String SchedSafeIncludesBattery = "SCHED_SAFE_BATTERY";
    private static final String SchedSafeIncludesPlugged = "SCHED_SAFE_PLUGGED_IN";
    
    private void loadSchedulerPrefs() {
        integerPref(SchedMinChargeKey, lowChargeValue, 50);
        booleanPref(SchedSafeIncludesBattery, safeIncludesMinCharge, true);
        booleanPref(SchedSafeIncludesPlugged, safeIncludesPluggedIn, false);
    }
    
/*------------------------------------------------------------------------------
 *
 * PRIVATE - Convenience Methods for handling preferences
 * 
 *----------------------------------------------------------------------------*/

    private void integerPref(final String key, IntegerProperty property, int defaultValue) {
        property.set(appContext.prefs.getInt(key, defaultValue));
        property.addListener(new ChangeListener<Number>() {
            @Override public void changed(
                ObservableValue<? extends Number> ov, Number old, Number cur) {
                    appContext.prefs.putInt(key, cur.intValue());
            }
        });
    }
    
    private void booleanPref(final String key, BooleanProperty property, boolean defaultValue) {
        property.set(appContext.prefs.getBoolean(key, defaultValue));
        property.addListener(new ChangeListener<Boolean>() {
            @Override public void changed(
                ObservableValue<? extends Boolean> ov, Boolean old, Boolean cur) {
                    appContext.prefs.putBoolean(key, cur);
            }
        });
    }
    
    private void stringPref(final String key, StringProperty property, String defaultValue) {
        property.set(appContext.prefs.get(key, defaultValue));
        property.addListener(new ChangeListener<String>() {
            @Override public void changed(
                ObservableValue<? extends String> ov, String old, String cur) {
                    appContext.prefs.put(key, cur);
            }
        });
    }
    
}

