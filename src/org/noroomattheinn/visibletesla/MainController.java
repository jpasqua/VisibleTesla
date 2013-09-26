/*
 * MainController.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Jul 22, 2013
 */

package org.noroomattheinn.visibletesla;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Dialogs;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.image.Image;
import javafx.scene.input.InputEvent;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import org.noroomattheinn.tesla.Options;
import org.noroomattheinn.tesla.Tesla;
import org.noroomattheinn.tesla.Vehicle;
import org.noroomattheinn.utils.Utils;

/**
 * This is the main application code for VisibleTesla. It does not contain
 * the main function. Main is in VisibleTesla.java which is mostly just a shell.
 * This controller is associated with the Tab panel in which all of the 
 * individual tabs live.
 * 
 * TO DO:
 * - If the user has more than one vehicle on their account, the app should pop
 *   up a list and let them  select a specific car. Perhaps represent them by 
 *   vin and small colored car icon.
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class MainController {
    
/*------------------------------------------------------------------------------
 *
 * Constants and Enums
 * 
 *----------------------------------------------------------------------------*/

    private static final String ProductName = "VisibleTesla";
    private static final String ProductVersion = "0.20.00";
    private static final long IdleThreshold = 15 * 60 * 1000;   // 15 Minutes

/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/

    private Tesla tesla;
    private List<Vehicle> vehicles;
    private Vehicle selectedVehicle;
    private boolean allowSleep;
    private AppContext appContext;

/*------------------------------------------------------------------------------
 *
 * UI Elements
 * 
 *----------------------------------------------------------------------------*/

    @FXML private ResourceBundle resources;
    @FXML private URL location;
    
    // The top level AnchorPane and the TabPane that sits inside it
    @FXML private AnchorPane root;
    @FXML private TabPane tabPane;

    // The individual tabs that comprise the overall UI
    @FXML private Tab schedulerTab;
    @FXML private Tab graphTab;
    @FXML private Tab chargeTab;
    @FXML private Tab hvacTab;
    @FXML private Tab locationTab;
    @FXML private Tab loginTab;
    @FXML private Tab overviewTab;
    @FXML private CheckMenuItem allowSleepMenuItem;
    
    
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
     * @param a 
     */
    public void start(Application a, Stage s) {   
        appContext = new AppContext(a, s);
        
        allowSleep = allowSleepMenuItem.isSelected();
        appContext.shouldBeSleeping.set(false);
        appContext.shouldBeSleeping.addListener(new ChangeListener<Boolean>() {
            @Override public void changed(
                ObservableValue<? extends Boolean> o, Boolean ov, Boolean nv)
            { setTitle(); } });


        setTitle();
        appContext.stage.getIcons().add(new Image(getClass().getClassLoader().getResourceAsStream(
                "org/noroomattheinn/TeslaResources/Icon-72@2x.png")));

        tesla = new Tesla();

        tabPane.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<Tab>() {
            public void changed(ObservableValue<? extends Tab> ov, Tab t, Tab t1) {
                BaseController c = controllerFromTab(t1);
                if (c != null) {
                    c.activate(selectedVehicle);
                }
            }
        });

        controllerFromTab(loginTab).setAppContext(appContext);
        controllerFromTab(schedulerTab).setAppContext(appContext);
        controllerFromTab(graphTab).setAppContext(appContext);
        controllerFromTab(chargeTab).setAppContext(appContext);
        controllerFromTab(hvacTab).setAppContext(appContext);
        controllerFromTab(locationTab).setAppContext(appContext);
        controllerFromTab(overviewTab).setAppContext(appContext);
        
        LoginController lc = Utils.cast(controllerFromTab(loginTab));
        lc.getLoginCompleteProperty().addListener(new HandleLoginEvent());
        lc.attemptAutoLogin(tesla);
    }
    
    public void stop() {
        appContext.shutDown();
        SchedulerController sc = Utils.cast(controllerFromTab(schedulerTab));
        sc.shutDown();
    }
    
    // Not really a public interface, but this is called by the FXML plumbing
    // to initialize the component
    @FXML void initialize() { root.setUserData(this); }

    
