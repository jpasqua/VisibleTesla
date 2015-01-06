/*
 * CycleStore.java - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Oct 23, 2014
 */
package org.noroomattheinn.visibletesla.data;

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
import org.noroomattheinn.utils.ThreadManager;
import org.noroomattheinn.visibletesla.vehicle.VTVehicle;

/**
 * CycleStore: Bases class for classes that provide persistent storage for 
 * various types of Cycle information.
 * 
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
abstract class CycleStore<C extends BaseCycle>
    implements ThreadManager.Stoppable {
/*------------------------------------------------------------------------------
 *
 * Internal  State
 * 
 *----------------------------------------------------------------------------*/
    
    protected final VTVehicle vtVehicle;
    protected final File cycleFile;
    protected final PrintStream cycleWriter;
    protected final Class<C> theClass;
    protected final String cycleType;
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    CycleStore(String cycleType, Class<C> theClass, File container, VTVehicle v)
            throws FileNotFoundException {
        this.theClass = theClass;
        this.vtVehicle = v;
        this.cycleType = cycleType;
        this.cycleFile = new File(
                container, v.getVehicle().getVIN()+"." + cycleType + ".json");
        
        FileOutputStream fos = new FileOutputStream(cycleFile, true);
        cycleWriter = new PrintStream(fos);
        ThreadManager.get().addStoppable((ThreadManager.Stoppable)this);
    }
    
    @Override public void stop() { cycleWriter.close(); }
    
    List<C> getCycles(Range<Long> period) {
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

