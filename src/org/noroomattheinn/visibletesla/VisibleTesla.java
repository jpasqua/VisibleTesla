/*
 * VisibleTesla.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Jul 22, 2013
 */

package org.noroomattheinn.visibletesla;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Dialogs;
import javafx.stage.Stage;
import org.noroomattheinn.utils.Utils;

/**
 * This is the main class for the VisibleTesla application.
 * In addition to loading the FXML and starting it off, this class maintains
 * global application state that is available to all of the individual tabs
 * which implement the various functions.
 * <P>
 * This is a singleton class which can be accessed via <code>VisibleTesla.getInstance()</code>.
 * With the instance in hand, the individual tabs can access the selected vehicle
 * which is the primary object they need to perform all activities.
 * <P>
 * The AgreggatorConnector doesn't do much, but it does give access to the underlying
 * Tabs. This is necessary so that this class can access the LoginConnector
 * and attempt an automatic login (based on previously set cookies). None of the
 * other tabs are available until a successful login occurs.
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
        setupLogger();

        Parent root = FXMLLoader.load(getClass().getResource("MainUI.fxml"));
        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.show();
        
        // Everything above is boilerplate. The only thing this method does that
        // is out of the ordinary is telling the MainController that startup is
        // complete and that it can start the mainline activity of the App.
        Dialogs.useNativeChrome(true);
        mainController = Utils.cast(root.getUserData());
        mainController.start(this, stage);
    }
    
    @Override public void stop() {
        mainController.stop();
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

    private void setupLogger() {
        rotateLogs(3);
        
        Logger logger = Logger.getLogger("");
        FileHandler fileHandler;
        try {
            fileHandler = new FileHandler("visibletesla-00.log");
            fileHandler.setFormatter(new SimpleFormatter());
            fileHandler.setLevel(Level.ALL);
            logger.addHandler(fileHandler);
        } catch (IOException | SecurityException ex) {
            logger.log(Level.SEVERE, "Unable to establish log file");
        }
    }
    
    private void rotateLogs(int max) {
        File logfile = new File(String.format("visibletesla-%02d.log", max));
        if (logfile.exists()) {
            logfile.delete();
        }
        if (max > 0) {
            File previous = new File(String.format("visibletesla-%02d.log", max-1));
            if (previous.exists()) {
                previous.renameTo(logfile);
            }
            rotateLogs(max-1);
        }
    }
}