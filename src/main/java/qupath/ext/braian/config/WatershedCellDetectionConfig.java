// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package qupath.ext.braian.config;

import qupath.ext.braian.ChannelHistogram;
import qupath.ext.braian.ImageChannelTools;

import java.io.IOException;
import java.util.*;
import java.util.stream.IntStream;

import static qupath.ext.braian.BraiAnExtension.getLogger;

/**
 * Configuration model for QuPath watershed cell detection parameters.
 */
public class WatershedCellDetectionConfig {
    /**
     * Computes an automatic threshold for a channel using histogram peak detection.
     *
     * @param channel image channel wrapper
     * @param params histogram-based threshold configuration
     * @return automatically selected threshold value
     */
    public static int findThreshold(ImageChannelTools channel, AutoThresholdParmameters params) {
        int windowSize = params.getSmoothWindowSize();
        ChannelHistogram histogram;
        try {
            histogram = channel.getHistogram(params.getResolutionLevel());
        } catch (IOException ignored) {
            throw new RuntimeException("Could not build the channel histogram of '"+channel.getName()+"' to automatically determine the threshold!");
        }
        int[] peaks = histogram.findHistogramPeaks(windowSize, params.getPeakProminence());
        getLogger().debug("'{}' histogram peaks (invalid peaks included): {}", channel.getName(), Arrays.toString(peaks));
        int threshold =  getNthValidPeak(histogram, peaks, params.getnPeak(), windowSize);
        getLogger().info("'{}' automatic threshold: {}", channel.getName(), threshold);
        return threshold;
        // if(peaks.length <= params.getnPeak())
        //     throw new RuntimeException("Could not automatically determine the channel threshold of '"+detectionImage+"' from its histogram!");
        // return peaks[params.getnPeak()];
    }

    /**
     * Selects the n-th valid peak while ignoring edge artifacts introduced by smoothing.
     *
     * @param histogram source histogram
     * @param peaks detected peak indices
     * @param nth zero-based peak index to select
     * @param windowSize smoothing window size used before peak detection
     * @return the n-th peak of the histogram excluding the peaks that are not trust-worthy (i.e. those at the beginning and end of the smoothed histogram)
     */
    private static int getNthValidPeak(ChannelHistogram histogram, int[] peaks, int nth, int windowSize) {
        int max = histogram.getMaxValue()-windowSize;
        OptionalInt firstValid = IntStream.range(0, peaks.length).filter(i -> peaks[i] >= windowSize && peaks[i] < max).findFirst();
        int shiftedNth = nth + firstValid.orElseGet(() -> 0);
        String msg = "Could not automatically determine the channel threshold of '"+histogram.getChannelName()+"' from its histogram!";
        if(firstValid.isEmpty())
            throw new RuntimeException(msg+" No peak was found within the trust-worthy interval");
        if (peaks.length <= shiftedNth)
            throw new RuntimeException(msg+" The histogram doesn't have n peaks in [windowSize:end]");
        if (peaks[shiftedNth] >= max)
            throw new RuntimeException(msg+" There is at least one valid peak, but not n valid peaks");
        return peaks[shiftedNth];
    }

    private String detectionImage = null; // it is not meant to be in the config file. It will stay null until build() is called
    private double requestedPixelSizeMicrons = 0.5;
    private double backgroundRadiusMicrons = 8.0;
    private boolean backgroundByReconstruction = true; // new from QuPath 0.4.0. Before it was always set to "true"
    private double medianRadiusMicrons = 0.0;
    private double sigmaMicrons = 1.5;
    private double minAreaMicrons = 10.0;
    private double maxAreaMicrons = 400.0;
    private double threshold = 100.0;
    private AutoThresholdParmameters histogramThreshold = null;
    private boolean watershedPostProcess = true;
    private double cellExpansionMicrons = 5.0;
    private boolean includeNuclei = false;
    private boolean smoothBoundaries = true;
    private boolean makeMeasurements = true;

