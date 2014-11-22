/*
 * DialogUtils.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Aug 31, 2013
 */

package org.noroomattheinn.visibletesla.dialogs;

import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.logging.Level;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import static org.noroomattheinn.tesla.Tesla.logger;

/**
 * DialogUtils
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */

public class DialogUtils {

    public static DialogController displayDialog(URL fxmlResource, String title, Stage owner, Map props) {
        try {
            // Load the fxml file and create a new stage for the popup
            FXMLLoader loader = new FXMLLoader(fxmlResource);
            AnchorPane dialog = (AnchorPane) loader.load();
            Stage dialogStage = new Stage();
            dialogStage.setTitle(title);
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(owner);
            Scene scene = new Scene(dialog);
            dialogStage.setScene(scene);
            DialogController controller = loader.getController();
            controller.setStage(dialogStage);
            controller.setProps(props);

            // Show the dialog and wait until the user closes it
            dialogStage.showAndWait();

            return controller;
        } catch (IOException e) {
            // Exception gets thrown if the fxml file could not be loaded
            logger.log(Level.SEVERE, "Can't load dialog", e);
            return null;
        }
    }
    
    public static interface DialogController {
        void setStage(Stage dialogStage);
        void setProps(Map props);
    }
}
