/*
 * MainController.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Jul 22, 2013
 */

package org.noroomattheinn.visibletesla;

import com.google.common.collect.Range;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.NavigableMap;
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
import javafx.scene.control.*;
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
import org.noroomattheinn.tesla.Vehicle;
import org.noroomattheinn.tesla.VehicleState;
import org.noroomattheinn.timeseries.Row;
import org.noroomattheinn.utils.ThreadManager;
import org.noroomattheinn.utils.TrackedObject;
import org.noroomattheinn.utils.Utils;
import org.noroomattheinn.visibletesla.data.RestCycle;
import org.noroomattheinn.visibletesla.data.VTData;
import org.noroomattheinn.visibletesla.dialogs.*;
import org.noroomattheinn.visibletesla.vehicle.VTVehicle;

import static org.noroomattheinn.tesla.Tesla.logger;
import static org.noroomattheinn.utils.Utils.timeSince;

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
    void start(App theApp, VTVehicle v, VTData data, Prefs prefs) {
        this.app = theApp;
        this.vtVehicle = v;  // This is defined in BaseController
        this.vtData = data;  // This is defined in BaseController
        this.prefs = prefs;  // This is defined in BaseController
        
        logAppInfo();
        addSystemSpecificHandlers(app.stage);

        refreshTitle();
        app.stage.getIcons().add(new Image(getClass().getClassLoader().getResourceAsStream(
                "org/noroomattheinn/TeslaResources/Icon-72@2x.png")));
        
        tabPane.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<Tab>() {
            @Override public void changed(ObservableValue<? extends Tab> ov, Tab t, Tab t1) {
                BaseController c = controllerFromTab(t1);
                if (c != null) { c.activate(); }
            }
        });

        tabs = Arrays.asList(prefsTab, loginTab, schedulerTab, graphTab, chargeTab,
                             hvacTab, locationTab, overviewTab, tripsTab, notifierTab);
        for (Tab t : tabs) {
            controllerFromTab(t).setAppContext(theApp, v, data, prefs);
        }
        
        // Handle font scaling
        int fontScale = prefs.fontScale.get();
        if (fontScale != 100) {
            for (Tab t : tabs) { 
                Node n = t.getContent();
                n.setStyle(String.format("-fx-font-size: %d%%;", fontScale));
            }
        }
        
        // Watch for changes to the inactivity mode and state in order to update the UI
        App.addTracker(app.api.mode, new Runnable() {
            @Override public void run() { setAppModeMenu(); } } );
        App.addTracker(app.api.state, new Runnable() {
            @Override public void run() { refreshTitle(); } });

        // Kick off the login process
        LoginController lc = Utils.cast(controllerFromTab(loginTab));
        App.addTracker(lc.loggedIn, new LoginStateChange(lc.loggedIn, false));
        lc.activate();
    }
    
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
                vtVehicle.setVehicle(null);
                setTabsEnabled(false);
                return;
            }

            if (assumeAwake) {
                wakePane.setVisible(false);
            } else {
                Vehicle v = SelectVehicleDialog.select(app.stage, app.tesla.getVehicles());
                vtVehicle.setVehicle(v);
                try {
                    upgradeDataStoreIfNeeded(v);
                    vtData.setVehicle(v);
                    vtData.setWakeEarly(new WakeEarlyPredicate());
                    vtData.setPassiveCollection(new PassiveCollectionPredicate());
                    vtData.setCollectNow(new CollectNowPredicate());
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
                if (!app.lock(v.getVIN())) {
                    showLockError();
                    Platform.exit();
                }
                logger.info("Vehicle Info: " + vtVehicle.getVehicle().getUnderlyingValues());

                if (vtVehicle.getVehicle().status().equals("asleep")) {
                    if (letItSleep()) {
                        logger.info("Allowing vehicle to remain in sleep mode");
                        wakePane.setVisible(true);
                        vtVehicle.waitForWakeup(
                                new LoginStateChange(loggedIn, true), forceWakeup);
                        return;
                    } else {
                        logger.log(Level.INFO, "Waking up your vehicle");
                    }
                }
            }
                
            showDisclaimer();
            conditionalCheckVersion();
            app.restoreMode();
            fetchInitialCarState();
        }
        
        private void upgradeDataStoreIfNeeded(Vehicle v) {
            if (vtData.upgradeRequired(v)) {
                Dialogs.showInformationDialog(
                        app.stage,
                        "Your data files must be upgraded\nPress OK to begin the process.",
                        "Data Upgrade Process", "Data File Upgrade");
                vtData.doUpgrade(v);
                Dialogs.showInformationDialog(
                        app.stage,
                        "Your data files have been upgraded\nPress OK to continue.",
                        "Data Upgrade Process", "Process Complete");
            }
        }
    }
    
    private void conditionalCheckVersion() {
        String key = vinKey("LastVersionCheck");
        long lastVersionCheck = prefs.storage().getLong(key, 0);
        long now = System.currentTimeMillis();
        if (now - lastVersionCheck > (7 * 24 * 60 * 60 * 1000)) {
            VersionUpdater.checkForNewerVersion(
                    App.productVersion(), app.stage, app.getHostServices(),
                    prefs.offerExperimental.get());
            prefs.storage().putLong(key, now);
        }
    }

    private Runnable finishAppStartup = new Runnable() {
        @Override public void run() {
            boolean remoteStartEnabled = vtVehicle.getVehicle().remoteStartEnabled();
            remoteStartMenuItem.setDisable(!remoteStartEnabled);
            
            app.watchForUserActivity(
                    Arrays.asList(overviewTab, hvacTab, locationTab, chargeTab));

            // TO DO: Isn't the following line redundant?
            vtVehicle.setVehicle(vtVehicle.getVehicle());

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
        Vehicle v = vtVehicle.getVehicle();
        
        long MaxWaitTime = 70 * 1000;
        long now = System.currentTimeMillis();
        while (timeSince(now) < MaxWaitTime) {
            if (ThreadManager.get().shuttingDown()) { return new Result(false, "shutting down"); }
            GUIState gs = v.queryGUI();
            if (gs.valid) {
                if (gs.rawState.optString("reason").equals("mobile_access_disabled")) {
                    return new Result(false, "mobile_access_disabled");
                }
                vtVehicle.noteUpdatedState(gs);
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
        Vehicle         v = vtVehicle.getVehicle();
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
        
        vtVehicle.noteUpdatedState(vs);
        vtVehicle.noteUpdatedState(cs);
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
        VTData.VoltageKey, VTData.CurrentKey,
        VTData.EstRangeKey, VTData.SOCKey,
        VTData.ROCKey, VTData.BatteryAmpsKey,
        VTData.SpeedKey, VTData.PowerKey,
    };
    private static final String[] locColumns = new String[] {
        VTData.LatitudeKey, VTData.LongitudeKey,
        VTData.HeadingKey, VTData.SpeedKey,
        VTData.OdometerKey, VTData.PowerKey
    };
    
    @FXML void exportHandler(ActionEvent event) {
        MenuItem mi = (MenuItem)event.getSource();
        if (mi == exportStatsMenuItem) { exportStats(statsColumns); }
        else if (mi == exportLocMenuItem) { exportStats(locColumns); }
        else if (mi == exportAllMenuItem) { exportStats(VTData.schema.columnNames); }
        else if (mi == exportChargeMenuItem) { exportCycles("Charge"); }
        else if (mi == exportRestMenuItem) { exportCycles("Rest"); }
        else if (mi == this.vampireLossMenuItem) { showVampireLoss(); }
    }
    
    // Options->"Inactivity Mode" menu items
    @FXML void inactivityOptionsHandler(ActionEvent event) {
        if (event.getTarget() == allowSleepMenuItem) app.api.allowSleeping();
        else app.api.stayAwake();
    }
    
    // Help->Documentation
    @FXML private void helpHandler(ActionEvent event) {
        app.showDocument(DocumentationURL);
    }
    
    // Help->What's New
    @FXML private void whatsNewHandler(ActionEvent event) {
        app.showDocument(ReleaseNotesURL);
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
                App.productName() + " " + App.productVersion(),
                "About " + App.productName());
    }

    // Help->Check for Updates
    @FXML private void updatesHandler(ActionEvent event) {
        if (!VersionUpdater.checkForNewerVersion(
                App.productVersion(),
                app.stage, app.getHostServices(),
                prefs.offerExperimental.get())) {
            Dialogs.showInformationDialog(
                    app.stage,
                    "You already have the latest release.",
                    "Update Check Results", "Checking for Updates");
        }
    }
    
    @FXML private void remoteStart(ActionEvent e) {
        final String[] unp = PasswordDialog.getCredentials(
                app.stage, "Authenticate", "Remote Start", false);
        if (unp == null) return;    // User cancelled
        if (unp[1] == null || unp[1].isEmpty()) {
            Dialogs.showErrorDialog(app.stage, "You must enter a password");
            return;
        }
        issuer.issueCommand(new Callable<Result>() {
            @Override public Result call() { 
                return vtVehicle.getVehicle().remoteStart(unp[1]); 
            } }, true, null, "Remote Start");
    }

    // Options->Action_>{Honk,Flsh,Wakeup}
    
    @FXML private void honk(ActionEvent e) {
        issuer.issueCommand(new Callable<Result>() {
            @Override public Result call() { return vtVehicle.getVehicle().honk(); }
        }, true, null, "Honk");
    }
    @FXML private void flash(ActionEvent e) {
        issuer.issueCommand(new Callable<Result>() {
            @Override public Result call() { return vtVehicle.getVehicle().flashLights(); }
        }, true, null, "Flash Lights");
    }
    @FXML private void wakeup(ActionEvent e) {
        issuer.issueCommand(new Callable<Result>() {
            @Override public Result call() { return vtVehicle.getVehicle().wakeUp(); }
        }, true, null, "Wake up");
    }
    
/*------------------------------------------------------------------------------
 *
 * Export Handling Methods
 * 
 *----------------------------------------------------------------------------*/
    
    private void exportCycles(String cycleType) {
        String initialDir = prefs.storage().get(
                App.LastExportDirKey, System.getProperty("user.home"));
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export " + cycleType + " Data");
        fileChooser.setInitialDirectory(new File(initialDir));

        Stage stage = app.stage;
        File file = fileChooser.showSaveDialog(stage);
        if (file != null) {
            String enclosingDirectory = file.getParent();
            if (enclosingDirectory != null)
                prefs.storage().put(App.LastExportDirKey, enclosingDirectory);
            Range<Long> exportPeriod = DateRangeDialog.getExportPeriod(stage);
            if (exportPeriod == null)
                return;
            boolean exported;
            if (cycleType.equals("Charge")) {
                exported = vtData.exportCharges(file, exportPeriod);
            } else {
                exported = vtData.exportRests(file, exportPeriod);
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
        String initialDir = prefs.storage().get(
                App.LastExportDirKey, System.getProperty("user.home"));
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Data");
        fileChooser.setInitialDirectory(new File(initialDir));

        File file = fileChooser.showSaveDialog(app.stage);
        if (file != null) {
            String enclosingDirectory = file.getParent();
            if (enclosingDirectory != null)
                prefs.storage().put(App.LastExportDirKey, enclosingDirectory);
            Range<Long> exportPeriod = DateRangeDialog.getExportPeriod(app.stage);
            if (exportPeriod == null)
                return;
            if (vtData.export(file, exportPeriod, columns)) {
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
    
    private void showVampireLoss() {
        Range<Long> exportPeriod = getExportPeriod();
        if (exportPeriod != null) {
            List<RestCycle> rests = vtData.getRestCycles(exportPeriod);
            boolean useMiles = vtVehicle.unitType() == Utils.UnitType.Imperial;

            // Compute some stats and generate detail output
            long totalRestTime = 0;
            double totalLoss = 0;
            for (RestCycle r : rests) {
                totalRestTime += r.endTime - r.startTime;
                totalLoss += r.startRange - r.endRange;
            }

            VampireLossResults.show(app.stage, rests, useMiles ? "mi" : "km", totalLoss/hours(totalRestTime));
        }
    }
            
    private double hours(long millis) {return ((double)(millis))/(60 * 60 * 1000); }

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
        Vehicle v = vtVehicle.getVehicle();
        String carName = (v != null) ? v.getDisplayName() : null;
        String title = App.productName() + " " + App.productVersion();
        if (carName != null) title = title + " for " + carName;
        if (app.api.isIdle()) {
            String time = String.format("%1$tH:%1$tM", new Date());
            title = title + " [sleeping at " + time + "]";
        }
        app.stage.setTitle(title);
    }

    private void setAppModeMenu() {
        if (app.api.allowingSleeping()) allowSleepMenuItem.setSelected(true);
        else stayAwakeMenuItem.setSelected(true);
    }

    private void logAppInfo() {
        logger.info(App.productName() + ": " + App.productVersion());
        
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
                if (app.api.mode.lastSet() > lastEval && app.api.stayingAwake()) return true;
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
            return (app.api.isActive() && app.api.state.lastSet() > last);
        }
    }
    
    private class PassiveCollectionPredicate implements Utils.Predicate {
        @Override public boolean eval() {
            return (app.api.isIdle() && app.api.allowingSleeping());
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
    
    private void showDisclaimer() {
        boolean disclaimer = prefs.storage().getBoolean("Disclaimer", false);
        if (!disclaimer) {
            Dialogs.showInformationDialog(
                    app.stage,
                    "Use this application at your own risk. The author\n" +
                    "does not guarantee its proper functioning.\n" +
                    "It is possible that use of this application may cause\n" +
                    "unexpected damage for which nobody but you are\n" +
                    "responsible. Use of this application can change the\n" +
                    "settings on your car and may have negative\n" +
                    "consequences such as (but not limited to):\n" +
                    "unlocking the doors, opening the sun roof, or\n" +
                    "reducing the available charge in the battery.",
                    "Please Read Carefully", "Disclaimer");
        }
        prefs.storage().putBoolean("Disclaimer", true);                
    }
    
    private Range<Long> getExportPeriod() {
        NavigableMap<Long,Row> rows = vtData.getAllLoadedRows();
        long timestamp = rows.firstKey(); 
        Calendar start = Calendar.getInstance();
        start.setTimeInMillis(timestamp);
        
        timestamp = rows.lastKey(); 
        Calendar end = Calendar.getInstance();
        end.setTimeInMillis(timestamp);
        
        Range<Long> exportPeriod = DateRangeDialog.getExportPeriod(app.stage, start, end);
        return exportPeriod;
    }
}
