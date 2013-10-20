/*
 * OverviewController.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Jul 22, 2013
 */

package org.noroomattheinn.visibletesla;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Dialogs;
import javafx.scene.control.Dialogs.DialogOptions;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import org.apache.commons.lang3.StringUtils;
import org.noroomattheinn.tesla.ActionController;
import org.noroomattheinn.tesla.ChargeState;
import org.noroomattheinn.tesla.DoorController;
import org.noroomattheinn.tesla.DoorController.PanoCommand;
import org.noroomattheinn.tesla.VehicleState;
import org.noroomattheinn.tesla.GUIState;
import org.noroomattheinn.tesla.Options;
import org.noroomattheinn.tesla.Options.PaintColor;
import org.noroomattheinn.tesla.Options.WheelType;
import org.noroomattheinn.tesla.Result;
import org.noroomattheinn.tesla.SnapshotState;
import org.noroomattheinn.tesla.Vehicle;
import org.noroomattheinn.utils.RestyWrapper;


public class OverviewController extends BaseController {
/*------------------------------------------------------------------------------
 *
 * Constants and Enums
 * 
 *----------------------------------------------------------------------------*/

    private static final double KilometersPerMile = 1.60934;
    public enum RoofState {Open, Closed, Vent, Solid};

/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/

    private VehicleState        vehicleState;   // The primary door state
    private SnapshotState       snapshotState;  // For odometer reading
    private ChargeState         chargeState;    // For chargePortDoor status
    private DoorController      doorController; // For primary door controller
    private ActionController    actions;        // For honk, flash, wake commands
    private double              storedOdometerReading;
    private Callable<Result>    wakeupCB, flashCB, honkCB;
    
/*------------------------------------------------------------------------------
 *
 * UI Elements
 * 
 *----------------------------------------------------------------------------*/
    
    // Lock Status Images
    @FXML private ImageView lockedImg;
    @FXML private ImageView unlockedImg;

    //
    // Car Images (and Labels)
    //
    @FXML private ImageView bodyImg;
    @FXML private ImageView darkRimFront, darkRimRear;
    @FXML private ImageView nineteenRimFront, nineteenRimRear;

    // Driver Side
    @FXML private ImageView dfOpenImg, dfClosedImg;
    @FXML private ImageView drOpenImg, drClosedImg;

    // Passenger Side
    @FXML private ImageView pfOpenImg;
    @FXML private ImageView prOpenImg;

    // Trunk/Frunk
    @FXML private ImageView ftClosedImg, ftOpenImg;
    @FXML private ImageView rtOpenImg, rtClosedImg;
    @FXML private ImageView spoilerClosedImg, spoilerOpenImg;

    // Roof Images (Solipath+Pano: Open, Closepath+Vent)
    @FXML private ImageView blackRoofImg, solidRoofImg;
    @FXML private ImageView panoClosedImg, panoVentImg, panoOpenImg;
    @FXML private Label panoPercent;

    // Charging related images
    @FXML private ImageView chargeCableImg, portClosedImg, portOpenImg, greenGlowImage;
    
    // Other Labels
    @FXML private Label odometerLabel;
    @FXML private Label vinLabel;
    
    //
    // Controls
    //
    @FXML private Button honkButton, flashButton, wakeupButton;
    @FXML private Button lockButton, unlockButton;
    @FXML private Button closePanoButton, ventPanoButton, openPanoButton;
    
/*------------------------------------------------------------------------------
 *
 *  UI Action Handlers
 * 
 *----------------------------------------------------------------------------*/

    @FXML void lockButtonHandler(ActionEvent event) {
        final Button source = (Button)event.getSource();
        issueCommand(new Callable<Result>() {
            @Override public Result call() {
                return doorController.setLockState(source == lockButton); } }, AfterCommand.Refresh);
    }

