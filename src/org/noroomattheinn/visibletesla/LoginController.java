/*
 * LoginController.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Jul 22, 2013
 */

package org.noroomattheinn.visibletesla;

import java.util.concurrent.Callable;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialogs;
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
 * Constants and Enums
 * 
 *----------------------------------------------------------------------------*/
    public static final String RememberMePrefKey = "APP_REMEMBER_ME";
    
    
/*------------------------------------------------------------------------------
 *
 * Observable State
 * 
 *----------------------------------------------------------------------------*/
    
    private final BooleanProperty loginCompleteProperty = new SimpleBooleanProperty(false);
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
        Boolean rememberPref = appContext.persistentState.getBoolean(RememberMePrefKey, false);
        rememberMe.setSelected(rememberPref);

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
    
    @FXML void rememberMeHandler(ActionEvent event) {
        appContext.persistentState.putBoolean(RememberMePrefKey, rememberMe.isSelected());
        if (!rememberMe.isSelected())
            tesla.clearCookies();
    }

/*------------------------------------------------------------------------------
 *
 * Methods overridden from BaseController
 * 
 *----------------------------------------------------------------------------*/

    @Override protected void fxInitialize() { showAutoLoginUI(); }
    
    @Override protected void refresh() { }

    @Override protected void prepForVehicle(Vehicle v) { }

    @Override protected void reflectNewState() {
        if (loginCompleteProperty.get()) showLoginSucceeded();
        else { showManualLoginUI(); }
    }

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
            else {   // Login with the specified username and password
                loggedIn = tesla.connect(username, password, rememberMe.isSelected());
                if (!loggedIn) {
                    Platform.runLater(new Runnable() {
                        @Override public void run() {
                            Dialogs.showErrorDialog(
                                    appContext.stage,
                                    "Remember to use your email address as your username",
                                    "Login failed - Please check your credentials",
                                    "Problem logging in");
                        }
                    });
                }
            }
            
            loginCompleteProperty.set(loggedIn);
            return loggedIn ? Result.Succeeded : Result.Failed;
        }
        
    }
    
}