    /**
     * Builds the parameter map consumed by QuPath watershed detection command.
     *
     * @param channel channel used for detection and optional auto-thresholding
     * @return immutable-style map of parameter names and values
     */
    public Map<String,?> build(ImageChannelTools channel) {
        this.setDetectionImage(channel.getName());
        if (this.histogramThreshold != null)
            this.setThreshold(findThreshold(channel, this.histogramThreshold));

        return Arrays.stream(WatershedCellDetectionConfig.class.getDeclaredFields())
                .filter(f -> !f.isSynthetic() && !f.getName().equals("histogramThreshold"))
                .reduce(
                        new HashMap<>(),
                        (map, field) -> {
                            try {
                                map.put(field.getName(), field.get(this));
                            } catch (IllegalAccessException e) {
                                throw new RuntimeException("This should never happen as it gets the field from the class itself!");
                            }
                            return map;
                        },
                        (map1, map2) -> {
                            map1.putAll(map2);
                            return map1;
                        }
                );
    }

    public String getDetectionImage() {
        return detectionImage;
    }

    public double getRequestedPixelSizeMicrons() {
        return requestedPixelSizeMicrons;
    }

    public double getBackgroundRadiusMicrons() {
        return backgroundRadiusMicrons;
    }

    public boolean isBackgroundByReconstruction() {
        return backgroundByReconstruction;
    }

    public double getMedianRadiusMicrons() {
        return medianRadiusMicrons;
    }

    public double getSigmaMicrons() {
        return sigmaMicrons;
    }

    public double getMinAreaMicrons() {
        return minAreaMicrons;
    }

    public double getMaxAreaMicrons() {
        return maxAreaMicrons;
    }

    public double getThreshold() {
        return threshold;
    }

    public boolean isWatershedPostProcess() {
        return watershedPostProcess;
    }

    public double getCellExpansionMicrons() {
        return cellExpansionMicrons;
    }

    public boolean isIncludeNuclei() {
        return includeNuclei;
    }

    public boolean isSmoothBoundaries() {
        return smoothBoundaries;
    }

    public boolean isMakeMeasurements() {
        return makeMeasurements;
    }

    public void setDetectionImage(String detectionImage) {
        this.detectionImage = detectionImage;
    }

    public void setRequestedPixelSizeMicrons(double requestedPixelSizeMicrons) {
        this.requestedPixelSizeMicrons = requestedPixelSizeMicrons;
    }

    public void setBackgroundRadiusMicrons(double backgroundRadiusMicrons) {
        this.backgroundRadiusMicrons = backgroundRadiusMicrons;
    }

    public void setBackgroundByReconstruction(boolean backgroundByReconstruction) {
        this.backgroundByReconstruction = backgroundByReconstruction;
    }

    public void setMedianRadiusMicrons(double medianRadiusMicrons) {
        this.medianRadiusMicrons = medianRadiusMicrons;
    }

    public void setSigmaMicrons(double sigmaMicrons) {
        this.sigmaMicrons = sigmaMicrons;
    }

    public void setMinAreaMicrons(double minAreaMicrons) {
        this.minAreaMicrons = minAreaMicrons;
    }

    public void setMaxAreaMicrons(double maxAreaMicrons) {
        this.maxAreaMicrons = maxAreaMicrons;
    }

    public void setThreshold(double threshold) {
        this.threshold = threshold;
    }

    public void setWatershedPostProcess(boolean watershedPostProcess) {
        this.watershedPostProcess = watershedPostProcess;
    }

    public void setCellExpansionMicrons(double cellExpansionMicrons) {
        this.cellExpansionMicrons = cellExpansionMicrons;
    }

    public void setIncludeNuclei(boolean includeNuclei) {
        this.includeNuclei = includeNuclei;
    }

    public void setSmoothBoundaries(boolean smoothBoundaries) {
        this.smoothBoundaries = smoothBoundaries;
    }

    public void setMakeMeasurements(boolean makeMeasurements) {
        this.makeMeasurements = makeMeasurements;
    }

    public AutoThresholdParmameters getHistogramThreshold() {
        return histogramThreshold;
    }

    public void setHistogramThreshold(AutoThresholdParmameters histogramThreshold) {
        this.histogramThreshold = histogramThreshold;
    }
}