    @FXML void miscCommandHandler(ActionEvent event) {
        Button source = (Button)event.getSource();
        if (source == honkButton)  issueCommand(honkCB, AfterCommand.Nothing);
        else if (source == flashButton) issueCommand(flashCB, AfterCommand.Nothing);
        else if (source == wakeupButton) issueCommand(wakeupCB, AfterCommand.Nothing);
    }

    @FXML void panoButtonHandler(ActionEvent event) {
        Button source = (Button)event.getSource();
        final PanoCommand cmd = 
            source == ventPanoButton ? PanoCommand.vent :
                ((source == openPanoButton) ? PanoCommand.open : PanoCommand.close);
        issueCommand(new Callable<Result>() {
            @Override public Result call() { return doorController.setPano(cmd); } },
            AfterCommand.Refresh);
    }

    @FXML void detailsButtonHandler(ActionEvent event) {
        AnchorPane pane = new AnchorPane();
        String info = vehicle.toString() +
                "\nFirmware Version: " + vehicleState.version() +
                "\nHas Spoiler: " + vehicleState.hasSpoiler() +
                "\n--------------------------------------------" +
                "\nLow level information: " + vehicle.getUnderlyingValues() +
                "\nAPI Usage Rates:";
        for (Map.Entry<Integer,Integer> e: RestyWrapper.stats().entrySet()) {
            int seconds = e.getKey();
            int calls = e.getValue();
            info += "\n    "+ calls + " calls in the last " + seconds + " seconds";
        }

        TextArea t = new TextArea(info);
        pane.getChildren().add(t);
        Dialogs.showCustomDialog(
            appContext.stage, pane, "Detailed Vehicle Description", "Details", DialogOptions.OK, null);
    }
    
/*------------------------------------------------------------------------------
 *
 * Methods overridden from BaseController
 * 
 *----------------------------------------------------------------------------*/
    
    @Override protected void fxInitialize() {
        odometerLabel.setVisible(true);
        honkCB = new Callable<Result>() {@Override public Result call() { return actions.honk(); } };
        flashCB = new Callable<Result>() {@Override  public Result call() { return actions.flashLights(); } };
        wakeupCB = new Callable<Result>() {@Override  public Result call() { return actions.wakeUp(); } };
    }

    @Override protected void appInitialize() {
        appContext.simulatedColor.addListener(new ChangeListener<PaintColor>() {
            @Override public void changed(
                    ObservableValue<? extends PaintColor> ov,
                    PaintColor oldPaint, PaintColor newPaint) {
                getAppropriateImages(vehicle);
            }
        });
    }
    
    /**
     * Refresh the state either because the user requested it or because the 
     * auto-refresh interval has passed. We always update the vehicleState and
     * chargeState. Getting the odometer reading can be more burdensome because
     * it has to be done through the streaming API. We only do that every 3rd
     * time refresh is invoked, or if the user pressed the refresh button.
     * This keeps down our request rate to the tesla servers.
     * 
     */
    @Override protected void refresh() {
        updateState(vehicleState);
        updateState(chargeState);
        if (userInvokedRefresh || refreshCount % 3 == 0) {
            updateState(snapshotState);
        }
        refreshCount++;
    }
    static private int refreshCount = 0;
    
    @Override protected void prepForVehicle(Vehicle v) {
        if (differentVehicle(actions, v)) {
            actions = new ActionController(v);
            doorController = new DoorController(v);
            getAppropriateImages(v);

            vehicleState = new VehicleState(v);
            snapshotState = new SnapshotState(v);
            chargeState = new ChargeState(v);
            
            storedOdometerReading = appContext.prefs.getDouble(v.getVIN()+"_odometer", 0);

            updateWheelView();  // Make sure we display the right wheels from the get-go
            updateRoofView();   // Make sure we display the right roof from the get-go
            if (storedOdometerReading != 0)
                updateOdometer();   // Show at least an old reading to start
            
            vinLabel.setText("VIN " + StringUtils.right(v.getVIN(), 6));
        }
    }
    
