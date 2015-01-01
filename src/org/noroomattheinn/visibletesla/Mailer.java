/*
 * Mailer.java - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Dec 31, 2014
 */

package org.noroomattheinn.visibletesla;

import org.noroomattheinn.utils.MailGun;

/**
 * Singleton wrapper on the MailGun class.
 * 
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class Mailer {
    private static MailGun mailer = null;
    
    public static MailGun create(Prefs prefs) {
        if (mailer != null) return mailer;
        mailer = new MailGun("api", prefs.useCustomMailGunKey.get()
                ? prefs.mailGunKey.get() : Prefs.MailGunKey);
        return mailer;
    }
    
    public static MailGun get() { return mailer; }
    
}
