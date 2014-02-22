/*
 * WakeSleepDialog.java  - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Aug 31, 2013
 */

package org.noroomattheinn.visibletesla.dialogs;

import org.noroomattheinn.visibletesla.dialogs.DialogUtils;
import java.net.URL;
import java.util.Map;
import java.util.ResourceBundle;
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

public class WakeSleepDialog implements DialogUtils.DialogController {
    private Stage myStage;
    private boolean letItSleep = true;
    private boolean dontAskAgain = false;
    private Map props;
    
    @FXML private ResourceBundle resources;
    @FXML private URL location;

    @FXML private CheckBox dontAskCheckbox;
    @FXML private Button sleepButton;
    @FXML private Button wakeButton;


    @FXML void dontAskhandler(ActionEvent event) {
        dontAskAgain = ((CheckBox)(event.getSource())).isSelected();
    }

    @FXML void wakeSleepHandler(ActionEvent event) {
        letItSleep = (event.getSource() == sleepButton);
        myStage.close();
    }

    @FXML
    void initialize() {
        assert dontAskCheckbox != null : "fx:id=\"dontAskCheckbox\" was not injected: check your FXML file 'WakeSleepDialog.fxml'.";
        assert sleepButton != null : "fx:id=\"sleepButton\" was not injected: check your FXML file 'WakeSleepDialog.fxml'.";
        assert wakeButton != null : "fx:id=\"wakeButton\" was not injected: check your FXML file 'WakeSleepDialog.fxml'.";
    }

    public boolean letItSleep() { return letItSleep; }
    public boolean dontAskAgain() { return dontAskAgain; }
    
    @Override public void setStage(Stage stage) { this.myStage = stage; }

    @Override public void setProps(Map props) { this.props = props; }
}
