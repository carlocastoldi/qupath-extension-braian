// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package qupath.ext.braian.config;

/**
 * Configuration for automatic histogram-based threshold selection.
 */
public class AutoThresholdParmameters {
    private int resolutionLevel = 4;
    private int smoothWindowSize = 15;
    private double peakProminence = 100;
    private int nPeak = 0;

    /**
     * @return image resolution level used to build the histogram
     */
    public int getResolutionLevel() {
        return resolutionLevel;
    }

    /**
     * @param resolutionLevel image resolution level used to build the histogram
     */
    public void setResolutionLevel(int resolutionLevel) {
        assert resolutionLevel >= 0;
        this.resolutionLevel = resolutionLevel;
    }

    /**
     * @return smoothing window size for histogram peak detection
     */
    public int getSmoothWindowSize() {
        return smoothWindowSize;
    }

    /**
     * @param smoothWindowSize smoothing window size for histogram peak detection
     */
    public void setSmoothWindowSize(int smoothWindowSize) {
        assert smoothWindowSize > 0;
        this.smoothWindowSize = smoothWindowSize;
    }

    /**
     * @return minimum prominence required for detected peaks
     */
    public double getPeakProminence() {
        return peakProminence;
    }

    /**
     * @param peakProminence minimum prominence required for detected peaks
     */
    public void setPeakProminence(double peakProminence) {
        assert peakProminence > 0;
        this.peakProminence = peakProminence;
    }

    /**
     * @return selected peak index (0-based)
     */
    public int getnPeak() {
        return nPeak;
    }

    /**
     * @param nPeak selected peak index (1-based in YAML, converted to 0-based internally)
     */
    public void setnPeak(int nPeak) {
        assert nPeak > 0;
        this.nPeak = nPeak-1;
    }
}
