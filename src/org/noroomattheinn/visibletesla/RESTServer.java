/*
 * RESTServer.java - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: May 24, 2014
 */

package org.noroomattheinn.visibletesla;

import java.util.Map;
import org.noroomattheinn.tesla.Tesla;
import org.noroomattheinn.utils.Utils;
import spark.Spark;
import spark.Request;
import spark.Response;
import spark.Route;
import org.noroomattheinn.visibletesla.AppContext.InactivityType;

/**
 * RESTServer: Provide minimal external services.
 * 
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class RESTServer implements Runnable {
    
    private static final Map<String,InactivityType> toInactivityType = 
            Utils.newHashMap("sleep", InactivityType.Sleep,
                             "daydream", InactivityType.Daydream,
                             "wakeup", InactivityType.Awake);
    
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private final AppContext appContext;
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public RESTServer(AppContext ac) {
        appContext = ac;
    }

    @Override public void run() {
        Spark.setPort(9090);    // TO DO: Get this from appContext
        Spark.get(new Route("/action/activity/:mode") {
            @Override public Object handle(Request request, Response response) {
                String authCode = request.queryParams("authCode");
                if (authCode == null || !authCode.equals("1234")) { // TO DO: Get this from AppContext
                    if (authCode == null) authCode = "<Not Supplied>";
                    Tesla.logger.warning(
                            "Wakeup request with bad or missing authCode: " + authCode);
                    response.status(401); // 401 Unauthorized
                    return "401: Unauthorized";
                }
                String mode = request.params(":mode");
                InactivityType requestedMode = toInactivityType.get(mode);
                if (requestedMode == null) {
                    Tesla.logger.warning("Unknown inactivity mode: " + mode);
                    response.status(400); // 400 Bad Request
                    return "Unknown activity mode";
                }
                Tesla.logger.info("Requesting inactivity mode: " + mode);
                appContext.requestInactivityMode(requestedMode);
                response.status(200); // 200 OK
                return "Requested specified inactivity mode";
            }
        });
    }
}
