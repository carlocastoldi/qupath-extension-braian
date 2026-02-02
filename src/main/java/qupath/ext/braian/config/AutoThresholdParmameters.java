// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package qupath.ext.braian.config;

/**
 * Parameters used for histogram-based auto-thresholding.
 * <p>
 * These settings control how {@code ChannelHistogram.findHistogramPeaks(...)} is interpreted when
 * selecting a threshold from the histogram.
 */
public class AutoThresholdParmameters {
    private int resolutionLevel = 4;
    private int smoothWindowSize = 15;
    private double peakProminence = 100;
    private int nPeak = 0;

    /**
     * @return the resolution level used to compute the histogram
     */
    public int getResolutionLevel() {
        return resolutionLevel;
    }

    /**
     * @param resolutionLevel the resolution level used to compute the histogram
     */
    public void setResolutionLevel(int resolutionLevel) {
        assert resolutionLevel >= 0;
        this.resolutionLevel = resolutionLevel;
    }

    /**
     * @return the smoothing window size used when searching histogram peaks
     */
    public int getSmoothWindowSize() {
        return smoothWindowSize;
    }

    /**
     * @param smoothWindowSize the smoothing window size used when searching histogram peaks
     */
    public void setSmoothWindowSize(int smoothWindowSize) {
        assert smoothWindowSize > 0;
        this.smoothWindowSize = smoothWindowSize;
    }

    /**
     * @return the minimum peak prominence required to keep a histogram peak
     */
    public double getPeakProminence() {
        return peakProminence;
    }

    /**
     * @param peakProminence the minimum peak prominence required to keep a histogram peak
     */
    public void setPeakProminence(double peakProminence) {
        assert peakProminence > 0;
        this.peakProminence = peakProminence;
    }

    /**
     * @return the 0-based peak index used when selecting a threshold
     */
    public int getnPeak() {
        return nPeak;
    }

    /**
     * Sets which peak should be selected when multiple peaks are found.
     * <p>
     * The value provided by users is 1-based; it is converted internally to 0-based.
     *
     * @param nPeak the peak index (1-based)
     */
    public void setnPeak(int nPeak) {
        assert nPeak > 0;
        this.nPeak = nPeak-1;
    }
}
