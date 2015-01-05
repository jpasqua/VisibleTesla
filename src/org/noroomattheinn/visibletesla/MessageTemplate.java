/*
 * MessageTemplate.java - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: May 31, 2014
 */
package org.noroomattheinn.visibletesla;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.noroomattheinn.utils.GeoUtils;
import org.noroomattheinn.utils.Utils;
import org.noroomattheinn.visibletesla.vehicle.VTVehicle;

import static org.noroomattheinn.tesla.Tesla.logger;

/**
 * MessageTemplate
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class MessageTemplate {
    
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    // The overall message template is represented by a list of MsgComponents
    private List<MsgComponent> components;
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/

    public MessageTemplate(String format) {
        components = new ArrayList<>();
        if (format == null) {
            return;
        }
        parse(format);
    }

    public String getMessage(App app, VTVehicle v, Map<String,String> contextSpecific) {
        StringBuilder sb = new StringBuilder();
        for (MsgComponent mc : components) {
            sb.append(mc.asString(app, v, contextSpecific));
        }
        return sb.toString();
    }

/*------------------------------------------------------------------------------
 *
 * PRIVATE Methods that parse a template string
 * 
 *----------------------------------------------------------------------------*/
    
    private void parse(String input) {
        while (input != null) {
            input = next(input);
        }
    }
    
    private String next(String input) {
        if (input == null) return null;
        if (input.isEmpty()) return null;
        int length = input.length();
        for (int i = 0; i < length; i++) {
            if (input.charAt(i) == '{') {
                if (i+1 != length && input.charAt(i+1) == '{') {
                    // Matched {{
                    if (i != 0) {
                        components.add(new MsgComponent.StrComponent(input.substring(0, i)));
                        return input.substring(i);
                    } else {
                        // Search for matching }}
                        for (int j = i+2; j < length; j++) {
                            if (input.charAt(j) == '}') {
                                if (j+1 != length && input.charAt(j+1) == '}') {
                                    // Founding matching }}
                                    components.add(new MsgComponent.VarComponent(input.substring(i+2, j)));
                                    return input.substring(j+2);
                                }
                            }
                        }
                    }
                }
            }
        }
        components.add(new MsgComponent.StrComponent(input));
        return null;
    }
    
