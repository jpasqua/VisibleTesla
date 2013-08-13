/*
 * BaseController.java - Copyright(c) 2013  All Rights Reserved, Joe Pasqua
 * Created: Jul 22, 2013
 */

package org.noroomattheinn.visibletesla;

import java.net.URL;
import java.util.Date;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import org.noroomattheinn.tesla.APICall;
import org.noroomattheinn.tesla.Result;
import org.noroomattheinn.tesla.Vehicle;

/**
 * BaseController: This superclass implements most of the common mechanisms used
 * by all Controller (sub)classes. The methods in this class are divided into 5
 * categories:<ol>
 * <li>Methods completely implemented by this class and called by external objects (not the subclasses)</li>
 * <li>Abstract methods that must be implemented by subclasses to provide subclass-specific functionality</li>
 * <li>Protected methods with default implementations that *may* be overriden by subclasses</li>
 * <li>Utility methods that may be used (but not overriden) by subclasses
 * <li>Private/internal methods</li>
 * </ol>
 * 
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */

abstract class BaseController {
    // Private Constants
    private static final String RefreshCommand = "REFRESH";
    private static final String ProgressIndicatorColor = "darkgray";
    private static final double ProgressIndicatorSize = 24.0;
    private static final double ProgressIndicatorOffset = 14.0;
    private static final double RefreshButtonOffset = 14.0;
    
    // Support for the AutoRefresh Mechanism
    static final long AutoRefreshInterval = 15 * 1000;
    static BaseController activeController = null;
    static long lastRefreshTime = new Date().getTime();;
    static {
        Thread refreshThread = new Thread(new AutoRefresh());
        refreshThread.setDaemon(true);
        refreshThread.start();
    }
    
    // Internal State
    protected Vehicle vehicle;
    protected Application app;
    protected Stage stage;
    
    // UI Elements in common to all subclasses (Controllers)
    @FXML protected ResourceBundle resources;
    @FXML protected URL location;
    @FXML protected AnchorPane root;
    @FXML protected Button refreshButton;
    @FXML protected ProgressIndicator progressIndicator;
    @FXML protected Label progressLabel;

    // Subclasses must implement the doInitialize method which is called from here.
    // The implementation can be empty if the subclass has nothing to do.
    @FXML final void initialize() {
        root.setUserData(this);
        prepCommonElements();
        doInitialize();
    }
    
    
    //
    // The following methods are called by the enclosing application. Subclasses
    // don't need to (and can't) override these methods.
    //
    
    /**
     * This is called ONE TIME at startup to establish the application object
     * in which we are running. In order to keep the lower level controller
     * classes independent of the main application, we need to "inject" the application
     * object into the controllers rather than have them know about the application.
     * TO DO: Find a better way to do this
     * @param a The containing Application object. Useful, for example, to
     *          get the HostServices object.
     */
    public final void setAppContext(Application a, Stage s) {
        this.app = a;
        this.stage = s;
    }
    
    /**
     * Called whenever this controller is activated. The vehicle object may
     * be the same as supplied to a previous activation, or it may be different.
     * Subclasses of BaseController may wish to override this method to perform
     * controller-specific tasks. If so, they should do a super.activate(v) when
     * complete to finish the overall activation process.
     * @param v The vehicle object that we're being asked to control.
     */
    public final void activate(Vehicle v) {
        this.vehicle = v;
        prepForVehicle(v);
        activeController = this;
        doRefresh();
    }
    
    
    //
    // Abstract methods that must be implemented by subclasses
    //
    
    /**
     * The controller is being initialized along with the associated UI. This
     * is a chance for the BaseController subclass to do any necessary setup.
     */
    abstract protected void doInitialize();

    /**
     * Subclasses can do whatever is needed to prepare for operations on the
     * given vehicle. E.g., they may wish to create a tesla.XYZController object.
     * @param v     The Vehicle which we'll be operating on. This may or may not
     *              be the same vehicle as has been operated on in the past.
     */
    abstract protected void prepForVehicle(Vehicle v);
    
