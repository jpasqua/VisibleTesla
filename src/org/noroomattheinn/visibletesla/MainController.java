/*
 * MainController.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Jul 22, 2013
 */

package org.noroomattheinn.visibletesla;

import com.google.common.collect.Range;
import java.io.File;
import org.noroomattheinn.visibletesla.data.StatsCollector;
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
import javafx.stage.FileChooser;
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
import static org.noroomattheinn.visibletesla.data.StatsCollector.LastExportDirKey;
import org.noroomattheinn.visibletesla.data.VTData;
import org.noroomattheinn.visibletesla.dialogs.DateRangeDialog;
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
     */
    public void start() {
        app = App.get();
        logAppInfo();
        addSystemSpecificHandlers(app.stage);

        refreshTitle();
        app.stage.getIcons().add(new Image(getClass().getClassLoader().getResourceAsStream(
                "org/noroomattheinn/TeslaResources/Icon-72@2x.png")));
        
        vampireStats = new VampireStats(app);

        tabPane.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<Tab>() {
            @Override public void changed(ObservableValue<? extends Tab> ov, Tab t, Tab t1) {
                BaseController c = controllerFromTab(t1);
                if (c != null) { c.activate(); }
            }
        });

        tabs = Arrays.asList(prefsTab, loginTab, schedulerTab, graphTab, chargeTab,
                             hvacTab, locationTab, overviewTab, tripsTab, notifierTab);
        for (Tab t : tabs) { controllerFromTab(t).setAppContext(this.app); }
        
        // Handle font scaling
        int fontScale = Prefs.get().fontScale.get();
        if (fontScale != 100) {
            for (Tab t : tabs) { 
                Node n = t.getContent();
                n.setStyle(String.format("-fx-font-size: %d%%;", fontScale));
            }
        }
        
        // Watch for changes to the inactivity mode and state in order to update the UI
        app.mode.addTracker(true, new Runnable() {
            @Override public void run() { setAppModeMenu(); } } );
        app.state.addTracker(true, new Runnable() {
            @Override public void run() { refreshTitle(); } });

        // Kick off the login process
        LoginController lc = Utils.cast(controllerFromTab(loginTab));
        lc.loggedIn.addTracker(true, new LoginStateChange(lc.loggedIn, false));
        lc.activate();
    }
    
    public void stop() { ThreadManager.get().shutDown(); }
    
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
                VTVehicle.get().setVehicle(null);
                setTabsEnabled(false);
                return;
            }

            if (assumeAwake) {
                wakePane.setVisible(false);
            } else {
                Vehicle v = SelectVehicleDialog.select(app);
                VTVehicle.get().setVehicle(v);
                try {
                    VTData data = VTData.get();
                    data.setVehicle(v);
                    data.setWakeEarly(new WakeEarlyPredicate());
                    data.setPassiveCollection(new PassiveCollectionPredicate());
                    data.setCollectNow(new CollectNowPredicate());
                    if (data.upgradeRequired()) {
                        Dialogs.showInformationDialog(
                            app.stage,
                            "Your data files must be upgraded\nPress OK to begin the process.",
                            "Data Upgrade Process" , "Data File Upgrade");
                        data.doUpgrade();
                        Dialogs.showInformationDialog(
                            app.stage,
                            "Your data files have been upgraded\nPress OK to continue.",
                            "Data Upgrade Process" , "Process Complete");
                    }
                } catch (IOException e) {
                    logger.severe("Unable to establish VTData: " + e.getMessage());
                    Dialogs.showErrorDialog(app.stage,
                            "VisibleTesla has encountered a severe error\n"
                            + "while trying to access its data files. Another\n"
                            + "copy of VisibleTesla may already be writing to them\n"
                            + "or they may be missing.\n\n"
                            + "VisibleTesla will close when you close this window.",
                            "Problem accessing data files", "Problem launching application");
                    Platform.exit();
                }
                if (!app.lock()) {
                    showLockError();
                    Platform.exit();
                }
                logger.info("Vehicle Info: " + VTVehicle.get().getVehicle().getUnderlyingValues());

                if (VTVehicle.get().getVehicle().status().equals("asleep")) {
                    if (letItSleep()) {
                        logger.info("Allowing vehicle to remain in sleep mode");
                        wakePane.setVisible(true);
                        VTVehicle.get().waitForWakeup(
                                new LoginStateChange(loggedIn, true), forceWakeup);
                        return;
                    } else {
                        logger.log(Level.INFO, "Waking up your vehicle");
                    }
                }
            }
                
            DisclaimerDialog.show(app);
            VersionUpdater.conditionalCheckVersion(app);
            app.restoreMode();
            fetchInitialCarState();
        }
    }
    
    private Runnable finishAppStartup = new Runnable() {
        @Override public void run() {
            boolean remoteStartEnabled = VTVehicle.get().getVehicle().remoteStartEnabled();
            remoteStartMenuItem.setDisable(!remoteStartEnabled);
            
            app.trackInactivity(
                    Arrays.asList(overviewTab, hvacTab, locationTab, chargeTab));

            // TO DO: Isn't the following line redundant?
            VTVehicle.get().setVehicle(VTVehicle.get().getVehicle());

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
        Vehicle v = VTVehicle.get().getVehicle();
        
        long MaxWaitTime = 70 * 1000;
        long now = System.currentTimeMillis();
        while (timeSince(now) < MaxWaitTime) {
            if (ThreadManager.get().shuttingDown()) { return new Result(false, "shutting down"); }
            GUIState gs = v.queryGUI();
            if (gs.valid) {
                if (gs.rawState.optString("reason").equals("mobile_access_disabled")) {
                    return new Result(false, "mobile_access_disabled");
                }
                VTVehicle.get().noteUpdatedState(gs);
                return Result.Succeeded;
            } else {
                String error = gs.rawState.optString("error");
                if (error.equals("vehicle unavailable")) v.wakeUp();
                ThreadManager.get().sleep(10 * 1000);
            }
        }
        return Result.Failed;
    }
    
    private Result cacheBasics() {
        final int MaxTriesToStart = 10;
        Result madeContact = establishContact();
        if (!madeContact.success) return madeContact;
        
        // As part of establishing contact with the car we cached the GUIState
        Vehicle         v = VTVehicle.get().getVehicle();
        VehicleState    vs = v.queryVehicle();
        ChargeState     cs = v.queryCharge();
        
        int tries = 0;
        while (!(vs.valid && cs.valid)) {
            if (tries++ > MaxTriesToStart) { return Result.Failed; }
            ThreadManager.get().sleep(5 * 1000);
            if (ThreadManager.get().shuttingDown()) return Result.Failed;
            if (!vs.valid) vs = v.queryVehicle();
            if (!cs.valid) cs = v.queryCharge();
        }
        
        VTVehicle.get().noteUpdatedState(vs);
        VTVehicle.get().noteUpdatedState(cs);
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
        if (mi == exportStatsMenuItem) { exportStats(statsColumns); }
        else if (mi == exportLocMenuItem) { exportStats(locColumns); }
        else if (mi == exportAllMenuItem) { exportStats(StatsCollector.Columns); }
        else if (mi == exportChargeMenuItem) { exportCycles("Charge"); }
        else if (mi == exportRestMenuItem) { exportCycles("Rest"); }
        else if (mi == this.vampireLossMenuItem) { vampireStats.showStats(); }
    }
    

    // Options->"Inactivity Mode" menu items
    @FXML void inactivityOptionsHandler(ActionEvent event) {
        if (event.getTarget() == allowSleepMenuItem) app.allowSleeping();
        else app.stayAwake();
    }
    
    // Help->Documentation
    @FXML private void helpHandler(ActionEvent event) {
        app.fxApp.getHostServices().showDocument(DocumentationURL);
    }
    
    // Help->What's New
    @FXML private void whatsNewHandler(ActionEvent event) {
        app.fxApp.getHostServices().showDocument(ReleaseNotesURL);
    }
    
    // Help->About
    @FXML private void aboutHandler(ActionEvent event) {
        Dialogs.showInformationDialog(
                app.stage,
                "Copyright (c) 2013, Joe Pasqua\n" +
                "Free for personal and non-commercial use.\n" +
                "Based on the great API detective work of many members\n" +
                "of teslamotorsclub.com.  All Tesla imagery derives\n" +
                "from the official Tesla iPhone app.",
                App.ProductName + " " + App.ProductVersion,
                "About " + App.ProductName);
    }

    // Help->Check for Updates
    @FXML private void updatesHandler(ActionEvent event) {
        if (!VersionUpdater.checkForNewerVersion(app)) 
            Dialogs.showInformationDialog(
                    app.stage,
                    "There is no newer version available.",
                    "Update Check Results", "Checking for Updates");
    }
    
    @FXML private void remoteStart(ActionEvent e) {
        final String[] unp = PasswordDialog.getCredentials(
                app.stage, "Authenticate", "Remote Start", false);
        if (unp == null) return;    // User cancelled
        if (unp[1] == null || unp[1].isEmpty()) {
            Dialogs.showErrorDialog(app.stage, "You must enter a password");
            return;
        }
        ThreadManager.get().issuer().issueCommand(new Callable<Result>() {
            @Override public Result call() { 
                return VTVehicle.get().getVehicle().remoteStart(unp[1]); 
            } }, true, null, "Remote Start");
    }

    // Options->Action_>{Honk,Flsh,Wakeup}
    
    @FXML private void honk(ActionEvent e) {
        ThreadManager.get().issuer().issueCommand(new Callable<Result>() {
            @Override public Result call() { return VTVehicle.get().getVehicle().honk(); }
        }, true, null, "Honk");
    }
    @FXML private void flash(ActionEvent e) {
        ThreadManager.get().issuer().issueCommand(new Callable<Result>() {
            @Override public Result call() { return VTVehicle.get().getVehicle().flashLights(); }
        }, true, null, "Flash Lights");
    }
    @FXML private void wakeup(ActionEvent e) {
        ThreadManager.get().issuer().issueCommand(new Callable<Result>() {
            @Override public Result call() { return VTVehicle.get().getVehicle().wakeUp(); }
        }, true, null, "Wake up");
    }
    
/*------------------------------------------------------------------------------
 *
 * Export Handling Methods
 * 
 *----------------------------------------------------------------------------*/
    
    private void exportCycles(String cycleType) {
        String initialDir = Prefs.store().get(
                StatsCollector.LastExportDirKey, System.getProperty("user.home"));
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export " + cycleType + " Data");
        fileChooser.setInitialDirectory(new File(initialDir));

        Stage stage = App.get().stage;
        File file = fileChooser.showSaveDialog(stage);
        if (file != null) {
            String enclosingDirectory = file.getParent();
            if (enclosingDirectory != null)
                Prefs.store().put(StatsCollector.LastExportDirKey, enclosingDirectory);
            Range<Long> exportPeriod = DateRangeDialog.getExportPeriod(stage);
            if (exportPeriod == null)
                return;
            boolean exported;
            if (cycleType.equals("Charge")) {
                exported = VTData.get().exportCharges(file, exportPeriod);
            } else {
                exported = VTData.get().exportRests(file, exportPeriod);
            }
            if (exported) {
                Dialogs.showInformationDialog(
                        stage, "Your data has been exported",
                        "Data Export Process" , "Export Complete");
            } else {
                Dialogs.showErrorDialog(
                        stage, "Unable to save to: " + file,
                        "Data Export Process" , "Export Failed");
            }
        }
    }
    
    private void exportStats(String[] columns) {
        String initialDir = Prefs.store().get(
                LastExportDirKey, System.getProperty("user.home"));
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Data");
        fileChooser.setInitialDirectory(new File(initialDir));

        File file = fileChooser.showSaveDialog(app.stage);
        if (file != null) {
            String enclosingDirectory = file.getParent();
            if (enclosingDirectory != null)
                Prefs.store().put(LastExportDirKey, enclosingDirectory);
            Range<Long> exportPeriod = DateRangeDialog.getExportPeriod(app.stage);
            if (exportPeriod == null)
                return;
            if (VTData.get().statsCollector.export(
                    file, exportPeriod, columns)) {
                Dialogs.showInformationDialog(
                    app.stage, "Your data has been exported",
                    "Data Export Process" , "Export Complete");
            } else {
                Dialogs.showErrorDialog(
                    app.stage, "Unable to save to: " + file,
                    "Data Export Process" , "Export Failed");
            }
        }
    }
    
/*------------------------------------------------------------------------------
 *
 * Other UI Handlers and utilities
 * 
 *----------------------------------------------------------------------------*/

    private void addSystemSpecificHandlers(final Stage theStage) {
        if (SystemUtils.IS_OS_MAC) {    // Add a handler for Command-H
            theStage.getScene().getAccelerators().put(
                    new KeyCodeCombination(KeyCode.H, KeyCombination.SHORTCUT_DOWN),
                    new Runnable() {
                @Override public void run() {
                    theStage.setIconified(true);
                }
            });
        }
    }
    
    private void refreshTitle() {
        String carName = (VTVehicle.get().getVehicle() != null) ? VTVehicle.get().getVehicle().getDisplayName() : null;
        String title = App.ProductName + " " + App.ProductVersion;
        if (carName != null) title = title + " for " + carName;
        if (app.isIdle()) {
            String time = String.format("%1$tH:%1$tM", new Date());
            title = title + " [sleeping at " + time + "]";
        }
        app.stage.setTitle(title);
    }

    private void setAppModeMenu() {
        if (app.allowingSleeping()) allowSleepMenuItem.setSelected(true);
        else stayAwakeMenuItem.setSelected(true);
    }

    private void logAppInfo() {
        logger.info(App.ProductName + ": " + App.ProductVersion);
        
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
    
    private class WakeEarlyPredicate implements Utils.Predicate {
        private long lastEval  = System.currentTimeMillis();

        @Override public boolean eval() {
            try {
                if (App.get().mode.lastSet() > lastEval && App.get().stayingAwake()) return true;
                return ThreadManager.get().shuttingDown();
            } finally {
                lastEval = System.currentTimeMillis();
            }
        }
    }
    
    private class CollectNowPredicate implements VTData.TimeBasedPredicate {
        private long last = Long.MAX_VALUE;
        
        @Override public void setTime(long time) { last = time; }

        @Override public boolean eval() {
            return (App.get().isActive() && App.get().state.lastSet() > last);
        }
    }
    
    private class PassiveCollectionPredicate implements Utils.Predicate {
        @Override public boolean eval() {
            return (App.get().isIdle() && App.get().allowingSleeping());
        }
    }

/*------------------------------------------------------------------------------
 *
 * Display various info and warning dialogs
 * 
 *----------------------------------------------------------------------------*/
    
    private boolean letItSleep() {
        WakeSleepDialog wsd = WakeSleepDialog.show(app.stage);
        return wsd.letItSleep();
    }
    
    @FXML private void wakeButtonHandler(ActionEvent event) { forceWakeup.set(true); }
    
    private void exitWithMobileAccessError() {
        Platform.runLater(new Runnable() {
            @Override public void run() {
                Dialogs.showErrorDialog(app.stage,
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
                Dialogs.showErrorDialog(app.stage,
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
        Dialogs.showErrorDialog(app.stage,
            "There appears to be another copy of VisibleTesla\n" +
            "running on this computer and trying to talk\n" +
            "to the same car. That can cause problems and\n" +
            "is not allowed\n\n"+
            "VisibleTesla will close when you close this window.",
            "Multiple Copies of VisibleTesla", "Problem launching application");
    }
}
