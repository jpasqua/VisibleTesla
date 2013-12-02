/*
 * StatsPublisher - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Nov 29, 2013
 */

package org.noroomattheinn.visibletesla.stats;

import java.util.List;

/**
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public interface StatsPublisher {
    public abstract List<Stat.Sample> valuesForRange(String type, long startX, long endX);
    
    public List<String> getStatTypes();
}
