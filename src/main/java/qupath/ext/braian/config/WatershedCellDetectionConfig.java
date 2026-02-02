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
 * Configuration wrapper for QuPath's watershed cell detection parameters.
 * <p>
 * This class can optionally compute an automatic threshold from a {@link ChannelHistogram} and can build
 * a parameter map compatible with {@code qupath.imagej.detect.cells.WatershedCellDetection}.
 */
public class WatershedCellDetectionConfig {
    /**
     * Computes an automatic threshold from the channel histogram using the provided parameters.
     *
     * @param channel the image channel to analyze
     * @param params parameters controlling histogram peak detection
     * @return the computed threshold value
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
     * @param histogram
     * @param peaks
     * @param nth
     * @param windowSize
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
     * Builds a parameter map compatible with QuPath's watershed cell detection plugin.
     * <p>
     * If {@link #getHistogramThreshold()} is set, this will compute an automatic threshold and override
     * {@link #getThreshold()}.
     *
     * @param channel the image channel to analyze
     * @return a map of parameters to pass to {@code qupath.imagej.detect.cells.WatershedCellDetection}
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

    /**
     * @return the channel name used for detection (not serialized in YAML)
     */
    public String getDetectionImage() {
        return detectionImage;
    }

    /**
     * @return requested pixel size (microns) for analysis
     */
    public double getRequestedPixelSizeMicrons() {
        return requestedPixelSizeMicrons;
    }

    /**
     * @return background radius (microns)
     */
    public double getBackgroundRadiusMicrons() {
        return backgroundRadiusMicrons;
    }

    /**
     * @return true if background subtraction uses reconstruction
     */
    public boolean isBackgroundByReconstruction() {
        return backgroundByReconstruction;
    }

    /**
     * @return median filter radius (microns)
     */
    public double getMedianRadiusMicrons() {
        return medianRadiusMicrons;
    }

    /**
     * @return Gaussian sigma (microns)
     */
    public double getSigmaMicrons() {
        return sigmaMicrons;
    }

    /**
     * @return minimum cell area (square microns)
     */
    public double getMinAreaMicrons() {
        return minAreaMicrons;
    }

    /**
     * @return maximum cell area (square microns)
     */
    public double getMaxAreaMicrons() {
        return maxAreaMicrons;
    }

    /**
     * @return intensity threshold used for detection
     */
    public double getThreshold() {
        return threshold;
    }

    /**
     * @return true to enable watershed post-processing
     */
    public boolean isWatershedPostProcess() {
        return watershedPostProcess;
    }

    /**
     * @return cell expansion (microns)
     */
    public double getCellExpansionMicrons() {
        return cellExpansionMicrons;
    }

    /**
     * @return true to include nuclei objects
     */
    public boolean isIncludeNuclei() {
        return includeNuclei;
    }

    /**
     * @return true to smooth object boundaries
     */
    public boolean isSmoothBoundaries() {
        return smoothBoundaries;
    }

    /**
     * @return true to compute measurements
     */
    public boolean isMakeMeasurements() {
        return makeMeasurements;
    }

    /**
     * @param detectionImage the channel name used for detection
     */
    public void setDetectionImage(String detectionImage) {
        this.detectionImage = detectionImage;
    }

    /**
     * @param requestedPixelSizeMicrons requested pixel size (microns) for analysis
     */
    public void setRequestedPixelSizeMicrons(double requestedPixelSizeMicrons) {
        this.requestedPixelSizeMicrons = requestedPixelSizeMicrons;
    }

    /**
     * @param backgroundRadiusMicrons background radius (microns)
     */
    public void setBackgroundRadiusMicrons(double backgroundRadiusMicrons) {
        this.backgroundRadiusMicrons = backgroundRadiusMicrons;
    }

    /**
     * @param backgroundByReconstruction true to use reconstruction for background subtraction
     */
    public void setBackgroundByReconstruction(boolean backgroundByReconstruction) {
        this.backgroundByReconstruction = backgroundByReconstruction;
    }

    /**
     * @param medianRadiusMicrons median filter radius (microns)
     */
    public void setMedianRadiusMicrons(double medianRadiusMicrons) {
        this.medianRadiusMicrons = medianRadiusMicrons;
    }

    /**
     * @param sigmaMicrons Gaussian sigma (microns)
     */
    public void setSigmaMicrons(double sigmaMicrons) {
        this.sigmaMicrons = sigmaMicrons;
    }

    /**
     * @param minAreaMicrons minimum cell area (square microns)
     */
    public void setMinAreaMicrons(double minAreaMicrons) {
        this.minAreaMicrons = minAreaMicrons;
    }

    /**
     * @param maxAreaMicrons maximum cell area (square microns)
     */
    public void setMaxAreaMicrons(double maxAreaMicrons) {
        this.maxAreaMicrons = maxAreaMicrons;
    }

    /**
     * @param threshold intensity threshold used for detection
     */
    public void setThreshold(double threshold) {
        this.threshold = threshold;
    }

    /**
     * @param watershedPostProcess true to enable watershed post-processing
     */
    public void setWatershedPostProcess(boolean watershedPostProcess) {
        this.watershedPostProcess = watershedPostProcess;
    }

    /**
     * @param cellExpansionMicrons cell expansion (microns)
     */
    public void setCellExpansionMicrons(double cellExpansionMicrons) {
        this.cellExpansionMicrons = cellExpansionMicrons;
    }

    /**
     * @param includeNuclei true to include nuclei objects
     */
    public void setIncludeNuclei(boolean includeNuclei) {
        this.includeNuclei = includeNuclei;
    }

    /**
     * @param smoothBoundaries true to smooth object boundaries
     */
    public void setSmoothBoundaries(boolean smoothBoundaries) {
        this.smoothBoundaries = smoothBoundaries;
    }

    /**
     * @param makeMeasurements true to compute measurements
     */
    public void setMakeMeasurements(boolean makeMeasurements) {
        this.makeMeasurements = makeMeasurements;
    }

    /**
     * @return parameters for histogram-based auto-thresholding; may be null
     */
    public AutoThresholdParmameters getHistogramThreshold() {
        return histogramThreshold;
    }

    /**
     * @param histogramThreshold parameters for histogram-based auto-thresholding; may be null
     */
    public void setHistogramThreshold(AutoThresholdParmameters histogramThreshold) {
        this.histogramThreshold = histogramThreshold;
    }
}
