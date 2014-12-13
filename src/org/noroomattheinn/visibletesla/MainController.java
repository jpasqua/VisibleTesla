/*
 * MainController.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Jul 22, 2013
 */

package org.noroomattheinn.visibletesla;

import java.io.IOException;
import org.noroomattheinn.visibletesla.dialogs.WakeSleepDialog;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Dialogs;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import org.apache.commons.lang3.SystemUtils;
import org.noroomattheinn.tesla.ChargeState;
import org.noroomattheinn.tesla.GUIState;
import org.noroomattheinn.tesla.Result;
import static org.noroomattheinn.tesla.Tesla.logger;
import org.noroomattheinn.tesla.Vehicle;
import org.noroomattheinn.tesla.VehicleState;
import org.noroomattheinn.utils.Utils;
import static org.noroomattheinn.utils.Utils.timeSince;
import org.noroomattheinn.visibletesla.dialogs.DisclaimerDialog;
import org.noroomattheinn.visibletesla.dialogs.PasswordDialog;
import org.noroomattheinn.visibletesla.dialogs.SelectVehicleDialog;
import org.noroomattheinn.visibletesla.dialogs.VersionUpdater;
import org.noroomattheinn.visibletesla.fxextensions.TrackedObject;

/**
 * This is the main application code for VisibleTesla. It does not contain
 * the main function. Main is in VisibleTesla.java which is mostly just a shell.
 * This controller is associated with the Tab panel in which all of the 
 * individual tabs live.
 * 
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class MainController extends BaseController {
    
/*------------------------------------------------------------------------------
 *
 * Constants and Enums
 * 
 *----------------------------------------------------------------------------*/

    private static final String DocumentationURL = 
            "http://visibletesla.com/Documentation/pages/GettingStarted.html";
    private static final String ReleaseNotesURL  = 
            "http://visibletesla.com/Documentation/ReleaseNotes.html";
    
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/

    private final BooleanProperty   forceWakeup = new SimpleBooleanProperty(false);
    private VampireStats vampireStats;

/*------------------------------------------------------------------------------
 *
 * UI Elements
 * 
 *----------------------------------------------------------------------------*/

    // The top level AnchorPane and the TabPane that sits inside it
    @FXML private TabPane tabPane;

    // The individual tabs that comprise the overall UI
    @FXML private Tab   notifierTab;
    @FXML private Tab   prefsTab;
    @FXML private Tab   schedulerTab;
    @FXML private Tab   graphTab;
    @FXML private Tab   chargeTab;
    @FXML private Tab   hvacTab;
    @FXML private Tab   locationTab;
    @FXML private Tab   loginTab;
    @FXML private Tab   overviewTab;
    @FXML private Tab   tripsTab;
    @FXML private Pane  wakePane;
    @FXML private Button wakeButton;
    
    private List<Tab> tabs;
    
    @FXML private MenuItem
            exportStatsMenuItem, exportLocMenuItem,  exportAllMenuItem,
            exportChargeMenuItem, exportRestMenuItem;
    @FXML private MenuItem vampireLossMenuItem;
    @FXML private MenuItem remoteStartMenuItem;
    
    // The menu items that are handled in this controller directly
    @FXML private RadioMenuItem allowSleepMenuItem;
    @FXML private RadioMenuItem stayAwakeMenuItem;
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/

    /**
     * Called by the main application to allow us to store away the fxApp context
     * and perform any other fxApp startup tasks. In particular, we (1) distribute
     * fxApp context to all of the controllers, and (2) we set a listener for login
     * completion and try and automatic login.
     * @param ac    The AppContext
     */
    public void start(final AppContext ac) {
        this.ac = ac;
        logAppInfo();
        addSystemSpecificHandlers(ac);

        refreshTitle();
        this.ac.stage.getIcons().add(new Image(getClass().getClassLoader().getResourceAsStream(
                "org/noroomattheinn/TeslaResources/Icon-72@2x.png")));
        
        vampireStats = new VampireStats(ac);

        tabPane.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<Tab>() {
            @Override public void changed(ObservableValue<? extends Tab> ov, Tab t, Tab t1) {
                BaseController c = controllerFromTab(t1);
                if (c != null) { c.activate(); }
            }
        });

        tabs = Arrays.asList(prefsTab, loginTab, schedulerTab, graphTab, chargeTab,
                             hvacTab, locationTab, overviewTab, tripsTab, notifierTab);
        for (Tab t : tabs) { controllerFromTab(t).setAppContext(this.ac); }
        
        // Handle font scaling
        int fontScale = this.ac.prefs.fontScale.get();
        if (fontScale != 100) {
            for (Tab t : tabs) { 
                Node n = t.getContent();
                n.setStyle(String.format("-fx-font-size: %d%%;", fontScale));
            }
        }
        
        // Watch for changes to the inactivity mode and state in order to update the UI
        this.ac.appMode.addTracker(true, new Runnable() {
            @Override public void run() { setAppModeMenu(); } } );
        this.ac.appState.addTracker(true, new Runnable() {
            @Override public void run() { refreshTitle(); } });

        // Kick off the login process
        LoginController lc = Utils.cast(controllerFromTab(loginTab));
        lc.loggedIn.addTracker(true, new LoginStateChange(lc.loggedIn, false));
        lc.activate();
    }
    
    public void stop() { ac.tm.shutDown(); }
    
