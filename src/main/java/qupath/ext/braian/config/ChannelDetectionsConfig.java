// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package qupath.ext.braian.config;

import java.util.List;

/**
 * Per-channel configuration for running cell detections and object classifiers.
 */
public class ChannelDetectionsConfig {
    private String name;
    private WatershedCellDetectionConfig parameters = new WatershedCellDetectionConfig();
    private List<ChannelClassifierConfig> classifiers = List.of(); // maps classifier name to annotation names

    /**
     * @return channel name used for detections
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the channel name and syncs the detection image field in watershed parameters.
     *
     * @param name channel name used for detections
     */
    public void setName(String name) {
        this.name = name;
        this.parameters.setDetectionImage(name);
    }

    /**
     * @return watershed detection parameters for this channel
     */
    public WatershedCellDetectionConfig getParameters() {
        return parameters;
    }

    /**
     * @param parameters watershed detection parameters for this channel
     */
    public void setParameters(WatershedCellDetectionConfig parameters) {
        this.parameters = parameters;
    }

    /**
     * @return classifiers to apply to detections from this channel
     */
    public List<ChannelClassifierConfig> getClassifiers() {
        return classifiers;
    }

    /**
     * Sets channel classifiers and propagates the configured channel name to each classifier.
     *
     * @param classifiers classifiers to apply to detections from this channel
     */
    public void setClassifiers(List<ChannelClassifierConfig> classifiers) {
        for (ChannelClassifierConfig classifier: classifiers) {
            classifier.setChannel(this.name);
        }
        this.classifiers = classifiers;
    }
}
