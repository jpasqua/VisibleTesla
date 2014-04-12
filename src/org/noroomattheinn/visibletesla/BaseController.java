/*
 * BaseController.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Jul 22, 2013
 */

package org.noroomattheinn.visibletesla;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
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
import org.noroomattheinn.tesla.APICall;
import org.noroomattheinn.tesla.Result;
import org.noroomattheinn.tesla.Tesla;
import org.noroomattheinn.tesla.Vehicle;
import org.noroomattheinn.utils.Utils;
import org.noroomattheinn.visibletesla.AppContext.InactivityType;

/**
 * BaseController: This superclass implements most of the common mechanisms used
 * by all Controller (sub)classes. The methods in this class are divided into 5
 * categories:<ol>
 * <li>Methods completely implemented by this class and called by external
 *     objects (not the subclasses)</li>
 * <li>Abstract methods that must be implemented by subclasses to provide
 *     subclass-specific functionality</li>
 * <li>Protected methods with default implementations that *may* be overriden
 *     by subclasses</li>
 * <li>Utility methods that may be used (but not overriden) by subclasses
 * <li>Private/internal methods</li>
 * </ol>
 * 
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */

abstract class BaseController {
/*------------------------------------------------------------------------------
 *
 * Constants and Enums
 * 
 *----------------------------------------------------------------------------*/
    protected static final long AutoRefreshInterval = 30 * 1000;
    protected static final long MinRefreshInterval =   2 * 1000;
    
    enum AfterCommand {Reflect, Refresh, RefreshLater, Nothing};
    
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private static BaseController activeController = null;    
    protected static long lastRefreshTime;  // Primarily sed by AutoRefresh
    protected Vehicle vehicle;              // The current vehicle
    protected Vehicle lastVehicle ;         // The last vehicle we had
    protected AppContext appContext;        // The overall app context
    protected boolean userInvokedRefresh;   // Is the current refresh a result
                                            // of the user pressing the refresh
                                            // button or is it an auto-refresh?
    
/*------------------------------------------------------------------------------
 *
 * UI Elements that are common to all subclasses (Controllers)
 * 
 *----------------------------------------------------------------------------*/
    
    @FXML protected ResourceBundle resources;
    @FXML protected URL location;
    @FXML protected AnchorPane root;
    @FXML protected Button refreshButton;
    @FXML protected ProgressIndicator progressIndicator;
    @FXML protected Label progressLabel;

/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/

    @FXML final void initialize() {
        root.setUserData(this);
        prepCommonElements();
        lastVehicle = null;
        fxInitialize();     // This is an initialization hook for subclasses
    }
    
    /**
     * This is called ONE TIME at startup to establish the appContext
     * in which we are running. In order to keep the lower level controller
     * classes independent of the main application, we need to "inject" the app
     * context into the controllers rather than have them know about the top
     * level app class.
     * 
     * Subclasses may override appInitialize if they want to do something
     * once the appContext is set (not true at fxInitialize() time)
     */
    public final void setAppContext(AppContext ctxt) {
        this.appContext = ctxt;
        ensureRefreshThread();
        appInitialize();    // This is an initialization hook for subclasses
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
        this.lastVehicle = vehicle;
        this.vehicle = v;
        prepForVehicle(v);              // Notify subclasses
        appContext.prepForVehicle(v);   // Notify the appContext machinery
        activeController = this;
        if (appContext.isSleeping()) {
            if (appContext.prefs.wakeOnTabChange.get()) {
                appContext.wakeup();
                doRefresh();
            }
        } else {
            doRefresh();
        }
        
        
    }
    
/*------------------------------------------------------------------------------
 *
 * UI Action Handlers
 * 
 *----------------------------------------------------------------------------*/
    
    @FXML protected void refreshButtonHandler(ActionEvent event) {
        long now = System.currentTimeMillis();
        if (now - lastRefreshTime < MinRefreshInterval) {
            Tesla.logger.log(Level.INFO, "Ignoring refresh button - we just refreshed!");
            return;
        }
        userInvokedRefresh = true;
            doRefresh();
        userInvokedRefresh = false;
    }

/*------------------------------------------------------------------------------
 * 
 * The following methods must be implemented by subclasses though the
 * implementation may be empty.
 * 
 *----------------------------------------------------------------------------*/
    
