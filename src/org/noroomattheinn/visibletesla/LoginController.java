/*
 * LoginController.java - Copyright(c) 2013  All Rights Reserved, Joe Pasqua
 * Created: Jul 22, 2013
 */

package org.noroomattheinn.visibletesla;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import org.noroomattheinn.tesla.APICall;
import org.noroomattheinn.tesla.Result;
import org.noroomattheinn.tesla.Tesla;
import org.noroomattheinn.tesla.Vehicle;
import org.noroomattheinn.utils.Utils;


public class LoginController extends BaseController {
    //
    // Observable State
    //
    private BooleanProperty loginCompleteProperty = new SimpleBooleanProperty(false);
    BooleanProperty getLoginCompleteProperty() { return loginCompleteProperty; }
    
    //
    // The Tesla object that allows us to connect...
    //
    private Tesla tesla;
    
    //
    // The UI Elements
    //
    @FXML private Label loggedInName;
    @FXML private Button loginButton;
    @FXML private Button logoutButton;
    @FXML private PasswordField passwordField;
    @FXML private TextField usernameField;
    @FXML private ImageView loggedInImage;
    @FXML private Label loggedInStatus;

    //
    // Login / Logout Button Handlers
    //
    
    @FXML void logoutAction(ActionEvent event) { }

    @FXML void loginAction(ActionEvent event) {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        attemptLogin(username, password);
    }

    // Controller-specific initialization
    protected void doInitialize() {
        showAutoLoginUI();
    }
    
    //
    // Overriden methods from BaseController
    //
    
    protected APICall getRefreshableState() { return null; }
    
    protected void commandComplete(String commandName, Object state, boolean refresh) {
        Result r = Utils.cast(state);
        if (r.success) {
            showLoginSucceeded();   // Should be done by refreshing the view!
            loginCompleteProperty.set(true);
        } else {
            loginCompleteProperty.set(false);
            showManualLoginUI();
        }
    }

    //
    // External Interface to this controller
    //
    
    void attemptAutoLogin(Tesla t) {
        this.tesla = t;
        attemptLogin(null, null);
    }
    
    //
    // Internal classes and methods to handle the login process
    //
    
    private void attemptLogin(String username, String password) {
        issueCommand("LOGIN", new AttemptLogin(username, password), false);
    }

    // This controller doesn't reflect state - it just logs in...
    @Override protected void reflectNewState(Object state) {  }

    // Nothing to do here, there is no vehicle established yet
    @Override protected void prepForVehicle(Vehicle v) { }

    private class AttemptLogin implements Callback {
        String username, password;
        
        AttemptLogin(String username, String password) {
            this.username = username;
            this.password = password;
        }
        
        @Override public Result execute() {
            if (username == null)   // Try auto login
                return new Result(tesla.connect(), "");
            else    // Login with the specified username and password
                return new Result(tesla.connect(username, password), "");
        }
        
    }
    

    //
    // Reflect the desired state of the UI
    //
    
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

}
