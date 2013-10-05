/*
 * LoginController.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Jul 22, 2013
 */

package org.noroomattheinn.visibletesla;

import java.util.concurrent.Callable;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import org.noroomattheinn.tesla.Result;
import org.noroomattheinn.tesla.Tesla;
import org.noroomattheinn.tesla.Vehicle;

/**
 * This controller allows the user to login and logout. The "logged-in" state
 * can be monitored by observing the loginCompleteProperty.
 * 
 * After a successful login, this controller also fetches the GUIState and
 * VehicleState and caches them in the appContext. Other components may use
 * these cached values but must understand that they are not updated - they
 * represent a snapshot of the values when the user logged in.
 * 
 * TO DO:
 * - If there are more than one vehicle associated with the logged-in user,
 *   allow the user to select the vehicle she's interested in.
 * 
 * 
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class LoginController extends BaseController {
    
/*------------------------------------------------------------------------------
 *
 * Observable State
 * 
 *----------------------------------------------------------------------------*/
    
    private BooleanProperty loginCompleteProperty = new SimpleBooleanProperty(false);
    BooleanProperty getLoginCompleteProperty() { return loginCompleteProperty; }
    
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/

    private Tesla tesla;
    
/*------------------------------------------------------------------------------
 *
 * UI Elements
 * 
 *----------------------------------------------------------------------------*/

    @FXML private Label loggedInName;
    @FXML private Button loginButton;
    @FXML private Button logoutButton;
    @FXML private PasswordField passwordField;
    @FXML private TextField usernameField;
    @FXML private ImageView loggedInImage;
    @FXML private Label loggedInStatus;
    @FXML private CheckBox rememberMe;
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    void attemptAutoLogin(Tesla t) {
        this.tesla = t;
        attemptLogin(null, null);
    }
    
/*------------------------------------------------------------------------------
 *
 *  UI Action Handlers
 * 
 *----------------------------------------------------------------------------*/
    
    @FXML void logoutAction(ActionEvent event) {
        loginCompleteProperty.set(false);
        usernameField.setText("");
        passwordField.setText("");
        reflectNewState();
    }

    @FXML void loginAction(ActionEvent event) {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        if (username.isEmpty()) {
            loggedInStatus.setText("Please enter a username");
            return;
        }
        attemptLogin(username, password);
    }

    // Controller-specific initialization
    protected void fxInitialize() {
        showAutoLoginUI();
    }
    
/*------------------------------------------------------------------------------
 *
 * Methods overridden from BaseController
 * 
 *----------------------------------------------------------------------------*/
    
    protected void refresh() { }
    
/*------------------------------------------------------------------------------
 *
 * Methods to Reflect the desired state of the UI
 * 
 *----------------------------------------------------------------------------*/
    
    private void showLoginUI(String prompt, String user, boolean loggedIn) {
        loggedInStatus.setText(prompt);
        loggedInName.setText(user);
        loggedInImage.setOpacity(loggedIn ? 1.0 : 0.25);
        
        loginButton.setDisable(loggedIn);
        loginButton.setDefaultButton(!loggedIn);
        
        logoutButton.setDisable(!loggedIn);
    }
    
    private void showManualLoginUI() {
        showLoginUI("Please enter your credentials", "", false);
        usernameField.requestFocus();
    }
    
    private void showLoginSucceeded() { showLoginUI("Logged in as:", tesla.getUsername(), true); }
    
    private void showAutoLoginUI() {
        showLoginUI("Attempting Automatic Login", "", false);
        logoutButton.setDisable(true);
    }
    
/*------------------------------------------------------------------------------
 *
 * Private Utility Methods and Classes
 * 
 *----------------------------------------------------------------------------*/
    
    private void attemptLogin(String username, String password) {
        issueCommand(new AttemptLogin(username, password), AfterCommand.Reflect);
    }

    @Override protected void reflectNewState() {
        if (loginCompleteProperty.get()) showLoginSucceeded();
        else showManualLoginUI();
    }

    // Nothing to do here, there is no vehicle established yet
    @Override protected void prepForVehicle(Vehicle v) { }

    private class AttemptLogin implements Callable<Result> {
        String username, password;
        
        AttemptLogin(String username, String password) {
            this.username = username;
            this.password = password;
        }
        
        @Override public Result call() {
            boolean loggedIn;
            
            if (username == null)   // Try auto login
                loggedIn = tesla.connect();
            else    // Login with the specified username and password
                loggedIn = tesla.connect(username, password, rememberMe.isSelected());
            
            loginCompleteProperty.set(loggedIn);
            return loggedIn ? Result.Succeeded : Result.Failed;
        }
        
    }
    
}
