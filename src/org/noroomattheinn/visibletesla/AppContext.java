/*
 * AppContext.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Aug 30, 2013
 */
package org.noroomattheinn.visibletesla;

import org.noroomattheinn.visibletesla.cycles.ChargeCycle;
import org.noroomattheinn.visibletesla.cycles.RestStore;
import org.noroomattheinn.visibletesla.cycles.ChargeStore;
import org.noroomattheinn.visibletesla.cycles.RestMonitor;
import org.noroomattheinn.visibletesla.cycles.RestCycle;
import org.noroomattheinn.visibletesla.cycles.ChargeMonitor;
import org.noroomattheinn.visibletesla.rest.RESTServer;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.stage.Stage;
import org.noroomattheinn.tesla.BaseState;
import org.noroomattheinn.tesla.ChargeState;
import org.noroomattheinn.tesla.DriveState;
import org.noroomattheinn.tesla.GUIState;
import org.noroomattheinn.tesla.HVACState;
import org.noroomattheinn.tesla.StreamState;
import org.noroomattheinn.tesla.Tesla;
import static org.noroomattheinn.tesla.Tesla.logger;
import org.noroomattheinn.tesla.Vehicle;
import org.noroomattheinn.tesla.VehicleState;
import org.noroomattheinn.utils.MailGun;
import org.noroomattheinn.utils.Utils;
import org.noroomattheinn.visibletesla.fxextensions.TrackedObject;

/**
 * AppContext - Stores application-wide state for use by all of the individual
 * Tabs and provides a number of utility methods.
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class AppContext {

/*------------------------------------------------------------------------------
 *
 * Constants and Enums
 * 
 *----------------------------------------------------------------------------*/
        
    public static final String ProductName = "VisibleTesla";
    public static final String ProductVersion = "0.31.00";

/*------------------------------------------------------------------------------
 *
 * PUBLIC - Application State
 * 
 *----------------------------------------------------------------------------*/
    
    public final Application fxApp;
    public final Tesla tesla;
    public final Stage stage;
    public final AppMode appMode;
    public final AppState appState;
    public final ObjectProperty<ChargeState> lastChargeState;
    public final ObjectProperty<DriveState> lastDriveState;
    public final ObjectProperty<GUIState> lastGUIState;
    public final ObjectProperty<HVACState> lastHVACState;
    public final ObjectProperty<StreamState> lastStreamState;
    public final ObjectProperty<VehicleState> lastVehicleState;
    public final TrackedObject<ChargeCycle> lastChargeCycle;
    public final TrackedObject<RestCycle> lastRestCycle;
    public final TrackedObject<String> schedulerActivity;
    public final MailGun mailer;
    public Vehicle vehicle = null;
    public StatsCollector statsCollector;
    public ChargeStore chargeStore;
    public RestStore restStore;
    public StreamProducer streamProducer;
    public StateProducer stateProducer;
    public CommandIssuer issuer;

/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private static AppContext instance = null;
            
    private final File appFilesFolder;
    private StatsStreamer statsStreamer;
    private ChargeMonitor chargeMonitor;
    private RestMonitor restMonitor;

/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public static AppContext create(Application app, Stage stage) {
        if (instance != null) return instance;
        return (instance = new AppContext(app, stage));
    }
    
    public static AppContext get() { return instance; }
    
    public boolean lockAppInstance() {
        return (Utils.obtainLock(vehicle.getVIN() + ".lck", appFilesFolder));
    }
                        
    public void prepForVehicle(Vehicle v) throws IOException {
        vehicle = v;

        statsCollector = new StatsCollector(this);
        initChargeStore();
        initRestStore();
        
        streamProducer = new StreamProducer(this);
        stateProducer = new StateProducer(this);
        statsStreamer = new StatsStreamer(this);
        
        lastStreamState.get().odometer = Prefs.store().getDouble(vinKey("odometer"), 0);

        RESTServer.get().launch(this);
    }

    public void noteUpdatedState(final BaseState state) {
        if (Platform.isFxApplicationThread()) { noteUpdatedStateInternal(state); }
        else {
            Platform.runLater(new Runnable() {
                @Override public void run() { noteUpdatedStateInternal(state); } });
        }
    }
    
    private void noteUpdatedStateInternal(BaseState state) {
        if (state instanceof ChargeState) {
            lastChargeState.set((ChargeState)state);
        } else if (state instanceof DriveState) {
            lastDriveState.set((DriveState)state);
        } else if (state instanceof GUIState) {
            lastGUIState.set((GUIState)state);
        } else if (state instanceof HVACState) {
            lastHVACState.set((HVACState)state);
        } else if (state instanceof VehicleState) {
            lastVehicleState.set((VehicleState)state);
        } else if (state instanceof StreamState) {
            lastStreamState.set((StreamState)state);
        }
    }

    public final String vinKey(String key) { return vehicle.getVIN() + "_" + key; }
    
    public File appFileFolder() { return appFilesFolder; }

/*------------------------------------------------------------------------------
 *
 * Hide the constructor for the singleton
 * 
 *----------------------------------------------------------------------------*/

    private AppContext(Application app, Stage stage) {
        Prefs prefs = Prefs.get();
        this.fxApp = app;
        this.stage = stage;
        
        this.appMode = new AppMode(this);    
        this.appState = new AppState(this);    
        
        this.lastChargeState = new SimpleObjectProperty<>(new ChargeState());
        this.lastDriveState = new SimpleObjectProperty<>();
        this.lastGUIState = new SimpleObjectProperty<>();
        this.lastHVACState = new SimpleObjectProperty<>();
        this.lastStreamState = new SimpleObjectProperty<>(new StreamState());
        this.lastVehicleState = new SimpleObjectProperty<>();
        this.schedulerActivity = new TrackedObject<>("");
        this.lastChargeCycle = new TrackedObject<>(null);
        this.lastRestCycle = new TrackedObject<>(null);
        
        appFilesFolder = Utils.ensureAppFilesFolder(ProductName);
        Utils.setupLogger(appFilesFolder, "visibletesla", logger, prefs.getLogLevel());
        
        tesla = (prefs.enableProxy.get()) ?
            new Tesla(prefs.proxyHost.get(), prefs.proxyPort.get()) : new Tesla();

        mailer = new MailGun("api", prefs.useCustomMailGunKey.get()
                ? prefs.mailGunKey.get() : Prefs.MailGunKey);
        issuer = new CommandIssuer(this);
    }

/*------------------------------------------------------------------------------
 *
 * Private Utility Methods
 * 
 *----------------------------------------------------------------------------*/

    private void initRestStore() throws FileNotFoundException {
        boolean needsInitialLoad = RestStore.requiresInitialLoad(this);
        restStore = new RestStore(this);
        restMonitor = new RestMonitor(this);
        if (needsInitialLoad) { restStore.doIntialLoad(); }
    }
    
    private void initChargeStore() throws FileNotFoundException {
        chargeStore = new ChargeStore(this);
        chargeMonitor = new ChargeMonitor(this);
    }
    

}
