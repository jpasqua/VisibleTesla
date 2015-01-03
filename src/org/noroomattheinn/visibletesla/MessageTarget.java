/*
 * MessageTarget.java - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Jun 12, 2014
 */

package org.noroomattheinn.visibletesla;

import org.noroomattheinn.visibletesla.vehicle.VTVehicle;
import org.noroomattheinn.visibletesla.prefs.Prefs;
import static org.noroomattheinn.tesla.Tesla.logger;
import org.noroomattheinn.utils.Utils;

class MessageTarget {
    private String address;
    private String subject, dfltSubj;
    private String message, dfltMsg;

    private App ac;
    private String theKey;

    MessageTarget(App ac, String baseKey, String dfltSubj, String dfltMsg) {
        this.ac = ac;
        this.theKey = key(baseKey);
        this.dfltSubj = dfltSubj;
        this.dfltMsg = dfltMsg;
        this.internalize();
    }

    String getActiveEmail() {
        return address != null ? address : Prefs.get().notificationAddress.get();
    }
    String getActiveSubj() { return subject != null ? subject : dfltSubj; }
    String getActiveMsg() { return message != null ? message : dfltMsg; }

    String getEmail() { return address; }
    void setEmail(String email) {  address = email; }

    String getSubject() { return subject; }
    void setSubject(String subject) { this.subject = subject; }

    String getMessage() { return message; }
    void setMessage(String msg) { this.message = msg; }

    String getDfltSubj() { return dfltSubj; }
    void setDfltSubj(String subject) { this.dfltSubj = subject; }

    String getDfltMsg() { return dfltMsg; }
    void setDfltMsg(String msg) { this.dfltMsg = msg; }

    final void externalize() {
        String encoded = String.format("%s_%s_%s",
                address == null ? "null" : encodeUnderscore(address),
                subject == null ? "null" : encodeUnderscore(subject),
                message == null ? "null" : Utils.toB64(message.getBytes()));
        Prefs.store().put(theKey, encoded);
    }

    final void internalize() {
        address = subject = message = null;

        String encoded = Prefs.store().get(theKey, "");
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

    private String key(String base) {
        return VTVehicle.get().getVehicle().getVIN()+"_MT_"+base;
    }

}
