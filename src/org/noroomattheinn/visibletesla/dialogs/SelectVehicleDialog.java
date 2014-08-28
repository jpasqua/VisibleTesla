/*
 * SelectVehicleDialog.java  - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Aug 14, 2014
 */

package org.noroomattheinn.visibletesla.dialogs;

import java.util.ArrayList;
import java.util.List;
import javafx.scene.control.Dialogs;
import org.apache.commons.lang3.StringUtils;
import org.noroomattheinn.tesla.Vehicle;
import org.noroomattheinn.visibletesla.AppContext;


/**
 * SelectVehicle: Choose a vehicle from a list of available vehicles
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */


public class SelectVehicleDialog {
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public static Vehicle select(AppContext appContext) {
        int selectedVehicleIndex = 0;
        List<Vehicle> vehicleList = appContext.tesla.getVehicles();
        if (vehicleList.size() != 1) {
            // Ask the  user to select a vehicle
            List<String> cars = new ArrayList<>();
            for (Vehicle v : vehicleList) {
                StringBuilder descriptor = new StringBuilder();
                descriptor.append(StringUtils.right(v.getVIN(), 6));
                descriptor.append(": ");
                descriptor.append(v.getOptions().paintColor());
                descriptor.append(" ");
                descriptor.append(v.getOptions().batteryType());
                cars.add(descriptor.toString());
            }
            String selection = Dialogs.showInputDialog(
                    appContext.stage,
                    "Vehicle: ",
                    "You lucky devil, you've got more than 1 Tesla!",
                    "Select a vehicle", cars.get(0), cars);
            selectedVehicleIndex = cars.indexOf(selection);
            if (selectedVehicleIndex == -1) { selectedVehicleIndex = 0; }
        }
        return vehicleList.get(selectedVehicleIndex);
    }

}
