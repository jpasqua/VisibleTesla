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
import javafx.stage.Stage;
import org.noroomattheinn.tesla.Tesla;
import static org.noroomattheinn.tesla.Tesla.logger;
import org.noroomattheinn.tesla.Vehicle;
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
    public static final String ProductVersion = "0.32.00";

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
    public final MailGun mailer;
    public StatsCollector statsCollector;
    public ChargeStore chargeStore;
    public RestStore restStore;
    public StreamProducer streamProducer;
    public StateProducer stateProducer;
    public CommandIssuer issuer;
    public final TrackedObject<ChargeCycle> lastChargeCycle;
    public final TrackedObject<RestCycle> lastRestCycle;
    public final TrackedObject<String> schedulerActivity;

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
        return (Utils.obtainLock(VTVehicle.get().getVehicle().getVIN() + ".lck", appFilesFolder));
    }
                        
    public void prepForVehicle(Vehicle v) throws IOException {
        statsCollector = new StatsCollector(this);
        initChargeStore();
        initRestStore();
        
        streamProducer = new StreamProducer();
        stateProducer = new StateProducer();
        statsStreamer = new StatsStreamer();
        
        RESTServer.get().launch(this);
    }

    public final String vinKey(String key) { return VTVehicle.get().getVehicle().getVIN() + "_" + key; }
    
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
                
        appFilesFolder = Utils.ensureAppFilesFolder(ProductName);
        Utils.setupLogger(appFilesFolder, "visibletesla", logger, prefs.getLogLevel());
        
        tesla = (prefs.enableProxy.get()) ?
            new Tesla(prefs.proxyHost.get(), prefs.proxyPort.get()) : new Tesla();
        
        this.lastChargeCycle = new TrackedObject<>(null);
        this.lastRestCycle = new TrackedObject<>(null);
        this.schedulerActivity = new TrackedObject<>("");
        
        mailer = new MailGun("api", prefs.useCustomMailGunKey.get()
                ? prefs.mailGunKey.get() : Prefs.MailGunKey);
        issuer = new CommandIssuer();
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
