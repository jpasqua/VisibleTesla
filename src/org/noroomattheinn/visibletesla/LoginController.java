/*
 * LoginController.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Jul 22, 2013
 */

package org.noroomattheinn.visibletesla;

import java.util.concurrent.Callable;
import javafx.application.Platform;
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
import org.noroomattheinn.visibletesla.fxextensions.TrackedObject;

/**
 * This controller allows the user to login and logout. The "logged-in" state
 * can be monitored by observing the loginCompleteProperty.
 * 
 * After a successful login, this controller also fetches the GUIState and
 * VehicleState and caches them in the ac. Other components may use
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
    public static final String AuthTokenKey = "APP_AUTH_TOKEN";
    public static final String UsernameKey = "APP_USERNAME";
    
/*------------------------------------------------------------------------------
 *
 * Observable State
 * 
 *----------------------------------------------------------------------------*/
    
    public TrackedObject<Boolean> loggedIn = new TrackedObject<>(false);
    
/*------------------------------------------------------------------------------
 *
 * UI Elements
 * 
 *----------------------------------------------------------------------------*/

    @FXML private Label loggedInName;
    @FXML private Button loginButton;
    @FXML private PasswordField passwordField;
    @FXML private TextField usernameField;
    @FXML private ImageView loggedInImage;
    @FXML private Label loggedInStatus;
    @FXML private CheckBox rememberMe;
        
/*------------------------------------------------------------------------------
 *
 *  UI Action Handlers
 * 
 *----------------------------------------------------------------------------*/
    
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
        Prefs.store().putBoolean(RememberMePrefKey, rememberMe.isSelected());
        if (!rememberMe.isSelected()) { Prefs.store().remove(AuthTokenKey); }
    }

/*------------------------------------------------------------------------------
 *
 * Methods overridden from BaseController
 * 
 *----------------------------------------------------------------------------*/

    @Override protected void fxInitialize() {
        loggedIn.addTracker(true, 
                new Runnable() { @Override public void run() { reflectLoginState(); } });        
    }
    
    @Override protected void refresh() { }
    
    @Override protected void initializeState() {
        Boolean rememberPref = Prefs.store().getBoolean(RememberMePrefKey, false);
        rememberMe.setSelected(rememberPref);

        String username = rememberPref ? Prefs.store().get(UsernameKey, null) : null;
        if (username != null) usernameField.setText(username);
    }
    
    @Override protected void activateTab() {
        if (loggedIn.get()) return;
        String username = usernameField.getText().trim();
        if (username.isEmpty()) { loggedIn.set(false); }
        else {
            showAutoLoginUI();
            attemptLogin(username, null);
        }
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
    }
    
    private void reflectLoginState() {
        if (loggedIn.get()) { showLoginSucceeded(); }
        else { showManualLoginUI(); }
    }


    private void showManualLoginUI() {
        showLoginUI("Please enter your credentials", "", false);
        usernameField.requestFocus();
    }
    
    private void showLoginSucceeded() {
        showLoginUI("Logged in as:", app.tesla.getUsername(), true);
    }
    
    private void showAutoLoginUI() {
        showLoginUI("Attempting Automatic Login", "", false);
    }
    
/*------------------------------------------------------------------------------
 *
 * Private Utility Methods and Classes
 * 
 *----------------------------------------------------------------------------*/
    
    private void attemptLogin(String username, String password) {
        app.issuer.issueCommand(
                new AttemptLogin(username, password), false, progressIndicator, "Attempt Login");
    }

    private class AttemptLogin implements Callable<Result> {
        String username, password;
        
        AttemptLogin(String username, String password) {
            this.username = username;
            this.password = password;
        }
        
        @Override public Result call() {
            boolean succeeded;
            
            if (password == null) {  // Try auto login
                String authToken = Prefs.store().get(AuthTokenKey, null);
                succeeded = (authToken == null) ? false :
                        app.tesla.connectWithToken(username, authToken);
            } else {   // Login with the specified username and password
                succeeded = app.tesla.connect(username, password);
                if (!succeeded) {
                    Platform.runLater(new Runnable() {
                        @Override public void run() {
                            Dialogs.showErrorDialog(
                                    app.stage,
                                    "Remember to use your email address as your username",
                                    "Login failed - Please check your credentials",
                                    "Problem logging in");
                        }
                    });
                } else {
                    String authToken = app.tesla.getToken();
                    if (rememberMe.isSelected()) {
                        Prefs.store().put(AuthTokenKey, authToken);
                        Prefs.store().put(UsernameKey, username);
                    }
                }
            }
            
            loggedIn.set(succeeded);
            return succeeded ? Result.Succeeded : Result.Failed;
        }
        
    }
    
}