    /**
     * Return a state object that is of the type to be refreshed. Return null
     * if no refresh-able state is being handled
     */
    abstract protected APICall getRefreshableState();
    
    /**
     * Subclasses must implement this method in order to update their UI
     * after a previously launched refresh Task has completed.
     * @param state The new state resulting from the prior doRefresh()
     */
    abstract protected void reflectNewState(Object state);
    
    
    //
    // The following methods may be overriden by subclasses, but need not be.
    //
    
    /**
     * Subclasses rarely need to override this handler, but they may do so if
     * they need to do some special processing before the refresh happens (or
     * instead of it).
     * @param event     The ActionEvent from the refreshButton
     */
    // Handle the refresh button by, you guessed it, refreshing
    @FXML protected void refreshButtonHandler(ActionEvent event) { doRefresh(); }
    
    /**
     * Override in order to explicitly handle the completion of a command
     * that was issued earlier using issueCommand. The default implementation
     * simply invokes doRefresh() if refresh is true.
     * @param state     The result of the command
     * @param refresh   Whether or not to invoke doRefresh()
     */
    protected void commandComplete(String commandName, Object state, boolean refresh) {
        if (commandName.equals(RefreshCommand))
            reflectNewState(state);
        else if (refresh)
            doRefresh();
    }

    
    //
    // The following methods can be used by (but not overriden) by subclasses
    // to perform various utility functions.
    //
    
    /**
     * Launch a Task that will issue a command to a tesla controller function.
     * The call to the controller function is encapsulated in the Callback
     * object along with any state it requires to operate. If refreshAfterCommand
     * is true, doRefresh() will be invoked after the command completes. This is
     * often useful to update the state of an associated object to determine
     * the impact of the command that was issued (eg refresh DoorState after
     * invoking functionality in DoorController).
     */
    protected final void issueCommand(String commandName, Callback c, boolean refreshAfterCommand) {
        Task<Result> task = new IssueCommand(c);
        launchTask(commandName, task, true, refreshAfterCommand);
    }
    
    /**
     * Launches a Task to refresh the state of an object of type T.
     * @param <T>   The Type of the Task that will be launched
     * @param task  The Task to be launched that will do the refresh
     */
    protected final void doRefresh() {
        APICall stateObject = getRefreshableState();
        if (stateObject == null) return;
        Task<APICall> task = new <APICall>RefreshTask(stateObject);
        launchTask(RefreshCommand, task, false, false);
        lastRefreshTime = new Date().getTime(); // Used by the AutoRefresh mechanism
    }
    
    protected final void setOptionState(boolean selected, ImageView selImg, ImageView deselImg) {
        if (deselImg != null) deselImg.setVisible(!selected);
        if (selImg != null) selImg.setVisible(selected);
    }
    
    
    //
    // Private Task Handling Methods
    //
    
    private <T> void launchTask(
            String commandName, Task<T> task, boolean isCommand, boolean refreshAfter) {
        showProgressUI(true);
        EventHandler<WorkerStateEvent> handler =
                new CompletionHandler(commandName, refreshAfter);
        task.setOnSucceeded(handler);
        task.setOnFailed(handler);
        bindProgress(task);
        new Thread(task).start();
    }
    
    private class IssueCommand extends Task<Result> {
        Callback callback;

        IssueCommand(Callback c) { this.callback = c; }

        @Override
        protected Result call() throws Exception {
            updateProgress(-1, 100);
            updateMessage("");
            Result r = callback.execute();
            if (r.success) {
                updateProgress(1, 1);
                return r;
            }
            updateMessage("Problem communicating with Tesla");
            updateProgress(1, 1);
            return new Result(false, "");
        }

    }
    
    private class CompletionHandler implements EventHandler<WorkerStateEvent> {
        String commandName;
        boolean refreshAfterCommand;
        CompletionHandler(String commandName, boolean refreshAfterCommand) {
            this.commandName = commandName;
            this.refreshAfterCommand = refreshAfterCommand;
        }
        
