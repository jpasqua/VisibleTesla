/*
 * DeviationTrigger.java - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Aug 10, 2014
 */
package org.noroomattheinn.visibletesla.trigger;

/**
 * DeviationTrigger: Determines whether a sample deviates from a historical
 * baseline.
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class DeviationTrigger {
    private static final double NoAverageEstablished = Double.MIN_VALUE;
    private double threshold;
    private double runningAverage, baseline;
    private long firstTime, lastTime, firstTriggerTime;
    private long baselineInterval;
    private long sampleCount;
    
    /**
     * An object that monitors whether a sample deviates from an established
     * baseline value by more than a certain percentage.
     * @param threshold         Percentage change that will trigger a deviation.
     *                          Expressed as a value from 0 to 1.
     * @param baselineInterval  The time (in millis) needed to establish a baseline
     *                          average. Also the time required for a deviation
     *                          to be sustained before being reported.
     */
    public DeviationTrigger(double threshold, long baselineInterval) {
        this.threshold = threshold;
        this.baselineInterval = baselineInterval;
        resetBaseline();
    }

    /**
     * Take a sample and determine whether it is out of the range of the current
     * baseline. If no baseline has yet been established, use this sample to help
     * build it.
     * @param sample    The current sample of some variable
     * @return          true: if this sample deviates significantly from an
     *                  established baseline
     *                  false: The sample doesn't deviate significantly or the
     *                  baseline is still being formed.
     */
    public boolean evalPredicate(double sample) {
        if (sample <= 0) {
            resetBaseline();
            return false;
        }

        if (runningAverage == NoAverageEstablished) {
            firstTime = lastTime = System.currentTimeMillis();
            runningAverage = sample;
            sampleCount = 1;
            return false;
        }

        if (lastTime - firstTime <= baselineInterval) {	// Keep accumulating
            runningAverage = ((runningAverage * sampleCount) + sample) / (++sampleCount);
            lastTime = System.currentTimeMillis();
        } else {	// Test whether we're in range
            if (sample <= runningAverage * (1 - threshold)) {	// The sample is too far from the average 
                if (firstTriggerTime == -1) {
                    firstTriggerTime = System.currentTimeMillis();
                } else if (System.currentTimeMillis() - firstTriggerTime > baselineInterval) {
                    baseline = runningAverage;
                    resetBaseline();    // Start a new baseline
                    return true;
                }
            } else {	// The sample is in range of the average
                firstTriggerTime = -1;
            }
        }
        return false;
    }

    /**
     * Get the baseline that was established and lead to a trigger event. This
     * should only be called after handleSample() returns true and before any
     * new handleSample() calls are made.
     * @return The baseline value
     */
    public double getBaseline() { return baseline; }
    
    private void resetBaseline() {
        runningAverage = NoAverageEstablished;
        firstTime = lastTime = firstTriggerTime = -1L;
        sampleCount = 0;
    }
}
