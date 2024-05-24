// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package qupath.ext.braian.config;

public class AutoThresholdParmameters {
    private int resolutionLevel = 4;
    private int smoothWindowSize = 15;
    private double peakProminence = 100;
    private int nPeak = 1;

    public int getResolutionLevel() {
        return resolutionLevel;
    }

    public void setResolutionLevel(int resolutionLevel) {
        assert resolutionLevel >= 0;
        this.resolutionLevel = resolutionLevel;
    }

    public int getSmoothWindowSize() {
        return smoothWindowSize;
    }

    public void setSmoothWindowSize(int smoothWindowSize) {
        assert smoothWindowSize > 0;
        this.smoothWindowSize = smoothWindowSize;
    }

    public double getPeakProminence() {
        return peakProminence;
    }

    public void setPeakProminence(double peakProminence) {
        assert peakProminence > 0;
        this.peakProminence = peakProminence;
    }

    public int getnPeak() {
        return nPeak;
    }

    public void setnPeak(int nPeak) {
        assert nPeak > 0;
        this.nPeak = nPeak-1;
    }
}
