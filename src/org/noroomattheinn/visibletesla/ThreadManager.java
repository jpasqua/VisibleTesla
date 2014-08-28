/*
 * ThreadManager - Copyright(c) 2013, 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Aug 16, 2014
 */

package org.noroomattheinn.visibletesla;

import static java.lang.Thread.State.BLOCKED;
import static java.lang.Thread.State.NEW;
import static java.lang.Thread.State.RUNNABLE;
import static java.lang.Thread.State.TERMINATED;
import static java.lang.Thread.State.TIMED_WAITING;
import static java.lang.Thread.State.WAITING;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javafx.beans.property.BooleanProperty;
import org.noroomattheinn.utils.Utils;

/**
 * ThreadManager: Manage (start, stop, cleanup) threads used by the app
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class ThreadManager {
    
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private int threadID = 0;
    
    private final BooleanProperty   shuttingDown;
    private final ArrayList<Thread> threads = new ArrayList<>();
    private final List<Stoppable>   stopList;
    
    public ThreadManager(final BooleanProperty shuttingDown) {
        this.shuttingDown = shuttingDown;
        this.stopList = new ArrayList<>();
    }
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public interface Stoppable { public void stop(); }
    
    public synchronized Thread launch(Runnable r, String name) {
        if (shuttingDown.get())
            return null;
        Thread t = new Thread(r);
        t.setName(name == null ? ("00 VT - " + threadID++) : name);
        t.setDaemon(true);
        t.start();
        threads.add(t);

        // Clean out any old terminated threads...
        Iterator<Thread> i = threads.iterator();
        while (i.hasNext()) {
            Thread cur = i.next();
            if (cur.getState() == Thread.State.TERMINATED) {
                i.remove();
            }
        }

        return t;
    }

    public void addStoppable(Stoppable s) { stopList.add(s); }
    
    public synchronized void shutDown() {
        shuttingDown.set(true);
        for (Stoppable s : stopList) { s.stop(); }

        int nActive;
        do {
            nActive = 0;
            for (Thread t : threads) {
                Thread.State state = t.getState();
                switch (state) {
                    case NEW:
                    case RUNNABLE:
                        nActive++;
                        break;

                    case TERMINATED:
                        break;

                    case BLOCKED:
                    case TIMED_WAITING:
                    case WAITING:
                        nActive++;
                        t.interrupt();
                        Utils.yieldFor(100);
                        break;

                    default:
                        break;
                }
            }
        } while (nActive > 0);
    }

}
