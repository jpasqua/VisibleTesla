/*
 * OverviewController.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Jul 22, 2013
 */

package org.noroomattheinn.visibletesla;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
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
import org.noroomattheinn.tesla.ChargeState;
import org.noroomattheinn.tesla.Options;
import org.noroomattheinn.tesla.Result;
import org.noroomattheinn.tesla.StreamState;
import org.noroomattheinn.tesla.Vehicle;
import org.noroomattheinn.tesla.Vehicle.PanoCommand;
import org.noroomattheinn.tesla.VehicleState;
import org.noroomattheinn.utils.Utils;


public class OverviewController extends BaseController {
/*------------------------------------------------------------------------------
 *
 * Constants and Enums
 * 
 *----------------------------------------------------------------------------*/
    
    private static final String ToggleChoiceKey = "DISP_VIN";
    private enum ToggleDisplayChoice { VIN, SW, FW };
    private static final int nToggleChoices = ToggleDisplayChoice.values().length;
    
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private ToggleDisplayChoice toggleChoice;
    
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
    
    // Wheels
    @FXML private ImageView silver21Front, silver21Rear;
    @FXML private ImageView darkRimFront, darkRimRear;
    @FXML private ImageView nineteenRimFront, nineteenRimRear;
    @FXML private ImageView aeroFront, aeroRear;
    @FXML private ImageView cycloneFront, cycloneRear;
    @FXML private ImageView darkCycloneFront, darkCycloneRear;
    private Map<Options.WheelType,Options.WheelType> wheelEquivs = new HashMap<>();  
    private Map<Options.WheelType,ImageView[]> wheelImages = new HashMap<>();  
    
    @FXML private ImageView seatsTanImg, seatsGrayImg;

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
    @FXML private Label shiftStateLabel;
    @FXML private Label rangeLabel;
    @FXML private Label odometerLabel;
    @FXML private Button vinButton;
    
    // Emblem Images
    @FXML private ImageView s60Img, s85Img, p85Img, p85pImg, p85dImg, s85dImg;
    
    //
    // Controls
    //
    @FXML private Button lockButton;
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
                Result r = vtVehicle.getVehicle().setLockState(source == lockButton);
                updateStateLater(Vehicle.StateType.Vehicle, 3 * 1000);
                return r;
            } }, (source == lockButton) ? "Lock" : "Unlock");
    }

    @FXML void panoButtonHandler(ActionEvent event) {
        Button source = (Button)event.getSource();
        final PanoCommand cmd = 
            source == ventPanoButton ? PanoCommand.vent :
                ((source == openPanoButton) ? PanoCommand.open : PanoCommand.close);
        issueCommand(new Callable<Result>() {
            @Override public Result call() {
                Result r = vtVehicle.getVehicle().setPano(cmd);
                updateStateLater(Vehicle.StateType.Vehicle, 5 * 1000);
                return r;
            } }, "Move Pano");
    }

    @FXML void detailsButtonHandler(ActionEvent event) {
        AnchorPane pane = new AnchorPane();
        VehicleState car = vtVehicle.vehicleState.get();
        String info = vtVehicle.getVehicle().toString() +
                "\nFirmware Version: " + car.version +
                "\nRemote Start Enabled: " + vtVehicle.getVehicle().remoteStartEnabled() +
                "\nCalendar Enabled: " + vtVehicle.getVehicle().calendarEnabled() +
                "\nNotifications Enabled: " + vtVehicle.getVehicle().notificationsEnabled() +
                "\n--------------------------------------------" +
                "\nLow level information: " + vtVehicle.getVehicle().getUnderlyingValues() +
                "\nVehicle UUID: " + vtVehicle.getVehicle().getUUID() +
                "\nApp UUID: " + app.getAppID() +
                "\n";

        TextArea t = new TextArea(info);
        pane.getChildren().add(t);
        Dialogs.showCustomDialog(
            app.stage, pane, "Detailed Vehicle Description", "Details", DialogOptions.OK, null);
    }
    
