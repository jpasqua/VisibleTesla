/*
 * MainController.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Jul 22, 2013
 */

package org.noroomattheinn.visibletesla;

import org.noroomattheinn.visibletesla.dialogs.WakeSleepDialog;
import org.noroomattheinn.visibletesla.dialogs.DialogUtils;
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
import org.noroomattheinn.tesla.Result;
import org.noroomattheinn.tesla.Tesla;
import org.noroomattheinn.utils.Utils;
import org.noroomattheinn.visibletesla.dialogs.DisclaimerDialog;
import org.noroomattheinn.visibletesla.dialogs.SelectVehicleDialog;
import org.noroomattheinn.visibletesla.dialogs.VersionUpdater;

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
    @FXML private Button wakeButton;
    
    private List<Tab> tabs;
    
    @FXML private MenuItem exportStatsMenuItem, exportLocMenuItem;
    @FXML private MenuItem vampireLossMenuItem;
    
    // The menu items that are handled in this controller directly
    @FXML private RadioMenuItem allowSleepMenuItem;
    @FXML private RadioMenuItem stayAwakeMenuItem;
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/

    /**
     * Called by the main application to allow us to store away the app context
     * and perform any other app startup tasks. In particular, we (1) distribute
     * app context to all of the controllers, and (2) we set a listener for login
     * completion and try and automatic login.
     * @param ac    The AppContext
     */
    public void start(AppContext ac) {
        appContext = ac;
        appContext.utils.logAppInfo();
        addSystemSpecificHandlers(ac);

        setTitle();
        appContext.stage.getIcons().add(new Image(getClass().getClassLoader().getResourceAsStream(
                "org/noroomattheinn/TeslaResources/Icon-72@2x.png")));

        tabPane.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<Tab>() {
            @Override public void changed(ObservableValue<? extends Tab> ov, Tab t, Tab t1) {
                BaseController c = controllerFromTab(t1);
                if (c != null) { c.activate(); }
            }
        });

        tabs = Arrays.asList(prefsTab, loginTab, schedulerTab, graphTab, chargeTab,
                             hvacTab, locationTab, overviewTab, tripsTab, notifierTab);
        for (Tab t : tabs) { controllerFromTab(t).setAppContext(appContext); }
        
        // Handle font scaling
        int fontScale = appContext.prefs.fontScale.get();
        if (fontScale != 100) {
            for (Tab t : tabs) { 
                Node n = t.getContent();
                n.setStyle(String.format("-fx-font-size: %d%%;", fontScale));
            }
        }
        
        // Watch for changes to the inactivity mode and state in order to update the UI
        appContext.inactivity.addModeListener(new Inactivity.Listener() {
            @Override public void handle(Inactivity.Type nv) { setInactivityMenu(nv); } });
        appContext.inactivity.addStateListener(new Inactivity.Listener() {
            @Override public void handle(Inactivity.Type nv) { setTitle(); } });

        // Kick off the login process
        LoginController lc = Utils.cast(controllerFromTab(loginTab));
        lc.loggedIn.addTracker(true, new LoginStateChange(lc.loggedIn, false));
        lc.attemptAutoLogin();
    }
    
    public void stop() { appContext.tm.shutDown(); }
    
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
                Result r = appContext.utils.cacheBasics();
                if (!r.success) {
                    if (r.explanation.equals("mobile_access_disabled"))  showMobileAccessError();
                    else showCachingError();
                    Platform.exit();
                    return Result.Failed;
                }
                Platform.runLater(finishAppStartup);
                return Result.Succeeded;
            } });
    }

    
    private class LoginStateChange implements Runnable {
        private final VTUtils.StateTracker<Boolean> loggedIn;
        private final boolean assumeAwake;
        
        LoginStateChange(VTUtils.StateTracker<Boolean> loggedIn, boolean assumeAwake) {
            this.loggedIn = loggedIn;
            this.assumeAwake = assumeAwake;
        }
        
        @Override public void run() {
            if (!loggedIn.get()) {
                appContext.vehicle = null;
                setTabsEnabled(false);
                return;
            }

            if (assumeAwake) {
                wakePane.setVisible(false);
            } else {
                appContext.vehicle = SelectVehicleDialog.select(appContext);
                if (!appContext.lockAppInstance()) {
                    showLockError();
                    Platform.exit();
                }
                Tesla.logger.info("Vehicle Info: " + appContext.vehicle.getUnderlyingValues());

                if (appContext.vehicle.status().equals("asleep")) {
                    if (letItSleep()) {
                        Tesla.logger.info("Allowing vehicle to remain in sleep mode");
                        wakePane.setVisible(true);
                        appContext.utils.waitForVehicleToWake(
                                new LoginStateChange(loggedIn, true), forceWakeup);
                        return;
                    } else {
                        Tesla.logger.log(Level.INFO, "Waking up your vehicle");
                    }
                }
            }
                
            DisclaimerDialog.show(appContext);
            VersionUpdater.conditionalCheckVersion(appContext);
            appContext.inactivity.restore();
            fetchInitialCarState();
        }
    }
    
    private Runnable finishAppStartup = new Runnable() {
        @Override public void run() {
            appContext.inactivity.trackInactivity(tabs);
            appContext.prepForVehicle(appContext.vehicle);
            
            // Start the Scheduler and the Notifier
            controllerFromTab(schedulerTab).activate();
            controllerFromTab(notifierTab).activate();
            
            setTabsEnabled(true);
            jumpToTab(overviewTab);
        }
    };
    
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
    @FXML void exportHandler(ActionEvent event) {
        MenuItem mi = (MenuItem)event.getSource();
        if (mi == exportStatsMenuItem)
            appContext.statsStore.exportCSV();
        if (mi == exportLocMenuItem)
            appContext.locationStore.exportCSV();
        if (mi == this.vampireLossMenuItem) {
            appContext.vampireStats.showStats();
        }
    }
    
    // Options->"Inactivity Mode" menu items
    @FXML void inactivityOptionsHandler(ActionEvent event) {
        Inactivity.Type mode = Inactivity.Type.Awake;
        if (event.getTarget() == allowSleepMenuItem) mode = Inactivity.Type.Sleep;
        appContext.inactivity.setMode(mode);
    }
    
    // Help->Documentation
    @FXML private void helpHandler(ActionEvent event) {
        appContext.app.getHostServices().showDocument(DocumentationURL);
    }
    
    // Help->What's New
    @FXML private void whatsNewHandler(ActionEvent event) {
        appContext.app.getHostServices().showDocument(ReleaseNotesURL);
    }
    
    // Help->About
    @FXML private void aboutHandler(ActionEvent event) {
        Dialogs.showInformationDialog(
                appContext.stage,
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
        if (!VersionUpdater.checkForNewerVersion(appContext)) 
            Dialogs.showInformationDialog(
                    appContext.stage,
                    "There is no newer version available.",
                    "Update Check Results", "Checking for Updates");
    }
    
    // Options->Action_>{Honk,Flsh,Wakeup}
    @FXML private void honk(ActionEvent e) { appContext.utils.miscAction(VTUtils.MiscAction.Honk); }
    @FXML private void flash(ActionEvent e) { appContext.utils.miscAction(VTUtils.MiscAction.Flash); }
    @FXML private void wakeup(ActionEvent e) { appContext.utils.miscAction(VTUtils.MiscAction.Wakeup); }
    
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
    
    private void setTitle() {
        String title = AppContext.ProductName + " " + AppContext.ProductVersion;
        if (appContext.inactivity.getState() == Inactivity.Type.Sleep) {
            String time = String.format("%1$tH:%1$tM", new Date());
            title = title + " [sleeping at " + time + "]";
        }
        appContext.stage.setTitle(title);
    }

    private void setInactivityMenu(Inactivity.Type mode) {
        switch (mode) {
            case Awake: stayAwakeMenuItem.setSelected(true); break;
            case Sleep: allowSleepMenuItem.setSelected(true); break;
        }
    }
    
/*------------------------------------------------------------------------------
 *
 * Display various info and warning dialogs
 * 
 *----------------------------------------------------------------------------*/
    
    private boolean letItSleep() {
        DialogUtils.DialogController dc = DialogUtils.displayDialog(
                getClass().getResource("dialogs/WakeSleepDialog.fxml"),
                "Wake up your car?", appContext.stage, null);
        if (dc == null) return true;
        WakeSleepDialog wsd = Utils.cast(dc);
        return wsd.letItSleep();
    }
    
    @FXML private void wakeButtonHandler(ActionEvent event) { forceWakeup.set(true); }
    
    private void showMobileAccessError() {
        Platform.runLater(new Runnable() {
            @Override public void run() {
                Dialogs.showErrorDialog(appContext.stage,
                        "Your Tesla has not been configured to allow mobile " +
                        "access. You have to enable this on your car's touch"  +
                        "screen using Controls / Settings / Vehicle." +
                        "\n\nChange that setting in your car, then relaunch VisibleTesla.",
                        "Mobile access is not enabled", "Communication Problem");
                Tesla.logger.log(Level.SEVERE, "Mobile access is not enabled - exiting.");
            }
        });
    }
    
    private void showCachingError() {
        Platform.runLater(new Runnable() {
            @Override public void run() {
                Dialogs.showErrorDialog(appContext.stage,
                        "Failed to connect to your vehicle even after a successful " +
                        "login. It may be in a deep sleep and can't be woken up.\n"  +
                        "\nPlease try to wake your Tesla and then try VisibleTesla again.",
                        "Unable to communicate with your Tesla", "Communication Problem");
                Tesla.logger.severe("Can't communicate with vehicle - exiting.");
            }
        });
    }
    
    private void showLockError() {
        Dialogs.showErrorDialog(appContext.stage,
            "There appears to be another copy of VisibleTesla\n" +
            "running on this computer and trying to talk\n" +
            "to the same car. That can cause problems and\n" +
            "is not allowed\n\n"+
            "VisibleTesla will close when you close this window.",
            "Multiple Copies of VisibleTesla", "Problem launching application");
    }
}
