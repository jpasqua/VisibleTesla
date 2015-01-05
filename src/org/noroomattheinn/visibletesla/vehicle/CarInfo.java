/*
 * CarInfo.java - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Jul 27, 2014
 */
package org.noroomattheinn.visibletesla.vehicle;

import java.util.Map;
import org.noroomattheinn.tesla.ChargeState;
import org.noroomattheinn.tesla.Options;
import org.noroomattheinn.tesla.VehicleState;
import org.noroomattheinn.utils.Utils;

/**
 * CarInfo
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
class CarInfo {
    
/*------------------------------------------------------------------------------
 *
 * Constants and Enums
 * 
 *----------------------------------------------------------------------------*/
    
    private static final Map<Options.PaintColor,String> ColorMap =
            Utils.<Options.PaintColor,String>newHashMap(
                Options.PaintColor.PBCW, "white",
                Options.PaintColor.PBSB, "black",
                Options.PaintColor.PMAB, "brown",
                Options.PaintColor.PMMB, "blue",
                Options.PaintColor.PMSG, "green",
                Options.PaintColor.PMSS, "silver",
                Options.PaintColor.PMTG, "gray",
                Options.PaintColor.PPMR, "newred",
                Options.PaintColor.PPSR, "red",
                Options.PaintColor.PPSW, "pearl",
                Options.PaintColor.Unknown, "white"
            );
    private static final Map<Options.InteriorColor,String> SeatMap = 
            Utils.<Options.InteriorColor,String>newHashMap(
                Options.InteriorColor.Black, "black",
                Options.InteriorColor.Tan, "tan",
                Options.InteriorColor.Gray, "gray",
                Options.InteriorColor.White, "white"
            );
    private static final Map<Options.WheelType,String> WheelMap = 
            Utils.<Options.WheelType,String>newHashMap(
                Options.WheelType.WT1P, "silver19",
                Options.WheelType.WTX1, "silver19",
                Options.WheelType.WT19, "silver19",
                Options.WheelType.WT21, "silver21",
                Options.WheelType.WTSP, "gray21",
                Options.WheelType.WTSG, "gray21",
                Options.WheelType.WTAE, "aero",
                Options.WheelType.WTTB, "cyclone",
                Options.WheelType.WTTP, "cyclone",
                Options.WheelType.Unknown, "silver21"
            );
    private static final String DetailsFormat =
        "{   \"color\": \"%s\", " +
        "    \"seats\": \"%s\", " +
        "    \"wheels\": \"%s\", " +
        "    \"hasSpoiler\": %b, " +
        "    \"hasPano\": %b, " +
        "    \"model\": \"%s\" }";
    private static final String CarStateFormat = 
        "{  \"rf\": \"%s\", \"rr\": \"%s\", \"lf\": \"%s\", \"lr\": \"%s\"," +
        "   \"ft\": \"%s\", \"rt\": \"%s\", " +
        "   \"panoPct\": \"%d\", " +
        "   \"chargePort\": \"%s\", \"charging\": %b, " +
        "   \"locked\": %b }";
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public static String carDetailsAsJSON(VTVehicle v) {
        Options options = v.getVehicle().getOptions();
        VehicleState vs = v.vehicleState.get();
        return String.format(DetailsFormat,
                ColorMap.get(options.paintColor()),
                SeatMap.get(options.seatType().getColor()),
                WheelMap.get(v.wheelType()),
                vs.hasSpoiler, vs.hasPano,
                getModel(options));

    }
    
    public static String carStateAsJSON(VTVehicle v) {
        VehicleState vs = v.vehicleState.get();
        ChargeState cs = v.chargeState.get();
        return String.format(CarStateFormat,
                ooc(vs.isPFOpen), ooc(vs.isPROpen), ooc(vs.isDFOpen), ooc(vs.isDROpen),
                ooc(vs.isFTOpen), ooc(vs.isRTOpen), vs.panoPercent,
                ooc(cs.chargePortOpen), cs.chargingState == ChargeState.Status.Charging,
                vs.locked);
    }
    

/*------------------------------------------------------------------------------
 *
 * PRIVATE - Utility Methods
 * 
 *----------------------------------------------------------------------------*/
    
    private static String ooc(boolean openState) { return openState ? "open" : "closed"; }

    private static String getModel(Options options) {
        switch (options.model()) {
            case S60: return "s60";
            case S85: return "s85";
            case P85: return "p85";
            case P85Plus: return "p85+";
            case P85D: return "p85d";
        }
        return "unknown";
    }

}


