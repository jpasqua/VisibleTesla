/*
 * MessageTarget.java - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Jun 12, 2014
 */

package org.noroomattheinn.visibletesla;

import org.noroomattheinn.utils.Utils;
import org.noroomattheinn.visibletesla.prefs.Prefs;

import static org.noroomattheinn.tesla.Tesla.logger;

class MessageTarget {
    private String address, subject, message;

    private final String theKey;
    private final String dfltSubj, dfltMsg;
    private final Prefs prefs;
    
    MessageTarget(Prefs prefs, String theKey, String dfltSubj, String dfltMsg) {
        this.prefs = prefs;
        this.theKey = theKey;
        this.dfltSubj = dfltSubj;
        this.dfltMsg = dfltMsg;
        this.internalize();
    }

    String getActiveEmail() {
        return address != null ? address : prefs.notificationAddress.get();
    }
    String getActiveSubj() { return subject != null ? subject : dfltSubj; }
    String getActiveMsg() { return message != null ? message : dfltMsg; }

    String getEmail() { return address; }
    void setEmail(String email) {  address = email; }

    String getSubject() { return subject; }
    void setSubject(String subject) { this.subject = subject; }

    String getMessage() { return message; }
    void setMessage(String msg) { this.message = msg; }

    final void externalize() {
        String encoded = String.format("%s_%s_%s",
                address == null ? "null" : encodeUnderscore(address),
                subject == null ? "null" : encodeUnderscore(subject),
                message == null ? "null" : Utils.toB64(message.getBytes()));
        prefs.storage().put(theKey, encoded);
    }

    final void internalize() {
        address = subject = message = null;

        String encoded = prefs.storage().get(theKey, "");
        if (encoded.isEmpty()) return;

        String[] elements = encoded.split("_");
        if (elements.length < 2 || elements.length > 3) {
            logger.warning("Malformed MessageTarget String: " + encoded);
            return;
        }
        address = elements[0].equals("null") ? null : decodeUnderscore(elements[0]);
        subject = elements[1].equals("null") ? null : decodeUnderscore(elements[1]);
        if (elements.length == 3) {
            message = elements[2].equals("null") ? null : Utils.decodeB64(elements[2]);
        }
    }

    private String encodeUnderscore(String input) {
        return input.replace("_", "&#95;");
    }

    private String decodeUnderscore(String input) {
        return input.replace("&#95;", "_");
    }

}
