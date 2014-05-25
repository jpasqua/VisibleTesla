/*
 * RESTServer.java - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: May 24, 2014
 */

package org.noroomattheinn.visibletesla;

import com.sun.net.httpserver.BasicAuthenticator;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.noroomattheinn.tesla.Tesla;
import org.noroomattheinn.utils.PWUtils;
import org.noroomattheinn.utils.Utils;
import org.noroomattheinn.visibletesla.AppContext.InactivityType;

/**
 * RESTServer: Provide minimal external services.
 * 
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class RESTServer {
    
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
    private HttpServer server;
    private PWUtils pwUtils = new PWUtils();
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public RESTServer(AppContext ac) {
        appContext = ac;
        server = null;
    }

    public synchronized void launch() {
        if (!appContext.prefs.enableRest.get()) {
            Tesla.logger.info("REST Services are disabled");
            return;
        }
        int restPort = appContext.prefs.restPort.get();
        try {
            server = HttpServer.create(new InetSocketAddress(restPort), 0);
            HttpContext cc  = server.createContext("/action/activity", activityRequest);
            cc.setAuthenticator(authenticator);
            server.setExecutor(null); // creates a default executor
            server.start();
        } catch (IOException ex) {
            Tesla.logger.severe("Unable to start RESTServer: " + ex.getMessage());
        }
    }

    public synchronized void shutdown() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

/*------------------------------------------------------------------------------
 *
 * PRIVATE - Request Handlers
 * 
 *----------------------------------------------------------------------------*/
    
    private HttpHandler activityRequest = new HttpHandler() {
        @Override public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equals("GET")) {
                sendResponse(exchange, 400, "GET only on activity endpoint");
                return;
            }
            String path = exchange.getRequestURI().getPath();
            String mode = StringUtils.substringAfterLast(path, "/");
            InactivityType requestedMode = toInactivityType.get(mode);
            if (requestedMode == null) {
                Tesla.logger.warning("Unknown inactivity mode: " + mode + "\n");
                sendResponse(exchange, 400, "Unknown activity mode");
                return;
            }
            Tesla.logger.info("Requested inactivity mode: " + mode);
            appContext.requestInactivityMode(requestedMode);
            sendResponse(exchange, 200,  "Requested mode: " + mode + "\n");
        }
    };
    
/*------------------------------------------------------------------------------
 *
 * PRIVATE - Utility Methods
 * 
 *----------------------------------------------------------------------------*/
    private BasicAuthenticator authenticator = new BasicAuthenticator("VT Action") {
        @Override public boolean checkCredentials(String user, String pwd) {
            if (!user.equals("VT")) return false;
            return pwUtils.authenticate(pwd, appContext.restEncPW, appContext.restSalt);
        }
        
    };
    
    private void sendResponse(HttpExchange exchange, int code, String response) throws IOException {
        exchange.sendResponseHeaders(code, response.length());
        OutputStream os = exchange.getResponseBody();
        os.write(response.getBytes());
        os.close();
    }
    
}