/*------------------------------------------------------------------------------
 *
 * Methods overridden from BaseController
 * 
 *----------------------------------------------------------------------------*/
    
    @Override protected void fxInitialize() {
        odometerLabel.setVisible(true);
        
        wheelImages.put(Options.WheelType.WT19, new ImageView[] {nineteenRimFront, nineteenRimRear});
        wheelEquivs.put(Options.WheelType.WT1P, Options.WheelType.WT19);
        wheelEquivs.put(Options.WheelType.WTX1, Options.WheelType.WT19);

        wheelImages.put(Options.WheelType.WTAE, new ImageView[] {aeroFront, aeroRear});
        wheelEquivs.put(Options.WheelType.WTAP, Options.WheelType.WTAE);
        
        wheelImages.put(Options.WheelType.WTTB, new ImageView[] {cycloneFront, cycloneRear});
        wheelEquivs.put(Options.WheelType.WTTP, Options.WheelType.WTTB);
        
        wheelImages.put(Options.WheelType.WTTG, new ImageView[] {darkCycloneFront, darkCycloneRear});
        wheelEquivs.put(Options.WheelType.WTGP, Options.WheelType.WTTG);
        
        wheelImages.put(Options.WheelType.WT21, new ImageView[] {silver21Front, silver21Rear});
        wheelEquivs.put(Options.WheelType.WT2E, Options.WheelType.WT21);
        wheelEquivs.put(Options.WheelType.WTSS, Options.WheelType.WT21);
        
        wheelImages.put(Options.WheelType.WTSP, new ImageView[] {darkRimFront, darkRimRear});
        wheelEquivs.put(Options.WheelType.WTSE, Options.WheelType.WTSP);
        wheelEquivs.put(Options.WheelType.WTSG, Options.WheelType.WTSP);
    }

    /**
     * Refresh the state either because the user requested it or because the 
     * auto-refresh interval has passed. We always update the car and
     * charge. Getting the odometer reading can be more burdensome because
     * it has to be done through the streaming API. We only do that every 3rd
     * time refresh is invoked, or if the user pressed the refresh button.
     * This keeps down our request rate to the tesla servers.
     * 
     */
    @Override protected void refresh() {
        updateState(Vehicle.StateType.Vehicle);
        updateState(Vehicle.StateType.Charge);
    }
    
    @Override protected void initializeState() {
        final Vehicle v = vtVehicle.getVehicle();
        getAppropriateImages(v);
        toggleChoice = this.storedToggleChoice();
        prefs.overrides.color.addListener(new ChangeListener<String>() {
            @Override public void changed(
                    ObservableValue<? extends String> ov, String t, String t1) {
                getAppropriateImages(v);
            }
        });
        prefs.overrides.doColor.addListener(new ChangeListener<Boolean>() {
            @Override public void changed(
                    ObservableValue<? extends Boolean> ov, Boolean t, Boolean t1) {
                getAppropriateImages(v);
            }
        });

        vtVehicle.vehicleState.addTracker(new Runnable() {
            @Override public void run() {
                Platform.runLater(new Runnable() {
                    @Override public void run() { updateVehicleState(); }
                });
            }
        });
        vtVehicle.chargeState.addTracker(new Runnable() {
            @Override public void run() {
                Platform.runLater(new Runnable() {
                    @Override public void run() { updateChargePort(); updateRange(); }
                });
            }
        });
        vtVehicle.streamState.addTracker(new Runnable() {
            @Override public void run() {
                Platform.runLater(new Runnable() {
                    @Override public void run() {
                        updateOdometer();
                        updateShiftState();
                    }
                });
            }
        });
        
        updateOdometer();   // Show at least an old reading to start
        vtData.produceStream(false);   // Update it at some point

        updateWheelView();  // Make sure we display the right wheels from the get-go
        updateRoofView();   // Make sure we display the right roof from the get-go
        reflectVINOrFirmware();
        vinButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                cycleToggleChoice();
                reflectVINOrFirmware();
            }
        });
    }
    
    @Override protected void activateTab() { }

