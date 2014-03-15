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
    @FXML private CheckBox useDefault;
    
/*------------------------------------------------------------------------------
 *
 * UI Initialization and Action Handlers
 * 
 *----------------------------------------------------------------------------*/
    
    @FXML void initialize() {
        cancelled = true;
        email = null;
        subject = null;
        useDefault.selectedProperty().addListener(new ChangeListener<Boolean>() {
            @Override public void changed(ObservableValue<? extends Boolean> ov, Boolean t, Boolean t1) {
                emailField.setDisable(t1);
                subjectField.setDisable(t1);
            }
        });
    }
    
    @FXML void buttonHandler(ActionEvent event) {
        Button b = (Button)event.getSource();
        if (b == okButton) {
            cancelled = false;
            if (useDefault.isSelected()) {
                email = null;
                subject = null;
            } else {
                email = emailField.getText().trim();
                subject = subjectField.getText().trim();
            }
        } else if (b == cancelButton) {
            cancelled = true;
            email = null;
            subject = null;
        }
        myStage.close();
    }
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    
    public String getEmail() {
        if (email == null || email.isEmpty()) return null;
        return email;
    }
    public String getSubject() {
        if (subject == null || subject.isEmpty()) return null;
        return subject;
    }
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
        if (initEmail == null && initSubject == null) {
            useDefault.setSelected(true);
        }
    }
    
    
/*------------------------------------------------------------------------------
 *
 * PRIVATE - Utility Methods
 * 
 *----------------------------------------------------------------------------*/
        
}
