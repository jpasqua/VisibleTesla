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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import static org.noroomattheinn.tesla.Tesla.logger;
import org.noroomattheinn.utils.PWUtils;
import org.noroomattheinn.utils.Utils;
import org.noroomattheinn.visibletesla.MessageTemplate;
import org.noroomattheinn.visibletesla.AppContext;
import org.noroomattheinn.utils.LRUMap;
import org.noroomattheinn.visibletesla.AppMode;
import org.noroomattheinn.visibletesla.ThreadManager;

/**
 * RESTServer: Provide minimal external services.
 * 
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class RESTServer implements ThreadManager.Stoppable {
    
/*------------------------------------------------------------------------------
 *
 * Constants and Enums
 * 
 *----------------------------------------------------------------------------*/

    private static final Map<String,AppMode.Mode> toAppMode = 
            Utils.newHashMap("sleep", AppMode.Mode.AllowSleeping,
                             "wakeup", AppMode.Mode.StayAwake,
                             "produce", AppMode.Mode.StayAwake);

/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private final AppContext ac;
    private HttpServer server;
    private PWUtils pwUtils = new PWUtils();
    private byte[] encPW, salt;

/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public RESTServer(AppContext ac) {
        this.ac = ac;
        server = null;
        ac.tm.addStoppable((ThreadManager.Stoppable)this);
    }

    public synchronized void launch() {
        if (!ac.prefs.enableRest.get()) {
            logger.info("REST Services are disabled");
            return;
        }
        int restPort = ac.prefs.restPort.get();
        try {
            server = HttpServer.create(new InetSocketAddress(restPort), 0);
            
            HttpContext cc;
            cc = server.createContext("/v1/action/activity", activityRequest);
            cc.setAuthenticator(authenticator);
            cc = server.createContext("/v1/action/info", infoRequest);
            cc.setAuthenticator(authenticator);
            cc = server.createContext("/", staticPageRequest);
            cc.setAuthenticator(authenticator);

            server.setExecutor(null); // creates a default executor
            server.start();
        } catch (IOException ex) {
            logger.severe("Unable to start RESTServer: " + ex.getMessage());
        }
    }

    @Override public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }
    
    /**
     * Set the password used by the RESTServer. If no password is supplied, 
     * a random one will be chosen meaning there is effectively no access to
     * the server.
     * @param   pw  The new password
     * @return  An external representation of the salted password that can be
     *          stored safely in a data file.
     */
    public String setPW(String pw) {
        if (pw == null || pw.isEmpty()) { // Choose a random value!
            pw = String.valueOf(Math.floor(Math.random()*100000));   
        }
        salt = pwUtils.generateSalt();
        encPW = pwUtils.getEncryptedPassword(pw, salt);
        return pwUtils.externalRep(salt, encPW);
    }

    /**
     * Initialize the password and salt from previously generated values.
     * @param externalForm  An external representation of the password and
     *                      salt that was previously returned by an invocation
     *                      of setPW()
     */
    public void internalizePW(String externalForm) {
        // Break down the external representation into the salt and password
        List<byte[]> internalForm = (new PWUtils()).internalRep(externalForm);
        salt = internalForm.get(0);
        encPW = internalForm.get(1);
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
            AppMode.Mode requestedMode = toAppMode.get(mode);
            if (requestedMode == null) {
                logger.warning("Unknown app mode: " + mode + "\n");
                sendResponse(exchange, 400, "Unknown app mode");
                return;
            }
            logger.info("Requested app mode: " + mode);
            if (requestedMode == AppMode.Mode.AllowSleeping) ac.appMode.allowSleeping();
            else ac.appMode.stayAwake();

            sendResponse(exchange, 200,  "Requested mode: " + mode + "\n");
        }
    };

    private HttpHandler infoRequest = new HttpHandler() {
        @Override public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equals("GET")) {
                sendResponse(exchange, 400, "GET only on info endpoint\n");
                return;
            }
            String path = StringUtils.stripEnd(exchange.getRequestURI().getPath(), "/");
            String infoType = StringUtils.substringAfterLast(path, "/");
            if (infoType.equals("info")) {
                sendResponse(exchange, 403, "403 (Forbidden)\n");
                return;
            }
            logger.info("Requested info type: " + infoType);
            String response;
            switch (infoType) {
                case "car_state":
                    response = CarInfo.carStateAsJSON(ac);
                    break;
                case "car_details":
                    response = CarInfo.carDetailsAsJSON(ac);
                    break;
                case "inactivity_mode":
                    response = String.format("{ \"mode\": \"%s\" }", ac.appMode);
                    break;
                case "dbg_sar":
                    Map<String,String> params = getParams(exchange.getRequestURI().getQuery());
                    response = params.get("p1");
                    ac.schedulerActivity.set(response == null ? "DBG_SAR" : response);
                    break;
                default:
                    logger.warning("Unknown info request: " + infoType + "\n");
                    sendResponse(exchange, 400, "Unknown info request " + infoType);
                    return;
            }
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            sendResponse(exchange, 200,  response);
        }
    };

    private Map<String, String> getParams(String query) {
        Map<String, String> params = new HashMap<>();
        if (query != null) {
            for (String param : query.split("&")) {
                String pair[] = param.split("=");
                if (pair.length > 1) {
                    params.put(pair[0], pair[1]);
                } else {
                    params.put(pair[0], "");
                }
            }
        }
        return params;
    }
    
    private HttpHandler staticPageRequest = new HttpHandler() {
        LRUMap<String,byte[]> cache = new LRUMap<>(10);
        @Override public void handle(HttpExchange exchange) throws IOException {
            // TO DO: Check for path traversal attack!
            String path = StringUtils.stripEnd(exchange.getRequestURI().getPath(), "/");
            path = StringUtils.stripStart(path, "/");
            try {
                byte[] content = cache.get(path);
                if (content == null) {
                    InputStream is;
                    if (path.startsWith("custom/")) {
                        String cPath = path.substring(7);
                        is = new URL(ac.prefs.customURLSource.get()+cPath).openStream();
                    } else if (path.startsWith("TeslaResources/")) {
                        path = "org/noroomattheinn/" + path;
                        is = getClass().getClassLoader().getResourceAsStream(path);
                    } else {
                        is = getClass().getResourceAsStream(path);
                    }

                    if (is == null) {
                        sendResponse(exchange, 404, "404 (Not Found)\n");
                        return;
                    } else {
                        content = IOUtils.toByteArray(is);
                        if (!path.startsWith("custom/_nc_"))
                            cache.put(path, content);
                    }
                }
                
                String type = getMimeType(StringUtils.substringAfterLast(path, "."));
                if (type.equalsIgnoreCase("text/html")) {
                    MessageTemplate mt = new MessageTemplate(ac, new String(content, "UTF-8"));
                    content = mt.getMessage(null).getBytes();
                } else if (cacheOnClient(type)) {
                    exchange.getResponseHeaders().add("Cache-Control", "max-age=2592000");
                }

                exchange.getResponseHeaders().add("Content-Type", type);
                sendResponse(exchange, 200, content);
            } catch (IOException ex) {
                logger.severe("Error reading requested file: " + ex.getMessage());
                sendResponse(exchange, 404, "404 (Not Found)\n");
            }
        }
    };
    

/*------------------------------------------------------------------------------
 *
 * PRIVATE - Utility Methods
 * 
 *----------------------------------------------------------------------------*/

    private boolean cacheOnClient(String type) {
        return (!type.equals("text/html"));
    }
    
    private BasicAuthenticator authenticator = new BasicAuthenticator("VisibleTesla") {
        @Override public boolean checkCredentials(String user, String pwd) {
            if (!user.equals("VT")) return false;
            if (encPW == null || salt == null) return false;
            return pwUtils.authenticate(pwd, encPW, salt);
        }
    };
    
    private void sendResponse(HttpExchange exchange, int code, String response) throws IOException {
        exchange.sendResponseHeaders(code, response.length());
        OutputStream os = exchange.getResponseBody();
        os.write(response.getBytes());
        os.close();
    }
    
    private void sendResponse(HttpExchange exchange, int code, byte[] response) throws IOException {
        exchange.sendResponseHeaders(code, response.length);
        OutputStream os = exchange.getResponseBody();
        os.write(response);
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