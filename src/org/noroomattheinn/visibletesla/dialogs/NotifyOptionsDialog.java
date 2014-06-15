/*
 * NotifyOptionsDialog.java  - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Mar 02, 2014
 */

package org.noroomattheinn.visibletesla.dialogs;

import java.net.URL;
import java.util.Map;
import java.util.ResourceBundle;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;

/**
 * NotifyOptionsDialog
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */

public class NotifyOptionsDialog implements DialogUtils.DialogController {

/*------------------------------------------------------------------------------
 *
 * Constants and Enums
 * 
 *----------------------------------------------------------------------------*/

/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
        
    private Stage myStage;
    private String email;
    private String subject;
    private String message;
    private boolean cancelled;
    
/*------------------------------------------------------------------------------
 *
 * UI Elements
 * 
 *----------------------------------------------------------------------------*/
        
    @FXML private ResourceBundle resources;
    @FXML private URL location;

    @FXML private Button okButton;
    @FXML private Button cancelButton;
    @FXML private TextField emailField;
    @FXML private TextField subjectField;
    @FXML private TextArea messageField;
    @FXML private CheckBox useDefault;
    
/*------------------------------------------------------------------------------
 *
 * UI Initialization and Action Handlers
 * 
 *----------------------------------------------------------------------------*/
    
    @FXML void initialize() {
        cancelled = true;
        email = subject = message = null;
        useDefault.selectedProperty().addListener(new ChangeListener<Boolean>() {
            @Override public void changed(ObservableValue<? extends Boolean> ov, Boolean t, Boolean t1) {
                emailField.setDisable(t1);
                subjectField.setDisable(t1);
                messageField.setDisable(t1);
            }
        });
    }
    
    @FXML void buttonHandler(ActionEvent event) {
        Button b = (Button)event.getSource();
        if (b == okButton) {
            cancelled = false;
            if (useDefault.isSelected()) {
                email = subject = message = null;
            } else {
                email = emailField.getText().trim();
                subject = subjectField.getText().trim();
                message = messageField.getText();
            }
        } else if (b == cancelButton) {
            cancelled = true;
            email = subject = message = null;
        }
        myStage.close();
    }
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    
    public String getEmail() { return item(email); }
    public String getSubject() { return item(subject); }
    public String getMessage() { return item(message); }
    public boolean cancelled() { return cancelled; }
    public boolean useDefault() { return useDefault.isSelected(); }

/*------------------------------------------------------------------------------
 *
 * Methods overriden from DialogController
 * 
 *----------------------------------------------------------------------------*/

    @Override public void setStage(Stage stage) { this.myStage = stage; }
    @Override public void setProps(Map props) {
        String initEmail = (String)props.get("EMAIL");
        if (initEmail != null) {
            email = initEmail.trim();
            emailField.setText(email);
        }
        String initSubject = (String)props.get("SUBJECT");
        if (initSubject != null) {
            subject = initSubject.trim();
            subjectField.setText(subject);
        }
        String initMsg = (String)props.get("MESSAGE");
        if (initMsg != null) {
            message = initMsg;
            messageField.setText(message);
        }
        if (initEmail == null && initSubject == null && initMsg == null) {
            useDefault.setSelected(true);
        }
    }
    
    
/*------------------------------------------------------------------------------
 *
 * PRIVATE - Utility Methods
 * 
 *----------------------------------------------------------------------------*/

    private String item(String item) {
        if (item == null || item.isEmpty()) return null;
        return item;
    }
   
}