    @Override protected void reflectNewState() {
        if (!vehicleState.hasValidData())    // Data's not ready yet
            return;

        updateWheelView();
        updateRoofView();
        updateDoorView();
        updateOdometer();
        updateChargePort();
    }
    
    

/*------------------------------------------------------------------------------
 *
 * Methods to Reflect the overall state of the vehicle
 * 
 *----------------------------------------------------------------------------*/
    
    
    private void updateDoorView() {
        boolean rtOpen = vehicleState.isRTOpen();
        
        // Show the open/closed state of the doors and trunks
        setOptionState(vehicleState.isFTOpen(), ftOpenImg, ftClosedImg);
        setOptionState(rtOpen, rtOpenImg, rtClosedImg);
        setOptionState(vehicleState.isDFOpen(), dfOpenImg, dfClosedImg);
        setOptionState(vehicleState.isPFOpen(), pfOpenImg, null);
        setOptionState(vehicleState.isDROpen(), drOpenImg, drClosedImg);
        setOptionState(vehicleState.isPROpen(), prOpenImg, null);
        setOptionState(vehicleState.locked(), lockedImg, unlockedImg);
        
        spoilerOpenImg.setVisible(false); spoilerClosedImg.setVisible(false);
        if (vehicleState.hasSpoiler()) {
            setOptionState(rtOpen, spoilerOpenImg, spoilerClosedImg);
        }        
    }
    
    private void updateRoofView() {
        Options.RoofType type = (appContext.simulatedRoof.get() == null) ?
            vehicle.getOptions().roofType() : appContext.simulatedRoof.get();
        boolean hasPano = (type == Options.RoofType.RFPO);
        
        // Start with all images set to invisible, then turn on the one right one
        panoOpenImg.setVisible(false);
        panoClosedImg.setVisible(false);
        panoVentImg.setVisible(false);
        solidRoofImg.setVisible(false);
        blackRoofImg.setVisible(false);
        // Only show the pano controls and percent if we have a pano roof
        closePanoButton.setVisible(hasPano);
        ventPanoButton.setVisible(hasPano);
        openPanoButton.setVisible(hasPano);
        panoPercent.setVisible(hasPano);
        
        if (hasPano)
            updatePanoView();
        else 
            setOptionState(type == Options.RoofType.RFBC, solidRoofImg, blackRoofImg);
    }
    
    private void updatePanoView() {
        int pct = (vehicleState.hasValidData()) ? vehicleState.panoPercent() : 0;
        
        if (pct == 0) panoClosedImg.setVisible(true);
        else if (pct > 0 && pct < 90) panoVentImg.setVisible(true);
        else panoOpenImg.setVisible(true);
        panoPercent.setText(String.valueOf(pct) + " %");
    }
    
    private void updateWheelView() {
        WheelType wt = (appContext.simulatedWheels.get() == null) ?
                vehicle.getOptions().wheelType() : appContext.simulatedWheels.get();
        
        nineteenRimFront.setVisible(false);
        nineteenRimRear.setVisible(false);
        darkRimFront.setVisible(false);
        darkRimRear.setVisible(false);
        switch (wt) {
            case WT19:
                nineteenRimFront.setVisible(true);
                nineteenRimRear.setVisible(true);
                break;
            case WTSP:
            case WTSG:
                darkRimFront.setVisible(true);
                darkRimRear.setVisible(true);
                break;
            case WT21:
            default:    // Unknown, use default which is WT21
                break;
        }
    }
      
    private void updateChargePort() {
        if (!chargeState.hasValidData()) return; // No data available yet...
        
        int pilotCurrent = chargeState.chargerPilotCurrent();
        boolean chargePortDoorOpen = (chargeState.chargePortOpen() || pilotCurrent > 0);
        setOptionState(chargePortDoorOpen, portOpenImg, portClosedImg);
        chargeCableImg.setVisible(pilotCurrent > 0);
        greenGlowImage.setVisible(chargeState.chargingState() == ChargeState.State.Charging);
    }
    
