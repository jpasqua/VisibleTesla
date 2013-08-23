/*
 * HVACController.java - Copyright(c) 2013  All Rights Reserved, Joe Pasqua
 * Created: Jul 22, 2013
 */

package org.noroomattheinn.visibletesla;

import org.noroomattheinn.tesla.Vehicle;

public class GraphController extends BaseController {
    
    //
    // Action Handlers
    //
    
    
    //
    // Overriden methods from BaseController
    //
    
    protected void prepForVehicle(Vehicle v) {

    }

    protected void refresh() {
        //issueCommand(new GetAnyState(hvacState), AfterCommand.Reflect);
    }

    protected void reflectNewState() {
    }

    
    // Controller-specific initialization
    protected void doInitialize() {
        refreshButton.setDisable(true);
        refreshButton.setVisible(false);
    }    

}