    /**
     * The controller is being initialized along with the associated UI. This
     * is a chance for the BaseController subclass to do any necessary setup.
     */
    abstract protected void fxInitialize();
    

    /**
     * Subclasses can do whatever is needed to prepare for operations on the
     * given vehicle. E.g., they may wish to create a tesla.XYZController object.
     * @param v     The Vehicle which we'll be operating on. This may or may not
     *              be the same vehicle as has been operated on in the past.
     */
    abstract protected void prepForVehicle(Vehicle v);
    
    /**
     * Do whatever is required to refresh the state the controller uses
     * to display its UI. This doesn't actually update the UI, it just gets
     * the state needed to do so.
     */
    abstract protected void refresh();
    
    /**
     * Subclasses must implement this method in order to update their UI
     * after a previously launched refresh Task has completed.
     */
    abstract protected void reflectNewState();
    
    
/*------------------------------------------------------------------------------
 * 
 * The following methods may be overridden by subclasses but need not be.
 * 
 *----------------------------------------------------------------------------*/
    
    /**
     * Gives controllers a chance to do one-time initialization *after* the 
     * appCntext is set. At fxInitialize time the appContext isn't ready yet.
     */
    protected void appInitialize() {}


    
/*------------------------------------------------------------------------------
 * 
 * Methods for use by subclasses to issue commands and refresh state
 * 
 *----------------------------------------------------------------------------*/    

    private static Thread refreshThread = null;

    /**
     * Launch a Task that will issue a command to a tesla controller function.
     * The call to the controller function is encapsulated in the Callback
     * object along with any state it requires to operate.
     * @param c         The Callback object which will be invoked to do the work
     * @param action    What to do after the command is complete
     */
    protected final void issueCommand(Callable<Result> c, AfterCommand action) {
        Task<Result> task = new IssueCommand(c);
        showProgressUI(true);
        EventHandler<WorkerStateEvent> handler = new CompletionHandler(action);
        task.setOnSucceeded(handler);
        task.setOnFailed(handler);
        progressIndicator.progressProperty().bind(task.progressProperty());
        progressLabel.textProperty().bind(task.messageProperty());
        appContext.launchThread(task, "00 IssueCommand");
    }
    
    /**
     * Issue a command to refresh the given state object and reflect the
     * results after the command is complete.
     * @param state     The state to be refreshed
     */
    protected final void updateState(final APICall state) {
        issueCommand(new Callable<Result>() {
            @Override public Result call() {
                if (state.refresh()) {
                    appContext.noteUpdatedState(state);
                    return Result.Succeeded;
                } else {
                    return Result.Failed;
                }
            }
        }, AfterCommand.Reflect);
    }
    
/*------------------------------------------------------------------------------
 *
 * Utility Methods for use by subclasses
 * 
 *----------------------------------------------------------------------------*/
    
    protected final void setOptionState(boolean selected, ImageView selImg, ImageView deselImg) {
        if (deselImg != null) deselImg.setVisible(!selected);
        if (selImg != null) selImg.setVisible(selected);
    }
    
    protected final boolean differentVehicle() {
        if (lastVehicle == null) return true;
        return !lastVehicle.getVIN().equals(vehicle.getVIN());
    }
    
    protected String prefKey(String key) {
        return vehicle.getVIN() + "_" + key;
    }
    
/*------------------------------------------------------------------------------
 * 
 * The following methods and classes implement background tasks for issuing
 * commands and refreshing state automatically.
 * 
 *----------------------------------------------------------------------------*/    
    
    private class IssueCommand extends Task<Result> {
        Callable<Result> callable;

        IssueCommand(Callable<Result> c) { this.callable = c; }

        @Override
        protected Result call() throws Exception {
            updateProgress(-1, 100);
            updateMessage("");
            Result r = callable.call();
            if (r.success) {
                return r;
            }
            updateMessage("Problem communicating with Tesla");
            return new Result(false, "");
        }

    }
    