/*------------------------------------------------------------------------------
 *
 * Methods overridden from BaseController. We implement BaseController so that
 * we can perform issueCommand operations.
 * 
 *----------------------------------------------------------------------------*/
    
    @Override protected void fxInitialize() { }
    @Override protected void initializeState() { }
    @Override protected void activateTab() { }
    @Override protected void refresh() { }

/*------------------------------------------------------------------------------
 *
 * Dealing with a Login Event
 * 
 *----------------------------------------------------------------------------*/

    private void fetchInitialCarState() {
        issueCommand(new Callable<Result>() {
            @Override public Result call() {
                Result r = cacheBasics();
                if (!r.success) {
                    if (r.explanation.equals("mobile_access_disabled"))  exitWithMobileAccessError();
                    else exitWithCachingError();
                    return Result.Failed;
                }
                Platform.runLater(finishAppStartup);
                return Result.Succeeded;
            } }, "Cache Basics");
    }

    
    private class LoginStateChange implements Runnable {
        private final TrackedObject<Boolean> loggedIn;
        private final boolean assumeAwake;
        
        LoginStateChange(TrackedObject<Boolean> loggedIn, boolean assumeAwake) {
            this.loggedIn = loggedIn;
            this.assumeAwake = assumeAwake;
        }
        
        @Override public void run() {
            if (!loggedIn.get()) {
                ac.vehicle = null;
                setTabsEnabled(false);
                return;
            }

            if (assumeAwake) {
                wakePane.setVisible(false);
            } else {
                ac.vehicle = SelectVehicleDialog.select(ac);
                if (!ac.lockAppInstance()) {
                    showLockError();
                    Platform.exit();
                }
                logger.info("Vehicle Info: " + ac.vehicle.getUnderlyingValues());

                if (ac.vehicle.status().equals("asleep")) {
                    if (letItSleep()) {
                        logger.info("Allowing vehicle to remain in sleep mode");
                        wakePane.setVisible(true);
                        VTExtras.waitForWakeup(
                                ac, new LoginStateChange(loggedIn, true), forceWakeup);
                        return;
                    } else {
                        logger.log(Level.INFO, "Waking up your vehicle");
                    }
                }
            }
                
            DisclaimerDialog.show(ac);
            VersionUpdater.conditionalCheckVersion(ac);
            ac.appMode.restore();
            fetchInitialCarState();
        }
    }
    
    private Runnable finishAppStartup = new Runnable() {
        @Override public void run() {
            boolean remoteStartEnabled = ac.vehicle.remoteStartEnabled();
            remoteStartMenuItem.setDisable(!remoteStartEnabled);
            
            ac.appState.trackInactivity(tabs);
            try {
                ac.prepForVehicle(ac.vehicle);
            } catch (IOException e) {
                logger.severe("Unable to establish repository: " + e.getMessage());
                Dialogs.showErrorDialog(ac.stage,
                        "VisibleTesla has encountered a severe error\n"
                        + "while trying to access its data files. Another\n"
                        + "copy of VisibleTesla may already be writing to them\n"
                        + "or they may be missing.\n\n"
                        + "VisibleTesla will close when you close this window.",
                        "Problem accessing data files", "Problem launching application");
                Platform.exit();
            }
            refreshTitle();
            
            // Start the Scheduler and the Notifier
            controllerFromTab(schedulerTab).activate();
            controllerFromTab(notifierTab).activate();
            
            setTabsEnabled(true);
            jumpToTab(overviewTab);
        }
    };
    