/*------------------------------------------------------------------------------
 *
 * Methods to Reflect the overall state of the vehicle
 * 
 *----------------------------------------------------------------------------*/
    
    private void updateVehicleState() {
        updateWheelView();
        updateRoofView();
        updateDoorView();
        updateOdometer();
        updateEmblem();
        updateSeats();
        updateRange();
        updateShiftState();
    }
    
    private void updateRange() {
        ChargeState cs = vtVehicle.chargeState.get();
        double range = 0;
        String rangeType = prefs.overviewRange.get();
        switch (rangeType) {
            case "Estimated": range = cs.estimatedRange; break;
            case "Ideal": range = cs.idealRange; break;
            case "Rated": range = cs.range; break;
        }
        range = vtVehicle.inProperUnits(range);
        String units = vtVehicle.unitType() == Utils.UnitType.Imperial ? "mi" : "km";
        rangeLabel.setText(String.format("%s Range: %3.1f %s", rangeType, range, units));
    }
    
    private void updateShiftState() {
        StreamState snapshot = vtVehicle.streamState.get();
        if (snapshot == null) return;
        shiftStateLabel.setText(snapshot.shiftState());
    }
    
    private void updateDoorView() {
        VehicleState car = vtVehicle.vehicleState.get();
        boolean rtOpen = car.isRTOpen;
        
        // Show the open/closed state of the doors and trunks
        setOptionState(car.isFTOpen, ftOpenImg, ftClosedImg);
        setOptionState(rtOpen, rtOpenImg, rtClosedImg);
        setOptionState(car.isDFOpen, dfOpenImg, dfClosedImg);
        setOptionState(car.isPFOpen, pfOpenImg, null);
        setOptionState(car.isDROpen, drOpenImg, drClosedImg);
        setOptionState(car.isPROpen, prOpenImg, null);
        setOptionState(car.locked, lockedImg, unlockedImg);
        
        spoilerOpenImg.setVisible(false); spoilerClosedImg.setVisible(false);
        if (vtVehicle.vehicleState.get().hasSpoiler) {
            setOptionState(rtOpen, spoilerOpenImg, spoilerClosedImg);
        }        
    }
    
    private void updateRoofView() {
        Options.RoofType type = vtVehicle.roofType();
        
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
        VehicleState car = vtVehicle.vehicleState.get();
        int pct = car.panoPercent;
        
        if (pct == 0) panoClosedImg.setVisible(true);
        else if (pct > 0 && pct < 90) panoVentImg.setVisible(true);
        else panoOpenImg.setVisible(true);
        panoPercent.setText(String.valueOf(pct) + " %");
    }
    
    private void updateWheelView() {
        updateImages(vtVehicle.wheelType(), wheelImages, wheelEquivs);
    }
    
    private void updateChargePort() {
        ChargeState charge = vtVehicle.chargeState.get();
        boolean connected = charge.connectedToCharger();
        
        boolean chargePortDoorOpen = (charge.chargePortOpen || connected);
        setOptionState(chargePortDoorOpen, portOpenImg, portClosedImg);
        chargeCableImg.setVisible(connected);
        greenGlowImage.setVisible(charge.chargingState == ChargeState.Status.Charging);
    }
    
    private void updateSeats() {
        seatsGrayImg.setVisible(false);
        seatsTanImg.setVisible(false);
        switch (vtVehicle.getVehicle().getOptions().seatType().getColor()) {
            case Gray:
            case White:
                seatsGrayImg.setVisible(true);
                break;
            case Tan:
                seatsTanImg.setVisible(true);
                break;
            case Black: // Do nothing, the base image is black
            default:
                break;
        }
    }
    
    private void updateEmblem() {
        s60Img.setVisible(false);
        s85Img.setVisible(false);
        p85Img.setVisible(false);
        p85pImg.setVisible(false);
        p85dImg.setVisible(false);
        s85dImg.setVisible(false);
        switch (vtVehicle.model()) {
            case S60: s60Img.setVisible(true); break;
            case S85: s85Img.setVisible(true); break;
            case P85: p85Img.setVisible(true); break;
            case P85Plus: p85pImg.setVisible(true); break;
            case P85D: p85dImg.setVisible(true); break;
            case S85D: s85dImg.setVisible(true); break;
            default: s85Img.setVisible(true); break;
        }
    }
    
    private void updateOdometer() {
        double odometerReading;
        if (vtVehicle.streamState.get().valid) {
            odometerReading = vtVehicle.streamState.get().odometer;
            prefs.storage().putDouble(vinKey("odometer"), odometerReading);
        } else {
            odometerReading = prefs.storage().getDouble(vinKey("odometer"), 0);
        }
                
        
        boolean useMiles = vtVehicle.unitType() == Utils.UnitType.Imperial;
        String units = useMiles ? "mi" : "km";
        odometerReading *= useMiles ? 1.0 : Utils.KilometersPerMile;
        odometerLabel.setText(String.format("Odometer: %.1f %s", odometerReading, units));
    }
    
    private void reflectVINOrFirmware() {
        VehicleState car = vtVehicle.vehicleState.get();
        String text;
        switch (toggleChoice) {
            case SW:
                text = "v" + Firmware.getSoftwareVersion(car.version);
                break;
            case FW:
                text = "FW: " + car.version;
                break;
            case VIN:
            default:
                text = "VIN " + StringUtils.right(vtVehicle.getVehicle().getVIN(), 6);
                break;
        }
        vinButton.setText(text);
    }
    
    private ToggleDisplayChoice storedToggleChoice() {
        String currentAsString = prefs.storage().get("DISP_VIN", ToggleDisplayChoice.VIN.name());
        return ToggleDisplayChoice.valueOf(currentAsString);
    }
    
    private void cycleToggleChoice() {
        int nextIndex = (toggleChoice.ordinal()+1) % nToggleChoices;
        toggleChoice = ToggleDisplayChoice.values()[nextIndex];
        prefs.storage().put(ToggleChoiceKey, toggleChoice.name());
    }
    
/*------------------------------------------------------------------------------
 *
 * State and Methods for locating the right images based on vehicle parameters
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
        colorToDirectory.put(Options.PaintColor.PMNG, "COLOR_steelgrey/");
        colorToDirectory.put(Options.PaintColor.Unknown, "COLOR_white/");
    }

    // Where the images are stored relative to the classpath
    private static final String ImagePrefix = "org/noroomattheinn/TeslaResources/";

    // Replace the images that were selected by default with images for the actual color
    private void getAppropriateImages(Vehicle v) {
        Options.PaintColor c = vtVehicle.paintColor();

        ClassLoader cl = getClass().getClassLoader();
        String colorDirectory = colorToDirectory.get(c);
        String path = ImagePrefix + colorDirectory;

        if (v.getOptions().driveSide() == Options.DriveSide.DRLH)
            bodyImg.setImage(new Image(cl.getResourceAsStream(path+"body@2x.png")));
        else
            bodyImg.setImage(new Image(cl.getResourceAsStream(path+"body_RHD@2x.png")));
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
