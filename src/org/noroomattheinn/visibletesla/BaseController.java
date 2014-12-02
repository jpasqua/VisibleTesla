/*
 * BaseController.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Jul 22, 2013
 */

package org.noroomattheinn.visibletesla;

import java.net.URL;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.TimerTask;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import org.noroomattheinn.tesla.Result;
import static org.noroomattheinn.tesla.Tesla.logger;
import org.noroomattheinn.tesla.Vehicle;

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
    protected static final long MinRefreshInterval =   2 * 1000;
    
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/

    private static BaseController activeController = null;
    private static long lastRefreshTime;
    
    protected boolean       initialized = false;    // Has this controller been init'd?
    protected AppContext    ac;                     // The overall app context
    
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

/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/

    @FXML final void initialize() {
        root.setUserData(this);
        prepCommonElements();
        fxInitialize();     // This is an initialization hook for subclasses
    }
    
    /**
     * This is called ONE TIME at startup to establish the ac
     * in which we are running. In order to keep the lower level controller
     * classes independent of the main application, we need to "inject" the fxApp
     * context into the controllers rather than have them know about the top
     * level fxApp class.
     * 
     * Subclasses may override appInitialize if they want to do something
     * once the ac is set (not true at fxInitialize() time)
     */
    public final void setAppContext(AppContext ctxt) {
        this.ac = ctxt;
    }
    
    /**
     * Called whenever the tab associated with this controller is activated.
     */
    public final void activate() {
        activeController = this;
        if (!initialized) { initializeState(); initialized = true; }
        activateTab();
        if (ac.appState.isIdle()) {
            if (ac.prefs.wakeOnTabChange.get()) {
                ac.appState.setActive();
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
        if (System.currentTimeMillis() - lastRefreshTime < MinRefreshInterval) {
            logger.log(Level.INFO, "Ignoring refresh button - we just refreshed!");
            return;
        }
        doRefresh();
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
     */
    abstract protected void initializeState();
    
    /**
     * Called every time the associated tab is selected.
     */
    abstract protected void activateTab();
    
    /**
     * Do whatever is required to refresh the state the controller uses
     * to display its UI. This doesn't actually update the UI, it just gets
     * the state needed to do so.
     */
    abstract protected void refresh();
    
/*------------------------------------------------------------------------------
 * 
 * Convenience methods used to issue commands and update state
 * 
 *----------------------------------------------------------------------------*/    

    protected final void issueCommand(Callable<Result> c, String commandName) {
        ac.issuer.issueCommand(c, true, progressIndicator, commandName);
    }
    
    protected final void updateState(Vehicle.StateType whichState) {
        ac.stateProducer.produce(whichState, progressIndicator);
    }

    protected final void updateStateLater(final Vehicle.StateType whichState, long delay) {
        ac.tm.addTimedTask(new TimerTask() {
            @Override public void run() { updateState(whichState);  } }, delay);
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
    
    protected <K> void updateImages(K key, Map<K,ImageView[]> imageMap, Map<K,K> equivs) {
        // Turn off all images
        for (ImageView[] images: imageMap.values()) {
            for (ImageView image: images) { image.setVisible(false); }
        }
        // Turn appropriate images on
        K mapped = equivs.get(key);
        if (mapped != null) key = mapped;
        ImageView[] images = imageMap.get(key);
        for (ImageView image: images) { image.setVisible(true); }
    }
      
    protected final boolean active() { return activeController == this; }
    

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
