/*
 * RESTServer.java - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: May 24, 2014
 */

package org.noroomattheinn.visibletesla.rest;

import com.sun.net.httpserver.BasicAuthenticator;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.Map;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.noroomattheinn.tesla.Tesla;
import org.noroomattheinn.utils.PWUtils;
import org.noroomattheinn.utils.Utils;
import org.noroomattheinn.visibletesla.MessageTemplate;
import org.noroomattheinn.visibletesla.AppContext;
import org.noroomattheinn.visibletesla.AppContext.InactivityType;
import org.noroomattheinn.utils.LRUMap;

/**
 * RESTServer: Provide minimal external services.
 * 
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class RESTServer {
    
/*------------------------------------------------------------------------------
 *
 * Constants and Enums
 * 
 *----------------------------------------------------------------------------*/

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
            
            HttpContext cc;
            cc = server.createContext("/v1/action/activity", activityRequest);
            cc.setAuthenticator(authenticator);
            cc = server.createContext("/", staticPageRequest);
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
                sendResponse(exchange, 400, "GET only on activity endpoint\n");
                return;
            }
            String path = StringUtils.stripEnd(exchange.getRequestURI().getPath(), "/");
            String mode = StringUtils.substringAfterLast(path, "/");
            if (mode.equals("activity")) {
                sendResponse(exchange, 403, "403 (Forbidden)\n");
                return;
            }
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

    private HttpHandler staticPageRequest = new HttpHandler() {
        LRUMap<String,String> cache = new LRUMap<>(10);
        @Override public void handle(HttpExchange exchange) throws IOException {
            // TO DO: Check for path traversal attack!
            String path = StringUtils.stripEnd(exchange.getRequestURI().getPath(), "/");
            path = StringUtils.stripStart(path, "/");
            try {
                String content = cache.get(path);
                if (content == null) {
                    InputStream is;
                    if (path.startsWith("custom/")) {
                        String cPath = path.substring(7);
                        is = new URL(appContext.prefs.customURLSource.get()+cPath).openStream();
                    } else {
                        is = getClass().getResourceAsStream(path);
                    }

                    if (is == null) {
                        sendResponse(exchange, 404, "404 (Not Found)\n");
                        return;
                    } else {
                        content = IOUtils.toString(is);
                        if (!path.startsWith("custom/_nc_"))
                            cache.put(path, content);
                    }
                }
                
                String type = getMimeType(StringUtils.substringAfterLast(path, "."));
                if (type.equalsIgnoreCase("text/html")) {
                    MessageTemplate mt = new MessageTemplate(content);
                    content = mt.getMessage(appContext, null);
                }

                exchange.getResponseHeaders().add("Content-Type", type);
                sendResponse(exchange, 200, content);
            } catch (IOException ex) {
                Tesla.logger.severe("Error reading requested file: " + ex.getMessage());
                sendResponse(exchange, 404, "404 (Not Found)\n");
            }
        }
    };
    

/*------------------------------------------------------------------------------
 *
 * PRIVATE - Utility Methods
 * 
 *----------------------------------------------------------------------------*/

    private BasicAuthenticator authenticator = new BasicAuthenticator("VisibleTesla") {
        @Override public boolean checkCredentials(String user, String pwd) {
            if (!user.equals("VT")) return false;
            if (appContext.restEncPW == null || appContext.restSalt == null) return false;
            return pwUtils.authenticate(pwd, appContext.restEncPW, appContext.restSalt);
        }
    };
    
    private void sendResponse(HttpExchange exchange, int code, String response) throws IOException {
        exchange.sendResponseHeaders(code, response.length());
        OutputStream os = exchange.getResponseBody();
        os.write(response.getBytes());
        os.close();
    }
    
    private String getMimeType(String type) {
        if (type != null) {
            switch (type) {
                case "css": return "text/css";
                case "htm":
                case "html": return "text/html";
                case "js": return "application/javascript";
                case "png": return "image/png";
                case "gif": return "image/gif";
                case "jpg":
                case "jpeg": return "image/jpeg";
            }
        }
        return "text/plain";
    }
}