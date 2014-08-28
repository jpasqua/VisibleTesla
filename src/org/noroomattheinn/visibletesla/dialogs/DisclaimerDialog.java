/*
 * DisclaimerDialog.java  - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Aug 14, 2014
 */

package org.noroomattheinn.visibletesla.dialogs;

import javafx.scene.control.Dialogs;
import org.noroomattheinn.visibletesla.AppContext;


/**
 * DisclaimerDialog: Display a disclaimer to users if not already seen
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */


public class DisclaimerDialog {
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public static void show(AppContext appContext) {
        boolean disclaimer = appContext.persistentState.getBoolean(
                appContext.vehicle.getVIN()+"_Disclaimer", false);
        if (!disclaimer) {
            Dialogs.showInformationDialog(
                    appContext.stage,
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
        appContext.persistentState.putBoolean(
                appContext.vehicle.getVIN()+"_Disclaimer", true);                
    }
}
