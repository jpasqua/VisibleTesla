/*
 * CommandIssuer.java - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Aug 8, 2014
 */
package org.noroomattheinn.visibletesla;

import java.util.concurrent.Callable;
import javafx.scene.control.ProgressIndicator;
import org.noroomattheinn.tesla.Result;
import org.noroomattheinn.tesla.Tesla;

/**
 * CommandIssuer: Execute commands in the background.
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class CommandIssuer extends Executor<CommandIssuer.Request> {
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public CommandIssuer(AppContext ac) {
        super(ac, "CommandIssuer");
    }
    
    public void issueCommand(Callable<Result> command, boolean retry, ProgressIndicator pi) {
        super.produce(new Request(command, retry, pi));
    }
    
/*------------------------------------------------------------------------------
 *
 * Internal Methods - Some declared protected since they implement interfaces
 * 
 *----------------------------------------------------------------------------*/

    @Override protected Request newRequest(Request basis, boolean allowRetry) {
        return new Request(basis.command, allowRetry, basis.pi);
    }

    @Override protected boolean execRequest(Request r) throws Exception {
        Result result = r.command.call();
        if (result.success) { return true; }
        Tesla.logger.warning("Failed command (" + r.command + "): " + result.explanation);
        return false;
    }
    
    public static class Request extends Executor.Request {
        public final Callable<Result> command;
        
        Request(Callable<Result> command, boolean retry, ProgressIndicator pi) {
            super(retry, 20 * 2000, pi);
            this.command = command;
        }
    }
}