//
// SPDX-License-Identifier: AGPL-3.0-or-later

package qupath.ext.braian.config;

import java.util.List;

/**
 * Configuration entry describing a pixel classifier to run and how to record its output.
 */
public class PixelClassifierConfig {
    /** Name of the pixel classifier to run (typically a QuPath {@code .json} classifier name or identifier). */
    private String classifierName;

    /** Optional measurement identifier to store/export derived results. */
    private String measurementId;

    /** Optional list of atlas region names to restrict where the classifier is run. */
    private List<String> regionFilter = List.of();

    public String getClassifierName() {
        return classifierName;
    }

    public void setClassifierName(String classifierName) {
        this.classifierName = classifierName;
    }

    public String getMeasurementId() {
        return measurementId;
    }

    public void setMeasurementId(String measurementId) {
        this.measurementId = measurementId;
    }

    public List<String> getRegionFilter() {
        return regionFilter;
    }

    public void setRegionFilter(List<String> regionFilter) {
        this.regionFilter = regionFilter == null ? List.of() : regionFilter;
    }
}
