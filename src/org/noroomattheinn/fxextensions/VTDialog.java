/*
 * VTDialog.java - Copyright(c) 2013, 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Aug 31, 2013
 */

package org.noroomattheinn.fxextensions;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.logging.Level;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import static org.noroomattheinn.tesla.Tesla.logger;

/**
 * VTDialog
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */

public class VTDialog {
    
    public static <C extends VTDialog.Controller> C load(URL fxmlResource, String title, Stage stage) {
        try {
            // Load the fxml file and create a new stage for the popup
            FXMLLoader loader = new FXMLLoader(fxmlResource);
            AnchorPane dialog = (AnchorPane)loader.load();
            Stage dialogStage = new Stage();
            dialogStage.setTitle(title);
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(stage);
            Scene scene = new Scene(dialog);
            dialogStage.setScene(scene);
            C controller = loader.getController();
            controller.dialogStage = dialogStage;
            return controller;
        } catch (IOException e) {
            // Exception gets thrown if the fxml file could not be loaded
            logger.log(Level.SEVERE, "Can't load dialog", e);
            throw new IllegalArgumentException("Can't Happen: " + e);
        }
    }
    
    
    
    public static class Controller {
        @FXML protected AnchorPane root;
        @FXML protected ResourceBundle resources;
        @FXML protected URL location;
        
        protected Stage dialogStage;
        
        public void show() { dialogStage.showAndWait(); }
    }
}