    private void updateOdometer() {
        double odometerReading = (snapshotState.hasValidData()) ?
                snapshotState.odometer() : storedOdometerReading;
        if (odometerReading == 0) return;   // The reading isn't ready yet
        
        // Save off the odometer reading (in miles)
        appContext.prefs.putDouble(vehicle.getVIN()+"_odometer", odometerReading);
        GUIState gs = appContext.cachedGUIState;
        boolean useMiles = gs.distanceUnits().equalsIgnoreCase("mi/hr");
        String units = useMiles ? "mi" : "km";
        odometerReading *= useMiles ? 1.0 : KilometersPerMile;
        odometerLabel.setText(String.format("Odometer: %.1f %s", odometerReading, units));
    }
    
    
/*------------------------------------------------------------------------------
 *
 * Data and Methods for locating the right images based on vehicle parameters
 * 
 *----------------------------------------------------------------------------*/

    // This Map maps from a PaintColor to a directory name which holds the
    // images for that color. As new colors are added by Tesla, the map
    // must be udated (as must the PaintColor enum).
    private static final Map<Options.PaintColor,String> colorToDirectory = new HashMap<>();
    static {
        colorToDirectory.put(Options.PaintColor.PBCW, "COLOR_white/");
        colorToDirectory.put(Options.PaintColor.PBSB, "COLOR_black/");
        colorToDirectory.put(Options.PaintColor.PMAB, "COLOR_brown/");
        colorToDirectory.put(Options.PaintColor.PMMB, "COLOR_blue/");
        colorToDirectory.put(Options.PaintColor.PMSG, "COLOR_green/");
        colorToDirectory.put(Options.PaintColor.PMSS, "COLOR_silver/");
        colorToDirectory.put(Options.PaintColor.PMTG, "COLOR_gray/");
        colorToDirectory.put(Options.PaintColor.PPMR, "COLOR_newred/");
        colorToDirectory.put(Options.PaintColor.PPSR, "COLOR_red/");
        colorToDirectory.put(Options.PaintColor.PPSW, "COLOR_pearl/");
        colorToDirectory.put(Options.PaintColor.Unknown, "COLOR_white/");
    }

    // Where the images are stored relative to the classpath
    private static final String ImagePrefix = "org/noroomattheinn/TeslaResources/";

    // Replace the images that were selected by default with images for the actual color
    private void getAppropriateImages(Vehicle v) {
        Options.PaintColor c = appContext.simulatedColor.get() != null ?
                appContext.simulatedColor.get() : v.getOptions().paintColor();

        ClassLoader cl = getClass().getClassLoader();
        String colorDirectory = colorToDirectory.get(c);
        String path = ImagePrefix + colorDirectory;

        bodyImg.setImage(new Image(cl.getResourceAsStream(path+"body@2x.png")));
        dfOpenImg.setImage(new Image(cl.getResourceAsStream(path+"left_front_open@2x.png")));
        dfClosedImg.setImage(new Image(cl.getResourceAsStream(path+"left_front_closed@2x.png")));
        drOpenImg.setImage(new Image(cl.getResourceAsStream(path+"left_rear_open@2x.png")));
        drClosedImg.setImage(new Image(cl.getResourceAsStream(path+"left_rear_closed@2x.png")));
        pfOpenImg.setImage(new Image(cl.getResourceAsStream(path+"right_front_open@2x.png")));
        prOpenImg.setImage(new Image(cl.getResourceAsStream(path+"right_rear_open@2x.png")));
        ftClosedImg.setImage(new Image(cl.getResourceAsStream(path+"frunk_closed@2x.png")));
        ftOpenImg.setImage(new Image(cl.getResourceAsStream(path+"frunk_open@2x.png")));
        rtOpenImg.setImage(new Image(cl.getResourceAsStream(path+"trunk_open@2x.png")));
        rtClosedImg.setImage(new Image(cl.getResourceAsStream(path+"trunk_closed@2x.png")));
        solidRoofImg.setImage(new Image(cl.getResourceAsStream(path+"roof@2x.png")));
    }
    
}
