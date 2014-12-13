/*
 * NotifyOptionsDialog.java  - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Mar 02, 2014
 */

package org.noroomattheinn.visibletesla.dialogs;

import org.noroomattheinn.visibletesla.fxextensions.VTDialog;
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

public class NotifyOptionsDialog extends VTDialog.Controller {
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
        
    private String email;
    private String subject;
    private String message;
    private boolean cancelled;
    
/*------------------------------------------------------------------------------
 *
 * Internal State - UI Components
 * 
 *----------------------------------------------------------------------------*/
        
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
    
    @FXML private void initialize() {
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
    
    @FXML private void buttonHandler(ActionEvent event) {
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
        dialogStage.close();
    }
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public static NotifyOptionsDialog show(
            String title, Stage stage,
            String initEmail, String initSubject, String initMsg) {
        NotifyOptionsDialog nod = VTDialog.<NotifyOptionsDialog>load(
            NotifyOptionsDialog.class.getResource("NotifyOptionsDialog.fxml"),
            title, stage);
        nod.setInitialValues(initEmail, initSubject, initMsg);
        nod.show();
        return nod;
    }
    
    public String getEmail() { return item(email); }
    public String getSubject() { return item(subject); }
    public String getMessage() { return item(message); }
    public boolean cancelled() { return cancelled; }
    public boolean useDefault() { return useDefault.isSelected(); }

/*------------------------------------------------------------------------------
 *
 * Private initialization methods
 * 
 *----------------------------------------------------------------------------*/

    private void setInitialValues(String initEmail, String initSubject, String initMsg) {
        if (initEmail != null) {
            email = initEmail.trim();
            emailField.setText(email);
        }
        if (initSubject != null) {
            subject = initSubject.trim();
            subjectField.setText(subject);
        }
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