/*------------------------------------------------------------------------------
 *
 * Private Utility Methods waking the car and initiating contact
 * 
 *----------------------------------------------------------------------------*/
    
    /**
     * Make contact with the car for the first time. It may need to be woken up
     * in the process. Since we need to do some command as part of this process,
     * we grab the GUIState and store it away.
     * @return 
     */
    private Result establishContact() {
        Vehicle v = ac.vehicle;
        
        long MaxWaitTime = 70 * 1000;
        long now = System.currentTimeMillis();
        while (timeSince(now) < MaxWaitTime) {
            if (ac.shuttingDown) { return new Result(false, "shutting down"); }
            GUIState gs = v.queryGUI();
            if (gs.valid) {
                if (gs.rawState.optString("reason").equals("mobile_access_disabled")) {
                    return new Result(false, "mobile_access_disabled");
                }
                ac.lastGUIState.set(gs);
                return Result.Succeeded;
            } else {
                String error = gs.rawState.optString("error");
                if (error.equals("vehicle unavailable")) v.wakeUp();
                ac.sleep(10 * 1000);
            }
        }
        return Result.Failed;
    }
    
    private Result cacheBasics() {
        final int MaxTriesToStart = 10;
        Result madeContact = establishContact();
        if (!madeContact.success) return madeContact;
        
        // As part of establishing contact with the car we cached the GUIState
        Vehicle         v = ac.vehicle;
        VehicleState    vs = v.queryVehicle();
        ChargeState     cs = v.queryCharge();
        
        int tries = 0;
        while (!(vs.valid && cs.valid)) {
            if (tries++ > MaxTriesToStart) { return Result.Failed; }
            ac.sleep(5 * 1000);
            if (ac.shuttingDown) return Result.Failed;
            if (!vs.valid) vs = v.queryVehicle();
            if (!cs.valid) cs = v.queryCharge();
        }
        
        ac.lastVehicleState.set(vs);
        ac.lastChargeState.set(cs);
        return Result.Succeeded;
    }
    
/*------------------------------------------------------------------------------
 *
 * Private Utility Methods for Tab handling
 * 
 *----------------------------------------------------------------------------*/
    
    private void setTabsEnabled(boolean enabled) {
        for (Tab t : tabs) { t.setDisable(!enabled); }
        loginTab.setDisable(false);     // The Login Tab is always enabled
        prefsTab.setDisable(false);     // The Prefs Tab is always enabled
    }
    
    private void jumpToTab(final Tab tab) {
        Platform.runLater(new Runnable() {
            @Override public void run() { tabPane.getSelectionModel().select(tab);  }
        });
    }

    /**
     * Utility method that returns the BaseController object associated with
     * a given tab. It does this by extracting the userData object which each
     * BaseController sets to itself.
     * @param   t   The tab for which we want the BaseController
     * @return      The BaseController
     */
    private BaseController controllerFromTab(Tab t) {
        Object userData = t.getContent().getUserData();
        return (userData instanceof BaseController) ? (BaseController)userData : null;
    }
    
