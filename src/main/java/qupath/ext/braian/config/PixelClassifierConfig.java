// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
// SPDX-FileCopyrightText: 2025 Nash Baughman <nfbaughman@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package qupath.ext.braian.config;

import java.util.List;

/**
 * Configuration for running a pixel classifier and extracting one measurement.
 */
public class PixelClassifierConfig {
    private String classifierName;
    private String measurementId;
    private List<String> regionFilter;

    /**
     * @return the pixel classifier name (as configured in QuPath)
     */
    public String getClassifierName() {
        return classifierName;
    }

    /**
     * @param classifierName the pixel classifier name (as configured in QuPath)
     */
    public void setClassifierName(String classifierName) {
        this.classifierName = classifierName;
    }

    /**
     * @return the measurement identifier to export
     */
    public String getMeasurementId() {
        return measurementId;
    }

    /**
     * @param measurementId the measurement identifier to export
     */
    public void setMeasurementId(String measurementId) {
        this.measurementId = measurementId;
    }

    /**
     * @return optional list of region names to filter the export to; may be null
     */
    public List<String> getRegionFilter() {
        return regionFilter;
    }

    /**
     * @param regionFilter optional list of region names to filter the export to;
     *                     may be null
     */
    public void setRegionFilter(List<String> regionFilter) {
        this.regionFilter = regionFilter;
    }
}
