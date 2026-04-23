// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package qupath.ext.braian.config;

import java.util.List;

public class ChannelDetectionsConfig {
    private String name;
    private WatershedCellDetectionConfig parameters = new WatershedCellDetectionConfig();

    /**
     * 1-based input channel index to use when the display name does not uniquely identify the source.
     */
    private int inputChannelID = 1;

    /**
     * Whether cell detection should run for this channel.
     */
    private boolean enableCellDetection = true;

    private List<ChannelClassifierConfig> classifiers = List.of(); // maps classifier name to annotation names

    public int getInputChannelID() {
        return inputChannelID;
    }

    public void setInputChannelID(int inputChannelID) {
        this.inputChannelID = inputChannelID;
    }

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

    public boolean isEnableCellDetection() {
        return enableCellDetection;
    }

    public void setEnableCellDetection(boolean enableCellDetection) {
        this.enableCellDetection = enableCellDetection;
    }
}