/*------------------------------------------------------------------------------
 *
 * This section implements UI Actionhandlers for the menu items
 * 
 *----------------------------------------------------------------------------*/
    
    // File->Close
    @FXML void closeHandler(ActionEvent event) {
        Platform.exit();
    }
    
    // File->Export * Data...
    private static final String[] statsColumns = new String[] {
        StatsCollector.VoltageKey, StatsCollector.CurrentKey,
        StatsCollector.EstRangeKey, StatsCollector.SOCKey,
        StatsCollector.ROCKey, StatsCollector.BatteryAmpsKey,
        StatsCollector.SpeedKey, StatsCollector.PowerKey,
    };
    private static final String[] locColumns = new String[] {
        StatsCollector.LatitudeKey, StatsCollector.LongitudeKey,
        StatsCollector.HeadingKey, StatsCollector.SpeedKey,
        StatsCollector.OdometerKey, StatsCollector.PowerKey
    };
    
    @FXML void exportHandler(ActionEvent event) {
        MenuItem mi = (MenuItem)event.getSource();
        if (mi == exportStatsMenuItem) {
            ac.statsCollector.export(statsColumns);
        } else if (mi == exportLocMenuItem) {
            ac.statsCollector.export(locColumns);
        } else if (mi == exportAllMenuItem) {
            ac.statsCollector.export(StatsCollector.Columns);
        } else if (mi == exportChargeMenuItem) {
            ac.chargeStore.export();
        } else if (mi == exportRestMenuItem) {
            ac.restStore.export();
        } else if (mi == this.vampireLossMenuItem) {
            vampireStats.showStats();
        }
    }
    
    // Options->"Inactivity Mode" menu items
    @FXML void inactivityOptionsHandler(ActionEvent event) {
        if (event.getTarget() == allowSleepMenuItem) ac.appMode.allowSleeping();
        else ac.appMode.stayAwake();
    }
    
    // Help->Documentation
    @FXML private void helpHandler(ActionEvent event) {
        ac.fxApp.getHostServices().showDocument(DocumentationURL);
    }
    
    // Help->What's New
    @FXML private void whatsNewHandler(ActionEvent event) {
        ac.fxApp.getHostServices().showDocument(ReleaseNotesURL);
    }
    
    // Help->About
    @FXML private void aboutHandler(ActionEvent event) {
        Dialogs.showInformationDialog(
                ac.stage,
                "Copyright (c) 2013, Joe Pasqua\n" +
                "Free for personal and non-commercial use.\n" +
                "Based on the great API detective work of many members\n" +
                "of teslamotorsclub.com.  All Tesla imagery derives\n" +
                "from the official Tesla iPhone app.",
                AppContext.ProductName + " " + AppContext.ProductVersion,
                "About " + AppContext.ProductName);
    }

    // Help->Check for Updates
    @FXML private void updatesHandler(ActionEvent event) {
        if (!VersionUpdater.checkForNewerVersion(ac)) 
            Dialogs.showInformationDialog(
                    ac.stage,
                    "There is no newer version available.",
                    "Update Check Results", "Checking for Updates");
    }
    
    @FXML private void remoteStart(ActionEvent e) {
        final String[] unp = PasswordDialog.getCredentials(
                ac.stage, "Authenticate", "Remote Start", false);
        if (unp == null) return;    // User cancelled
        if (unp[1] == null || unp[1].isEmpty()) {
            Dialogs.showErrorDialog(ac.stage, "You must enter a password");
            return;
        }
        ac.issuer.issueCommand(new Callable<Result>() {
            @Override public Result call() { 
                return ac.vehicle.remoteStart(unp[1]); 
            } }, true, null, "Remote Start");
    }

    // Options->Action_>{Honk,Flsh,Wakeup}
    
    @FXML private void honk(ActionEvent e) {
        ac.issuer.issueCommand(new Callable<Result>() {
            @Override public Result call() { return ac.vehicle.honk(); }
        }, true, null, "Honk");
    }
    @FXML private void flash(ActionEvent e) {
        ac.issuer.issueCommand(new Callable<Result>() {
            @Override public Result call() { return ac.vehicle.flashLights(); }
        }, true, null, "Flash Lights");
    }
    @FXML private void wakeup(ActionEvent e) {
        ac.issuer.issueCommand(new Callable<Result>() {
            @Override public Result call() { return ac.vehicle.wakeUp(); }
        }, true, null, "Wake up");
    }
    

