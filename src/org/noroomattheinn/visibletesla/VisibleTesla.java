/*
 * VisibleTesla.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Jul 22, 2013
 */

package org.noroomattheinn.visibletesla;

import java.util.prefs.Preferences;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Dialogs;
import javafx.stage.Stage;
import org.noroomattheinn.utils.MailGun;
import org.noroomattheinn.utils.ThreadManager;
import org.noroomattheinn.utils.Utils;
import org.noroomattheinn.visibletesla.data.VTData;
import org.noroomattheinn.visibletesla.prefs.Prefs;
import org.noroomattheinn.visibletesla.rest.RESTServer;
import org.noroomattheinn.visibletesla.vehicle.VTVehicle;

/**
 * This is the main class for the VisibleTesla application.
 * In addition to loading the FXML and launching the MainController, it creates
 * all of the primary application services in the form of singletons.
 * 
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class VisibleTesla extends Application {
    MainController mainController;
    
    /**
     * This is where everything starts. It's invoked indirectly by main() and is
     * where the application is loaded and initialized.
     * 
     * @param stage
     * @throws Exception
     */
    @Override public void start(Stage stage) throws Exception {
        // The following is basically JavaFX boilerplate to get the main
        // application UI loaded and displayed
        Parent root = FXMLLoader.load(getClass().getResource("MainUI.fxml"));
        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.show();
        
        Dialogs.useNativeChrome(true);  // Tell Dialogs to use the native look

        //
        // Create the various application services (in the form of singletons)
        // in the appropriate order of dependency.
        //
        
        // Create a ThreadManager singleton. This coul
        ThreadManager.create();

        // Create the Prefs singleton early since we need preference information
        // in other parts of the initialization process
        Prefs prefs = Prefs.create(Preferences.userNodeForPackage(this.getClass()));
        
        // Create a default instance of the Mail sending class (MailGun)
        // based on stored preferences
        MailGun.createDefaultInstance("api", prefs.useCustomMailGunKey.get()
                ? prefs.mailGunKey.get() : Prefs.MailGunKey);
        
        // The App object depends on Prefs, so create it now
        App app = App.create(this, stage, prefs);
        
        // The object representing the vehicle we're monitoring
        VTVehicle v = VTVehicle.create();
        
        // Even though it's not represented in the parameters, VTData
        // depends on VTVehicle, so now you can create it
        VTData.create(app.appFileFolder(), app.progressListener);
        
        // The RESTServer depends on the App object and the Vehicle
        RESTServer.create(v, app.authenticator);
        
        // OK, that's done. Now launch the MainController and let's get started!
        mainController = Utils.cast(root.getUserData());
        mainController.start();
    }
    
    @Override public void stop() {
        // Shut the app down cleanly. All threads and components are tied into
        // the ThreadManager's shutDown mechanism.
        ThreadManager.get().shutDown();
    }
    
    /**
     * The main() method is ignored in correctly deployed JavaFX application.
     * main() serves only as fallback in case the application can not be
     * launched through deployment artifacts, e.g., in IDEs with limited FX
     * support. NetBeans ignores main().
     *
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }


}