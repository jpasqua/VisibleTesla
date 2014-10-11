/*
 * PasswordDialog.java  - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Sep 23, 2014
 */
package org.noroomattheinn.visibletesla.dialogs;

import javafx.geometry.Insets;
import javafx.scene.control.Dialogs;
import javafx.scene.control.Dialogs.DialogOptions;
import javafx.scene.control.Dialogs.DialogResponse;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

/**
 * PasswordDialog: Display a dialog that accepts a password
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class PasswordDialog {
    
/*==============================================================================
* -------                                                               -------
* -------              Public Interface To This Class                   ------- 
* -------                                                               -------
*============================================================================*/
    
    public static String[] getCredentials(
            Stage stage, String title, String masthead,
            boolean promptForUsername) {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(0, 10, 0, 10));
        int row = 0;
        final TextField username = new TextField();
        if (promptForUsername) {
            username.setPromptText("Username");
            grid.add(new Label("Username:"), 0, row);
            grid.add(username, 1, row);
            row++;
        }
        
        final PasswordField password = new PasswordField();
        password.setPromptText("Password");

        grid.add(new Label("Password:"), 0, row);
        grid.add(password, 1, row);

        DialogResponse resp = Dialogs.showCustomDialog(
                stage, grid, masthead, title, DialogOptions.OK_CANCEL, null);

        if (resp.equals(DialogResponse.CANCEL)) return null;
        String[] results = new String[2];
        results[0] = promptForUsername ? username.getText() : null;
        results[1] = password.getText();
        return results;
    }
    
}