/*------------------------------------------------------------------------------
 *
 * Dealing with a Login Event
 * 
 *----------------------------------------------------------------------------*/
    
    /**
     * Whenever we try to login, a boolean property is set with the result of
     * the attempt. We monitor changes on that boolean property and if we see
     * a successful login, we gather the appropriate state and make the app
     * ready to go.
     */
    private class HandleLoginEvent implements ChangeListener<Boolean> {
        @Override public void changed(
                ObservableValue<? extends Boolean> observable,
                Boolean oldValue, Boolean newValue) {
            Platform.runLater(new DoLogin(newValue));
        }
    }

    private class DoLogin implements Runnable {
        private boolean loginSucceeded;
        DoLogin(boolean loggedin) { loginSucceeded = loggedin; }
        
        @Override
        public void run() {
            if (loginSucceeded) {
                vehicles = tesla.getVehicles();
                selectedVehicle = vehicles.get(0);  // TO DO: Allow user to select vehicle from list

                if (selectedVehicle.status().equals("asleep")) {
                    if (letItSleep())
                        Platform.exit();
                }
                
                allowSleep = appContext.prefs.getBoolean(
                        selectedVehicle.getVIN()+"_AllowSleep", false);
                allowSleepMenuItem.setSelected(allowSleep);
                trackInactivity();
                setTabsEnabled(true);
                jumpToTab(overviewTab);
                SchedulerController sc = Utils.cast(controllerFromTab(schedulerTab));
                sc.activate(selectedVehicle);
            } else {
                vehicles = null;
                selectedVehicle = null;
                setTabsEnabled(false);
            }
        }
        
    }
    
/*------------------------------------------------------------------------------
 *
 * Private Utility Methods - General
 * 
 *----------------------------------------------------------------------------*/

    private void setTitle() {
        String title = ProductName + " " + ProductVersion;
        if (appContext.shouldBeSleeping.get())
            title = title + " [inactive]";
                appContext.stage.setTitle(title);
    }
    

    private boolean letItSleep() {
        DialogUtils.DialogController dc = DialogUtils.displayDialog(
                getClass().getResource("WakeSleepDialog.fxml"),
                "Wake up your car?", appContext.stage);
        if (dc == null) return true;
        WakeSleepDialog wsd = Utils.cast(dc);
        return wsd.letItSleep();
    }
    

/*------------------------------------------------------------------------------
 *
 * Private Utility Methods for Tab handling
 * 
 *----------------------------------------------------------------------------*/
    

    private void setTabsEnabled(boolean enabled) {
        schedulerTab.setDisable(!enabled);
        graphTab.setDisable(!enabled);
        chargeTab.setDisable(!enabled);
        hvacTab.setDisable(!enabled);
        locationTab.setDisable(!enabled);
        loginTab.setDisable(false);     // The Login Tab is always enabled
        overviewTab.setDisable(!enabled);
    }
    
    private void jumpToTab(final Tab tab) {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                tabPane.getSelectionModel().select(tab);    // Jump to Overview Tab     
            }
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
 * This section implements UI Actionhandlers
 * 
 *----------------------------------------------------------------------------*/
    
    // File->Close
    @FXML void closeHandler(ActionEvent event) {
        Platform.exit();
    }
    
    // File->Export Graph Data...
    @FXML void exportHandler(ActionEvent event) {
        GraphController gc = Utils.cast(controllerFromTab(graphTab));
        gc.exportCSV();
    }
    
    // File->Allow Sleep menu option
    @FXML void allowSleepHandler(ActionEvent event) {
        allowSleep = allowSleepMenuItem.isSelected();
        if (allowSleep == false)
            appContext.shouldBeSleeping.set(false);
        appContext.prefs.putBoolean(
                selectedVehicle.getVIN()+"_AllowSleep", allowSleep);

    }
    // Help->Documentation
    @FXML private void helpHandler(ActionEvent event) {
        appContext.app.getHostServices().showDocument(
                appContext.app.getHostServices().getDocumentBase() +
                "Documentation/Overview.html");
    }
    
    // Help->What's New
    @FXML private void whatsNewHandler(ActionEvent event) {
        appContext.app.getHostServices().showDocument(
                appContext.app.getHostServices().getDocumentBase() +
                "Documentation/ReleaseNotes.html");
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
                ProductName + " " + ProductVersion, "About " + ProductName);
    }

