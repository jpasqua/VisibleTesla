/*
 * OverviewController.java - Copyright(c) 2013  All Rights Reserved, Joe Pasqua
 * Created: Jul 22, 2013
 */

package org.noroomattheinn.visibletesla;

import java.util.HashMap;
import java.util.Map;
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
import org.noroomattheinn.tesla.ActionController;
import org.noroomattheinn.tesla.ChargeState;
import org.noroomattheinn.tesla.DoorController;
import org.noroomattheinn.tesla.DoorController.PanoCommand;
import org.noroomattheinn.tesla.VehicleState;
import org.noroomattheinn.tesla.GUIState;
import org.noroomattheinn.tesla.Options;
import org.noroomattheinn.tesla.Options.WheelType;
import org.noroomattheinn.tesla.Result;
import org.noroomattheinn.tesla.StreamingState;
import org.noroomattheinn.tesla.Vehicle;


public class OverviewController extends BaseController {
    private static final double KilometersPerMile = 1.60934;
    
    // The Tesla State and Controller objects
    private VehicleState vehicleState;      // The primary door state
    private DoorController doorController;  // For primary door controller
    private ActionController actions;       // For honk, flash, wake commands
    private StreamingState streamingState;  // For odometer reading
    private ChargeState chargeState;        // For chargePortDoor status
    
    private Callback wakeupCB, flashCB, honkCB;
    
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
    @FXML private Label odometerLabel;
    
    //
    // Controls
    //
    @FXML private Button honkButton, flashButton, wakeupButton;
    @FXML private Button lockButton, unlockButton;
    @FXML private Button closePanoButton, ventPanoButton, openPanoButton;
            
    // Controller-specific initialization
    protected void doInitialize() {
        odometerLabel.setVisible(true);
        honkCB = new Callback() { public Result execute() { return actions.honk(); } };
        flashCB = new Callback() { public Result execute() { return actions.flashLights(); } };
        wakeupCB = new Callback() { public Result execute() { return actions.wakeUp(); } };
    }

    /**
     * This method would normally just return a VehicleState object, but it actually
     * requires two additional objects to display all of the information in this
     * tab. We also need a ChargeState and a StreamingState. We get these by issuing
     * explicit background commands to read them.
     * @return A new VehicleState object
     */
    protected void refresh() {
        issueCommand(new GetAnyState(streamingState), AfterCommand.Reflect);
        issueCommand(new GetAnyState(chargeState), AfterCommand.Reflect);
        issueCommand(new GetAnyState(vehicleState), AfterCommand.Reflect);
    }
    
    @Override protected void prepForVehicle(Vehicle v) {
        if (actions == null || v != vehicle) {
            actions = new ActionController(v);
            doorController = new DoorController(v);
            getAppropriateImages(v);
            // Handle background updates to streamingState and ChargeState
            vehicleState = new VehicleState(v);
            streamingState = new StreamingState(v);
            chargeState = new ChargeState(v);
        }
    }
    
    @Override protected void reflectNewState() {
        if (vehicleState.lastRefreshTime() == 0)    // Data's not ready yet
            return;
        
        updateWheelView();
        updateRoofView();
        updateDoorView();
        updateOdometer();
        updateChargePort();
    }
    
    public enum RoofState {Open, Closed, Vent, Solid};
    

    //
    // The following methods issue commands using the DoorController or
    // ActionController objects. If the command may change the state of the
    // vehicle, a refresh will be invoked to update the display.
    //
    
