// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package qupath.ext.braian.config;

import java.util.List;

public class ChannelDetectionsConfig {
    private String name;
    private WatershedCellDetectionConfig parameters = new WatershedCellDetectionConfig();
    private List<ChannelClassifierConfig> classifiers = List.of(); // maps classifier name to annotation names
    private List<PixelClassifierConfig> pixelClassifiers = List.of();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        this.parameters.setDetectionImage(name);
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
        for (ChannelClassifierConfig classifier: classifiers) {
            classifier.setChannel(this.name);
        }
        this.classifiers = classifiers;
    }

    public List<PixelClassifierConfig> getPixelClassifiers() {
        return pixelClassifiers;
    }

    public void setPixelClassifiers(List<PixelClassifierConfig> pixelClassifiers) {
        this.pixelClassifiers = pixelClassifiers == null ? List.of() : pixelClassifiers;
    }
}
