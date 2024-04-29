// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package qupath.ext.braian;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class WatershedCellDetectionParameters {
    private String detectionImage;
    private double requestedPixelSizeMicrons = 0.5;
    private double backgroundRadiusMicrons = 8.0;
    private boolean backgroundByReconstruction = true; // new from QuPath 0.4.0. Before it was always set to "true"
    private double medianRadiusMicrons = 0.0;
    private double sigmaMicrons = 1.5;
    private double minAreaMicrons = 10.0;
    private double maxAreaMicrons = 400.0;
    private double threshold = -1.0; //100.0
    private boolean watershedPostProcess = true;
    private double cellExpansionMicrons = 5.0;
    private boolean includeNuclei = false;
    private boolean smoothBoundaries = true;
    private boolean makeMeasurements = true;

    public Map<String,?> toMap() {
        return Arrays.stream(WatershedCellDetectionParameters.class.getDeclaredFields())
                .filter(f -> !f.isSynthetic())
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
}