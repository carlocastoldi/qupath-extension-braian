// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package qupath.ext.braian.config;

import java.util.List;

public class ChannelDetectionsConfig {
    private String name;
    private WatershedCellDetectionConfig parameters;
    private List<ChannelClassifierConfig> classifiers; // maps classifier name to annotation names

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public WatershedCellDetectionConfig getParameters() {
        return parameters;
    }

    public void setParameters(WatershedCellDetectionConfig parameters) {
        this.parameters = parameters;
    }

    public List<ChannelClassifierConfig> getClassifiers() {
        return classifiers;
    }

    public void setClassifiers(List<ChannelClassifierConfig> classifiers) {
        this.classifiers = classifiers;
    }
}
