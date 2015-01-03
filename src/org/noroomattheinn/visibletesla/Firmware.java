/*
 * Firmware - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Nov 20, 2014
 */
package org.noroomattheinn.visibletesla;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Properties;

import static org.noroomattheinn.tesla.Tesla.logger;

/**
 * Firmware: Determine software version from firmware version
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class Firmware {
    
/*------------------------------------------------------------------------------
 *
 * Constants and Enums
 * 
 *----------------------------------------------------------------------------*/
    
    private static final String FirmwareVersionsFile =
        "org/noroomattheinn/visibletesla/FirmwareVersions.properties";
        // This data is collected from:
        // http://www.teslamotorsclub.com/showwiki.php?title=Model+S+software+firmware+changelog
    private static final String FirmwareVersionsURL =
        "https://dl.dropboxusercontent.com/u/7045813/VisibleTesla/FirmwareVersions.properties";

/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private static Properties firmwareVersions = 
        loadFirmwareVersion(
            Firmware.class.getClassLoader().getResourceAsStream(FirmwareVersionsFile));
    
    public static String getSoftwareVersion(String firmwareVersion) {
        String v = firmwareVersions.getProperty(firmwareVersion);
        if (v != null) return v;
        
        try {
            InputStream is = new URL(FirmwareVersionsURL).openStream();
            loadFirmwareVersion(is);
            v = firmwareVersions.getProperty(firmwareVersion);
        } catch (IOException ex) {
            logger.warning("Couldn't download firmware versions property file: " + ex);
        }
        if (v == null) {
            v = firmwareVersion;
            // Avoid testing for new versions of the firmware mapping file every
            // time around. We'll check again next time the App starts
            firmwareVersions.put(firmwareVersion, firmwareVersion);
        }
        return v;
    }
    
    private static Properties loadFirmwareVersion(InputStream is) {
        Properties p = new Properties();
        try {
            p.load(is);
        } catch (IOException ex) {
            logger.warning("Couldn't load firmware versions property file: " + ex);
        }
        return p;
    }
}