    @FXML void lockButtonHandler(ActionEvent event) {
        final Button source = (Button)event.getSource();
        issueCommand(new Callback() {
            public Result execute() {
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
        issueCommand(new Callback() {
            public Result execute() { return doorController.setPano(cmd); } },
            AfterCommand.Refresh);
    }

    @FXML void detailsButtonHandler(ActionEvent event) {
        AnchorPane pane = new AnchorPane();
        String info = vehicle.toString() + "\nFirmware Version: " + vehicleState.version();
        TextArea t = new TextArea(info);
        pane.getChildren().add(t);
        Dialogs.showCustomDialog(
            stage, pane, "Detailed Vehicle Description", "Details", DialogOptions.OK, null);
    }
    
    //
    // The following methods are responsible for updating the displayed image
    // to reflect the elements of the vehicleState object
    //
    
    private void updateDoorView() {
        boolean rtOpen = vehicleState.isRTOpen();
        boolean hasSpoiler = vehicleState.hasSpoiler();
        
        // Show the open/closed state of the doors and trunks
        setOptionState(vehicleState.isFTOpen(), ftOpenImg, ftClosedImg);
        setOptionState(rtOpen, rtOpenImg, rtClosedImg);
        setOptionState(vehicleState.isDFOpen(), dfOpenImg, dfClosedImg);
        setOptionState(vehicleState.isPFOpen(), pfOpenImg, null);
        setOptionState(vehicleState.isDROpen(), drOpenImg, drClosedImg);
        setOptionState(vehicleState.isPROpen(), prOpenImg, null);
        setOptionState(vehicleState.locked(), lockedImg, unlockedImg);
        if (hasSpoiler)
            setOptionState(rtOpen, spoilerOpenImg, spoilerClosedImg);
        
    }
    
    private void updateRoofView() {
        Options.RoofType type = vehicle.getOptions().roofType();
        if (simulatedRoof != null) type = simulatedRoof;
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
        
        if (hasPano) {
            int pct = vehicleState.panoPercent();
            if (pct == 0) panoClosedImg.setVisible(true);
            else if (pct > 0 && pct < 90) panoVentImg.setVisible(true);
            else panoOpenImg.setVisible(true);
            panoPercent.setText(String.valueOf(pct) + " %");
        } else {
            if (type == Options.RoofType.RFBC) solidRoofImg.setVisible(true);
            else blackRoofImg.setVisible(true);
        }
    }
    
    private void updateWheelView() {
        WheelType wt = (simulatedWheels == null) ? vehicle.getOptions().wheelType() : simulatedWheels;
        
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
        if (chargeState.lastRefreshTime() == 0) return; // No data available yet...
        
        int pilotCurrent = chargeState.chargerPilotCurrent();
        boolean chargePortDoorOpen = chargeState.chargePortOpen();
        setOptionState(chargePortDoorOpen, portOpenImg, portClosedImg);
        chargeCableImg.setVisible(pilotCurrent > 0);
        greenGlowImage.setVisible(chargeState.chargingState() == ChargeState.State.Charging);
    }
    
    private void updateOdometer() {
        if (streamingState.lastRefreshTime() == 0)
            return;  // Data isn't ready yet

        double odometerReading = streamingState.odometer();
        if (odometerReading == 0) return;   // The reading isn't ready yet 
        GUIState gs = vehicle.getLastKnownGUIState();
        boolean useMiles = gs.distanceUnits().equalsIgnoreCase("mi/hr");
        String units = useMiles ? "mi" : "km";
        odometerReading *= useMiles ? 1.0 : KilometersPerMile;
        odometerLabel.setText(String.format("Odometer: %.1f %s", odometerReading, units));
    }
    
    

    //
    // The following methods and data implement the mechanism that chooses
    // the images corresponding to the vehicle's actual color
    //
    
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
        Options.PaintColor c = simulatedColor != null ? simulatedColor : v.getOptions().paintColor();

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
    
    //
    // Methods and data to handle simulated color, rims, etc.
    //
    private Options.PaintColor simulatedColor = null;
    void setSimulatedColor(Options.PaintColor color) {
        simulatedColor = color;
        getAppropriateImages(vehicle);
    }
    
    private Options.WheelType simulatedWheels = null;
    void setSimulatedWheels(Options.WheelType wt) { simulatedWheels = wt; }
    
    private Options.RoofType simulatedRoof = null;
    void setSimulatedRoof(Options.RoofType rt) { simulatedRoof = rt; }
    
}