/*------------------------------------------------------------------------------
 * 
 * This section implements the mechanism that tracks whether the app is idle.
 * It does it by hooking the event stream and looking for mouse or keyboard
 * activity. A separate thread checks periodically to see how long it's been
 * since the last event. If a threshold is passed, we say that we're idle.
 * 
 *----------------------------------------------------------------------------*/

    private long timeOfLastEvent = System.currentTimeMillis();

    private void trackInactivity() {
        appContext.stage.addEventFilter(KeyEvent.ANY, new EventPassThrough());
        appContext.stage.addEventFilter(MouseEvent.MOUSE_PRESSED, new EventPassThrough());
        appContext.stage.addEventFilter(MouseEvent.MOUSE_RELEASED, new EventPassThrough());
        appContext.launchThread(new InactivityThread(), "00 Inactivity");
    }
    
    class InactivityThread implements Runnable {
        @Override public void run() {
            while (true) {
                Utils.sleep(IdleThreshold/2);
                if (appContext.shuttingDown.get())
                    return;
                if (allowSleep && System.currentTimeMillis() - timeOfLastEvent > IdleThreshold) {
                    appContext.shouldBeSleeping.set(true);
                }
            }
        }
    }
    
    class EventPassThrough implements EventHandler<InputEvent> {
        @Override public void handle(InputEvent t) {
            timeOfLastEvent = System.currentTimeMillis();
            if (appContext.shouldBeSleeping.get() == true)
                appContext.shouldBeSleeping.set(false);
        }
    }
    

/*------------------------------------------------------------------------------
 * 
 * Everything below has to do with simulated vehicle properties. For example,
 * the user can ask to simulate a different car color, roof type, or wheel
 * type. The simulation options are selected here and implemented in the
 * OverviewController. They could be implemented in the lower level state
 * objects, but I decided to keep those unsullied by this stuff.
 * 
 * TO DO: This way of handling the simulation feels clunky. Perhaps do it
 * with observable properties instead
 * 
 *----------------------------------------------------------------------------*/
    
    @FXML MenuItem simColorRed, simColorBlue, simColorGreen, simColorBrown, simColorBlack;
    @FXML MenuItem simColorSigRed, simColorSilver, simColorGray, simColorPearl, simColorWhite;
    @FXML MenuItem darkRims, lightRims, silver19Rims;
    @FXML MenuItem simSolidRoof, simPanoRoof, simBlackRoof;
    @FXML private MenuItem simImperialUnits, simMetricUnits;

    @FXML void simUnitsHandler(ActionEvent event) {
        MenuItem source = (MenuItem)event.getSource();
        Utils.UnitType ut = (source == simImperialUnits) ? Utils.UnitType.Imperial : Utils.UnitType.Metric;
        appContext.simulatedUnits.set(ut);
    }
    
    @FXML void simRimsHandler(ActionEvent event) {
        MenuItem source = (MenuItem)event.getSource();
        Options.WheelType simWheels = null;

        if (source == darkRims) simWheels = Options.WheelType.WTSP;
        else if (source == lightRims) simWheels = Options.WheelType.WT21;
        else if (source == silver19Rims) simWheels = Options.WheelType.WT19;
        
        if (simWheels != null)
            appContext.simulatedWheels.set(simWheels);
    }
    
    @FXML void simColorHandler(ActionEvent event) {
        MenuItem source = (MenuItem)event.getSource();
        ObjectProperty<Options.PaintColor> pc = appContext.simulatedColor;
        if (source == simColorRed) pc.set(Options.PaintColor.PPMR);
        else if (source == simColorGreen) pc.set(Options.PaintColor.PMSG);
        else if (source == simColorBlue) pc.set(Options.PaintColor.PMMB);
        else if (source == simColorBlack) pc.set(Options.PaintColor.PBSB);
        else if (source == simColorSilver) pc.set(Options.PaintColor.PMSS);
        else if (source == simColorGray) pc.set(Options.PaintColor.PMTG);
        else if (source == simColorSigRed) pc.set(Options.PaintColor.PPSR);
        else if (source == simColorPearl) pc.set(Options.PaintColor.PPSW);
        else if (source == simColorBrown) pc.set(Options.PaintColor.PMAB);
        else if (source == simColorWhite) pc.set(Options.PaintColor.PPSW);
    }

    @FXML void simRoofHandler(ActionEvent event) {
        MenuItem source = (MenuItem)event.getSource();
        ObjectProperty<Options.RoofType> rt = appContext.simulatedRoof;
        if (source == simSolidRoof) rt.set(Options.RoofType.RFBC);
        else if (source == simBlackRoof) rt.set(Options.RoofType.RFBK);
        else if (source == simPanoRoof) rt.set(Options.RoofType.RFPO);
    }

}
