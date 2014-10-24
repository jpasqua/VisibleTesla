/*
 * VersionUpdater.java  - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Aug 14, 2014
 */

package org.noroomattheinn.visibletesla.dialogs;

import java.net.URL;
import java.util.List;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Dialogs;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.apache.commons.lang3.SystemUtils;
import org.noroomattheinn.utils.Utils;
import org.noroomattheinn.utils.Versions;
import org.noroomattheinn.utils.Versions.Release;
import org.noroomattheinn.visibletesla.AppContext;


/**
 * VersionUpdater: Check for, offer, and download new versions of the fxApp
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */


public class VersionUpdater {
    
/*------------------------------------------------------------------------------
 *
 * Constants and Enums
 * 
 *----------------------------------------------------------------------------*/

    private static final String VersionsFile = 
      "https://dl.dropboxusercontent.com/u/7045813/VisibleTesla/versions.xml";
      //"https://dl.dropboxusercontent.com/u/7045813/VTExtras/test_versions.xml";
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public static void conditionalCheckVersion(final AppContext appContext) {
        long lastVersionCheck = appContext.persistentState.getLong(
                appContext.vehicle.getVIN() + "_LastVersionCheck", 0);
        long now = System.currentTimeMillis();
        if (now - lastVersionCheck > (7 * 24 * 60 * 60 * 1000)) {
            checkForNewerVersion(appContext);
        }
    }
    
    public static boolean checkForNewerVersion(final AppContext appContext) {
        appContext.persistentState.putLong(
                appContext.vehicle.getVIN() + "_LastVersionCheck", System.currentTimeMillis());
        
        final Versions versions = Versions.getVersionInfo(VersionsFile);
        if (versions == null) return false; // Missing, empty, or corrupt versions file
        
        List<Release> releases = versions.getReleases();

        if (releases != null && !releases.isEmpty()) {
            Release lastRelease = null;
            for (Release cur : releases) {
                if (cur.getInvisible()) continue;
                if (cur.getExperimental() && !appContext.prefs.offerExperimental.get())
                    continue;
                lastRelease = cur;
                break;
            }
            if (lastRelease == null) return false;
            String releaseNumber = lastRelease.getReleaseNumber();
            if (Utils.compareVersions(AppContext.ProductVersion, releaseNumber) < 0) {
                VBox customPane = new VBox();
                String msgText = String.format(
                        "A newer version of VisibleTesla is available:\n" +
                        "Version: %s, Date: %tD",
                        releaseNumber, lastRelease.getReleaseDate());
                Label msg = new Label(msgText);
                Hyperlink platformLink = null;
                final URL platformURL;
                final String linkText;
                if (SystemUtils.IS_OS_MAC) {
                    linkText = "Download the latest Mac version";
                    platformURL = lastRelease.getMacURL();
                } else if (SystemUtils.IS_OS_WINDOWS) {
                    linkText = "Download the latest Windows version";
                    platformURL = lastRelease.getWindowsURL();
                } else  {
                    linkText = "Download the latest Generic version";
                    platformURL = lastRelease.getReleaseURL();
                }
                if (platformURL != null) {
                    platformLink = new Hyperlink(linkText);
                    platformLink.setStyle("-fx-color: blue; -fx-text-fill: blue;");
                    platformLink.setOnAction(new EventHandler<ActionEvent>() {
                        @Override public void handle(ActionEvent t) {
                            appContext.fxApp.getHostServices().showDocument(
                                    platformURL.toExternalForm());

                        }
                    });
                }
                Hyperlink rnLink = new Hyperlink("Click to view the release notes");
                rnLink.setOnAction(new EventHandler<ActionEvent>() {
                    @Override public void handle(ActionEvent t) {
                        appContext.fxApp.getHostServices().showDocument(
                                versions.getReleaseNotes().toExternalForm());

                    }
                });
                customPane.getChildren().addAll(msg, rnLink);
                customPane.getChildren().add(platformLink);
                Dialogs.showCustomDialog(
                        appContext.stage, customPane,
                        "Newer Version Available",
                        "Checking for Updates", Dialogs.DialogOptions.OK, null);
                return true;
            }
        }
        return false;
    }
}