        @Override public void handle(WorkerStateEvent event) {
            unbindProgress();
            Worker w = event.getSource();
            Object result = null;
            if (w.getState() == Worker.State.SUCCEEDED) {
                // The task succeeded, now check if the lookup succeeded!
                result = w.getValue();
            }
            showProgressUI(false);
            commandComplete(commandName, result, refreshAfterCommand);
        }
    }
    
    
    //
    // Private Utility Methods
    //
    
    private void bindProgress(Task t) {
        progressIndicator.progressProperty().bind(t.progressProperty());
        progressLabel.textProperty().bind(t.messageProperty());
    }
    
    private void unbindProgress() {
        progressIndicator.progressProperty().unbind();  // Get rid of binding
        progressLabel.textProperty().unbind();          // Get rid of binding
    }
    
    private void showProgressUI(boolean show) {
        progressIndicator.setVisible(show);
        progressLabel.setVisible(false);    // Progress label is only used for debugging
    }
    
    private void prepCommonElements() {
        // Set the size, color, and location/anchor of the progressIndicator
        progressIndicator.setStyle("-fx-progress-color: " + ProgressIndicatorColor + ";");
        progressIndicator.setMaxWidth(ProgressIndicatorSize);
        progressIndicator.setMaxHeight(ProgressIndicatorSize);
        // Place the indicator at a fixed offset from the lower left corner
        AnchorPane.setLeftAnchor(progressIndicator, ProgressIndicatorOffset);
        AnchorPane.setBottomAnchor(progressIndicator, ProgressIndicatorOffset);
        
        // Set the location/anchor of the refresh button
        if (refreshButton != null) {    // Some controllers don't have a refresh button
            AnchorPane.setRightAnchor(refreshButton, RefreshButtonOffset);
            AnchorPane.setBottomAnchor(refreshButton, RefreshButtonOffset);
        }
    }

}


/**
 * Implementations of this interface are used as input to the issueCommand
 * method. When the execute method is called, it should invoke a command
 * on a tesla Controller object
 */
interface Callback { Result execute(); }



/**
 * This class is a sublcass of Task used as input to the doRefresh method.
 * It is parameterized by the APICall subclass representing the type of
 * state we are refreshing (eg DoorState or ChargeState).
 * 
 * @param <S> The type of the State object we're updating
 */
class RefreshTask extends Task<APICall> {
    APICall state;

    RefreshTask(APICall state) { this.state = state; }

    @Override
    protected APICall call() throws Exception {
        String stateName = state.getStateName();
        updateProgress(-1, 100);
        updateMessage("Retrieving " + stateName);
        if (state.refresh()) {
            updateMessage(stateName + " retrieved");
            updateProgress(1, 1);
            return state;
        }
        updateMessage("Failed to retrieve " + stateName);
        updateProgress(1, 1);
        return null;
    }
}

/**
 * This class runs in the background and periodically invokes doRefresh()
 * so that the UI stays up to date without the user hitting the refresh button.
 * 
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
class AutoRefresh implements Runnable {
    @Override public void run() {
        while (true) {
            try {
                long timeToSleep = BaseController.AutoRefreshInterval;
                while (timeToSleep > 0) {
                    Thread.sleep(timeToSleep);
                    timeToSleep = BaseController.AutoRefreshInterval - 
                            (new Date().getTime() - BaseController.lastRefreshTime);
                    timeToSleep = Math.min(timeToSleep, BaseController.AutoRefreshInterval);
                }
                Platform.runLater(new FireRefresh());
            } catch (InterruptedException ex) {
                Logger.getLogger(AutoRefresh.class.getName()).log(Level.FINEST, null, ex);
            }
        }
    }
    
    class FireRefresh implements Runnable {
        @Override public void run() {
            BaseController active = BaseController.activeController;
            if (active == null) return;
            active.doRefresh();
        }
    }
}

