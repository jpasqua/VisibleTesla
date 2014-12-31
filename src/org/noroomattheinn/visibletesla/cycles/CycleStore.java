/*
 * CycleStore.java - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Oct 23, 2014
 */
package org.noroomattheinn.visibletesla.cycles;

import com.google.common.collect.Range;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import static org.noroomattheinn.tesla.Tesla.logger;
import org.noroomattheinn.visibletesla.AppContext;
import org.noroomattheinn.visibletesla.ThreadManager;
import org.noroomattheinn.visibletesla.VTVehicle;

/**
 * CycleStore: Bases class for classes that provide persistent storage for 
 * various types of Cycle information.
 * 
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public abstract class CycleStore<C extends BaseCycle>
    implements ThreadManager.Stoppable {
/*------------------------------------------------------------------------------
 *
 * Internal  State
 * 
 *----------------------------------------------------------------------------*/
    
    protected final AppContext ac;
    protected final File cycleFile;
    protected final PrintStream cycleWriter;
    protected final Class<C> theClass;
    protected final String cycleType;
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    public CycleStore(AppContext appContext, String cycleType, Class<C> theClass)
            throws FileNotFoundException {
        this.ac = appContext;
        this.theClass = theClass;
        this.cycleType = cycleType;
        this.cycleFile = new File(
                ac.appFileFolder(),
                VTVehicle.get().getVehicle().getVIN()+"." + cycleType + ".json");
        
        FileOutputStream fos = new FileOutputStream(cycleFile, true);
        cycleWriter = new PrintStream(fos);
        ThreadManager.get().addStoppable((ThreadManager.Stoppable)this);
    }
    
    @Override public void stop() { cycleWriter.close(); }
    
    public List<C> getCycles(Range<Long> period) {
        List<C> cycles = new ArrayList<>();
        try {
            BufferedReader r = new BufferedReader(new FileReader(cycleFile));

            try {
                String entry;
                while ((entry = r.readLine()) != null) {
                    C cycle = ChargeCycle.fromJSON(entry, theClass);
                    if (period == null || period.contains(cycle.startTime)) {
                        cycles.add(cycle);
                    }
                }
            } catch (IOException ex) {
                logger.warning("Problem reading " + cycleType + " Cycle data: " + ex);
            }
        } catch (FileNotFoundException ex) {
            logger.warning("Could not open " + cycleType + " file: " + ex);
        }
        return cycles;
    }
}