/*------------------------------------------------------------------------------
 *
 * PRIVATE class that implements an individual component of a message
 * 
 *----------------------------------------------------------------------------*/
    
    private static abstract class MsgComponent {
        abstract String asString(App app, VTVehicle v, Map<String,String> contextSpecific);

        // A String component is very simple - it's just a literal String
        static class StrComponent extends MsgComponent {
            public String string;
            
            StrComponent(String s) { this.string = s; }
            
            @Override String asString(App app, VTVehicle v, Map<String,String> cs) {
                return string;
            }
        }

        // A Variable component represents a formatted reading from the car
        static class VarComponent extends MsgComponent {
            public String varName;

            VarComponent(String v) { this.varName = v; }

            @Override String asString(
                    App app, VTVehicle v, Map<String,String> contextSpecific) {
                String val;
                switch (varName) {
                    case "SPEED":
                        val = String.format(
                                "%3.1f", v.inProperUnits(v.streamState.get().speed));
                        break;
                    case "SOC":
                        val = String.valueOf(v.streamState.get().soc);
                        break;
                    case "IDEAL":
                        val = String.format(
                                "%3.1f", v.inProperUnits(v.chargeState.get().idealRange));
                        break;
                    case "RATED":
                        val = String.format(
                                "%3.1f", v.inProperUnits(v.chargeState.get().range));
                        break;
                    case "ESTIMATED":
                        val = String.format(
                                "%3.1f", v.inProperUnits(v.chargeState.get().estimatedRange));
                        break;
                    case "CHARGE_STATE":
                        val = v.chargeState.get().chargingState.name();
                        break;
                    case "D_UNITS":
                        val = v.unitType() == Utils.UnitType.Imperial ? "mi" : "km";
                        break;
                    case "S_UNITS":
                        val = v.unitType() == Utils.UnitType.Imperial ? "mph" : "km/h";
                        break;
                    case "DATE":
                        val = String.format("%1$tY-%1$tm-%1$td", new Date());
                        break;
                    case "TIME":
                        val = String.format("%1$tH:%1$tM:%1$tS", new Date());
                        break;
                    case "LOC":
                    case "HT_LOC":
                        String lat = String.valueOf(v.streamState.get().estLat);
                        String lng = String.valueOf(v.streamState.get().estLng);
                        val = GeoUtils.getAddrForLatLong(lat, lng);
                        if (val == null || val.isEmpty()) {
                            val = String.format("(%s, %s)", lat, lng);
                        }
                        if (varName.equals("HT_LOC")) {
                            try {
                                val = String.format(
                                        "<a href='http://maps.google.com/maps?z=12&t=m&q=@%s,%s'>%s</a>",
                                        lat, lng, val);
                            } catch (Exception e) { // In case something goes wrong with the format
                                logger.severe(e.getMessage());
                            }
                        }
                        break;
                    case "I_STATE":
                        val = app.state.get().name();
                        break;
                    case "I_MODE":
                        val = app.mode.get().name();
                        break;
                    case "P_CURRENT":
                        val = String.valueOf(v.chargeState.get().chargerPilotCurrent);
                        break;
                    case "TIME_TO_FULL":
                        val = ChargeController.getDurationString(
                                v.chargeState.get().timeToFullCharge);
                        break;
                    case "C_RATE":
                        val = String.format(
                                "%.1f", v.inProperUnits(v.chargeState.get().chargeRate));
                        break;
                    case "C_AMP":
                        val = String.format("%.1f", v.chargeState.get().batteryCurrent);
                        break;
                    case "C_VLT":
                        val = String.valueOf(v.chargeState.get().chargerVoltage);
                        break;
                    case "C_PWR":
                        val = String.valueOf(v.chargeState.get().chargerPower);
                        break;
                    case "HT_SOC_G":
                        val = genSOCGauge(v);
                        break;
                    case "HT_ODO":
                        val = genODO(v);
                        break;
                    case "HT_RATED_G":
                        val = genGaugeWrapper(v, "Rated", v.chargeState.get().range);
                        break;
                    case "HT_IDEAL_G":
                        val = genGaugeWrapper(v, "Ideal", v.chargeState.get().idealRange);
                        break;
                    case "HT_ESTIMATED_G":
                        val = genGaugeWrapper(v, "Estimated", v.chargeState.get().estimatedRange);
                        break;
                    case "HT_SPEEDO":
                        val = genSpeedo(v);
                        break;
                    case "HT_CARVIEW":
                        val = genCarView(v);
                        break;
                    case "ODO":
                        val = String.format("%.1f", v.inProperUnits(v.streamState.get().odometer));
                        break;
                    default:
                        val = (contextSpecific == null) ? null : contextSpecific.get(varName);
                        if (val == null) {
                            val = "Unknown variable: " + varName;
                        }
                        break;
                }
                return val;
            }
            
            private static final String SpeedoTemplate = 
                "<canvas id='speedo' width='150' height='150'></canvas>" +
                "<script src='../scripts/CanvasUtils.js' type='text/javascript'></script>" +
                "<script src='../scripts/SpeedGauge.js' type='text/javascript'></script>" +
                "<script type='text/javascript'>" +
                "speedGauge(document.getElementById('speedo').getContext('2d'), 150, 150, %f, %f);" +
                "</script>";
            
            private String genSpeedo(VTVehicle v) {
                double speed = v.inProperUnits(v.streamState.get().speed);
                double power = v.inProperUnits(v.streamState.get().power);
                return String.format(SpeedoTemplate, speed, power);
            }
            
            private static final String CarViewFormat = 
                "<div class=\"carview\">\n"+
                "   <canvas id=\"cb\" width=\"540\" height=\"330\"> </canvas>\n" +
                "   <script src=\"../scripts/CarView.js\" type=\"text/javascript\"></script>\n" +
                "   <script>\n" +
                "       var ctx = document.getElementById(\"cb\").getContext(\"2d\");\n" +
                "       var carDetails = %s;\n"+
                "       var carState = %s;\n" +
                "       carView(ctx, carDetails, carState);\n" +
                "   </script>"+
                "</div>";
    
            private String genCarView(VTVehicle v) {
                return String.format(
                        CarViewFormat,  v.carDetailsAsJSON(), v.carStateAsJSON());
            }
            
            private static final String SOCGaugeTemplate = 
                "<canvas id='bg' width='140' height='50'></canvas>" +
		"<script src='../scripts/CanvasUtils.js' type='text/javascript'></script>" +
		"<script src='../scripts/BatteryGauge.js' type='text/javascript'></script>" +
                "<script type='text/javascript'>" +
		"batteryGauge(document.getElementById('bg').getContext('2d'), 100, 50, %d, %b);" +
                "</script>";
            
            private String genSOCGauge(VTVehicle v) {
                int soc = v.streamState.get().soc;
                boolean showPlug = false;
                switch (v.chargeState.get().chargingState) {
                    case Charging:
                    case Complete:
                        showPlug = true;
                        break;
                }
                return String.format(SOCGaugeTemplate, soc, showPlug);
            }
            
            private static String genODO(VTVehicle v) {
                String punc = v.unitType() == Utils.UnitType.Imperial ? "," : ".";
                StringBuilder sb = new StringBuilder();
                double odo = v.inProperUnits(v.streamState.get().odometer);
                double modulus = 100000;
                for (int i = 0; i < 6; i++) {
                    int digit = (int)(odo / modulus);
                    if (i == 3) {
                        sb.append("<span class='punc_box'>");
                        sb.append(punc);
                        sb.append("</span>");
                    }
                    sb.append("<span class='dark_box'>");
                    sb.append(digit);
                    sb.append("</span>");
                    odo = odo % modulus;
                    modulus /= 10;
                }
                int tenths = (int)(odo * 10);
                sb.append("<span class='light_box'>");
                sb.append(tenths);
                sb.append("</span>");
                return sb.toString();
            }

            private static String genGaugeWrapper(VTVehicle v, String label, double val) {
                return genGauge(
                        label, v.inProperUnits(val),
                        v.unitType() == Utils.UnitType.Imperial ? "Miles" : "Km",
                        val < 25);
            }
            
            private static String genGauge(
                    String label, double val, String units, boolean critical) {
                StringBuilder sb = new StringBuilder();
                sb.append("<div class='inset' style='width:140px'>");
                sb.append("<table border='0' width='100%' style='margin:0px;padding:0px'>");
                sb.append("<tr width='100%'> <td width='100%' colspan='3' align='center' class='gaugeLabel'>");
                sb.append(label);
                sb.append("</td> </tr> <tr> <td class='gaugeSymbol'>");
                if (critical) sb.append("&#x2757");
                sb.append("</td> <td class='gaugeReadout'>");
                sb.append(String.format("%.1f", val));
                sb.append("</td> <td class='gaugeUnits'>");
                sb.append(units);
                sb.append("</td></tr></table></div>");
                return sb.toString();
            }
        }
        
    }

}