/*------------------------------------------------------------------------------
 *
 * Other UI Handlers and utilities
 * 
 *----------------------------------------------------------------------------*/

    private void addSystemSpecificHandlers(AppContext ac) {
        if (SystemUtils.IS_OS_MAC) {    // Add a handler for Command-H
            final Stage theStage = ac.stage;
            ac.stage.getScene().getAccelerators().put(
                    new KeyCodeCombination(KeyCode.H, KeyCombination.SHORTCUT_DOWN),
                    new Runnable() {
                @Override public void run() {
                    theStage.setIconified(true);
                }
            });
        }
    }
    
    private void refreshTitle() {
        String carName = (ac.vehicle != null) ? ac.vehicle.getDisplayName() : null;
        String title = AppContext.ProductName + " " + AppContext.ProductVersion;
        if (carName != null) title = title + " for " + carName;
        if (ac.appState.isIdle()) {
            String time = String.format("%1$tH:%1$tM", new Date());
            title = title + " [sleeping at " + time + "]";
        }
        ac.stage.setTitle(title);
    }

    private void setAppModeMenu() {
        if (ac.appMode.allowingSleeping()) allowSleepMenuItem.setSelected(true);
        else stayAwakeMenuItem.setSelected(true);
    }

    private void logAppInfo() {
        logger.info(AppContext.ProductName + ": " + AppContext.ProductVersion);
        
        logger.info(
                String.format("Max memory: %4dmb", Runtime.getRuntime().maxMemory()/(1024*1024)));
        List<String> jvmArgs = Utils.getJVMArgs();
        logger.info("JVM Arguments");
        if (jvmArgs != null) {
            for (String arg : jvmArgs) {
                logger.info("Arg: " + arg);
            }
        }
    }
    
/*------------------------------------------------------------------------------
 *
 * Display various info and warning dialogs
 * 
 *----------------------------------------------------------------------------*/
    
    private boolean letItSleep() {
        WakeSleepDialog wsd = WakeSleepDialog.show(ac.stage);
        return wsd.letItSleep();
    }
    
    @FXML private void wakeButtonHandler(ActionEvent event) { forceWakeup.set(true); }
    
    private void exitWithMobileAccessError() {
        Platform.runLater(new Runnable() {
            @Override public void run() {
                Dialogs.showErrorDialog(ac.stage,
                        "Your Tesla has not been configured to allow mobile " +
                        "access. You have to enable this on your car's touch"  +
                        "screen using Controls / Settings / Vehicle." +
                        "\n\nChange that setting in your car, then relaunch VisibleTesla.",
                        "Mobile access is not enabled", "Communication Problem");
                logger.log(Level.SEVERE, "Mobile access is not enabled - exiting.");
                Platform.exit();
            }
        });
    }
    
    private void exitWithCachingError() {
        Platform.runLater(new Runnable() {
            @Override public void run() {
                Dialogs.showErrorDialog(ac.stage,
                        "Failed to connect to your vehicle even after a successful " +
                        "login. It may be in a deep sleep and can't be woken up.\n"  +
                        "\nPlease try to wake your Tesla and then try VisibleTesla again.",
                        "Unable to communicate with your Tesla", "Communication Problem");
                logger.severe("Can't communicate with vehicle - exiting.");
                Platform.exit();
            }
        });
    }
    
    private void showLockError() {
        Dialogs.showErrorDialog(ac.stage,
            "There appears to be another copy of VisibleTesla\n" +
            "running on this computer and trying to talk\n" +
            "to the same car. That can cause problems and\n" +
            "is not allowed\n\n"+
            "VisibleTesla will close when you close this window.",
            "Multiple Copies of VisibleTesla", "Problem launching application");
    }
}