    private class CompletionHandler implements EventHandler<WorkerStateEvent> {
        AfterCommand action;
        
        CompletionHandler(AfterCommand action) { this.action = action; }
        
        @Override public void handle(WorkerStateEvent event) {
            Worker w = event.getSource();
            Object result = null;
            if (w.getState() == Worker.State.SUCCEEDED) {
                // The task succeeded, now check if the lookup succeeded!
                result = w.getValue();
            } else {
                Tesla.logger.warning("Exception during IssueCommand: " +
                        w.getException().getLocalizedMessage());
                action = AfterCommand.Nothing;
            }

            showProgressUI(false);
            switch (action) {
                case Reflect: reflectNewState(); break;
                case RefreshLater: Utils.sleep(500); doRefresh(); break;
                case Refresh: doRefresh(); break;
                case Nothing:
                default: break;
            }
        }
    }
    
    private void ensureRefreshThread() {
        if (refreshThread == null) {
            refreshThread = appContext.launchThread(new AutoRefresh(), "00 AutoRefresh");
            lastRefreshTime = System.currentTimeMillis();
        }
    }
    
    class AutoRefresh implements Runnable, ChangeListener<InactivityType> {
        private InactivityType inactivityState = InactivityType.Awake;
        
        @Override public void
        changed(ObservableValue<? extends InactivityType> o, InactivityType ov, InactivityType nv) {
            inactivityState = nv;
        }

        @Override public void run() {
            appContext.inactivityState.addListener(this);
            inactivityState = appContext.inactivityState.get();
            while (true) {
                long timeToSleep = AutoRefreshInterval;
                while (timeToSleep > 0) {
                    Utils.sleep(timeToSleep);
                    if (appContext.shuttingDown.get())
                        return;
                    timeToSleep = AutoRefreshInterval - 
                            (System.currentTimeMillis() - lastRefreshTime);
                    timeToSleep = Math.min(timeToSleep, AutoRefreshInterval);
                }
                if (inactivityState == InactivityType.Awake)
                    Platform.runLater(new FireRefresh());
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

/*------------------------------------------------------------------------------
 * 
 * Private Utility Methods
 * 
 *----------------------------------------------------------------------------*/    

    /**
     * Wrapper around refresh() that keeps track of the time to facilitate AutoRefresh
     */
    private void doRefresh() {
        lastRefreshTime = System.currentTimeMillis(); // Used by the AutoRefresh mechanism
        refresh();
    }
    

    private int spuiCount = 0;
    private void showProgressUI(boolean show) {
        spuiCount = spuiCount + (show ? 1 : -1);
        progressIndicator.setVisible(spuiCount != 0);
        progressLabel.setVisible(false);    // Progress label is only used for debugging
        
    }
    
    private static final String ProgressIndicatorColor = "darkgray";
    private static final double ProgressIndicatorSize = 24.0;
    private static final double ProgressIndicatorOffset = 14.0;
    private static final double RefreshButtonOffset = 14.0;
    
    private void prepCommonElements() {
        if (progressIndicator != null) {
            // Set the size, color, and location/anchor of the progressIndicator
            progressIndicator.setStyle("-fx-progress-color: " + ProgressIndicatorColor + ";");
            progressIndicator.setMaxWidth(ProgressIndicatorSize);
            progressIndicator.setMaxHeight(ProgressIndicatorSize);
            // Place the indicator at a fixed offset from the lower left corner
            AnchorPane.setLeftAnchor(progressIndicator, ProgressIndicatorOffset);
            AnchorPane.setBottomAnchor(progressIndicator, ProgressIndicatorOffset);
        }
        
        // Set the location/anchor of the refresh button
        if (refreshButton != null) {    // Some controllers don't have a refresh button
            AnchorPane.setRightAnchor(refreshButton, RefreshButtonOffset);
            AnchorPane.setBottomAnchor(refreshButton, RefreshButtonOffset);
        }
    }
    
}
