/*
 * WakeSleepDialog.java  - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Aug 31, 2013
 */

package org.noroomattheinn.visibletesla.dialogs;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.stage.Stage;

/**
 * WakeSleepDialog
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */

public class WakeSleepDialog extends VTDialog.Controller {
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/

    private boolean letItSleep = true;
    private boolean dontAskAgain = false;
    
/*------------------------------------------------------------------------------
 *
 * Internal State - UI Components
 * 
 *----------------------------------------------------------------------------*/
    
    @FXML private CheckBox dontAskCheckbox;
    @FXML private Button sleepButton;
    @FXML private Button wakeButton;

/*------------------------------------------------------------------------------
 *
 * UI Action Handlers
 * 
 *----------------------------------------------------------------------------*/
    
    @FXML private void dontAskhandler(ActionEvent event) {
        dontAskAgain = ((CheckBox)(event.getSource())).isSelected();
    }

    @FXML private void wakeSleepHandler(ActionEvent event) {
        letItSleep = (event.getSource() == sleepButton);
        dialogStage.close();
    }
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public static WakeSleepDialog show(Stage stage) {
        WakeSleepDialog wsd = VTDialog.<WakeSleepDialog>load(
            WakeSleepDialog.class.getResource("WakeSleepDialog.fxml"),
            "Wake up your car?", stage);
        return wsd;
    }

    public boolean letItSleep() { return letItSleep; }
    public boolean dontAskAgain() { return dontAskAgain; }
    
}
