// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
// SPDX-FileCopyrightText: 2025 Nash Baughman <nfbaughman@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package qupath.ext.braian.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-channel configuration for BraiAn detection and classification.
 * <p>
 * This configuration describes one channel (by {@link #getName()}) and provides
 * both cell-detection
 * parameters and optional classifiers.
 */
public class ChannelDetectionsConfig {
    private String name;
    private WatershedCellDetectionConfig parameters = new WatershedCellDetectionConfig();
    private int inputChannelID = 1; // 1-based index
    private List<ChannelClassifierConfig> classifiers = List.of(); // maps classifier name to annotation names
    private boolean enableCellDetection = true;
    private boolean enablePixelClassification = false;
    private List<PixelClassifierConfig> pixelClassifiers = new ArrayList<>();

    /**
     * @return the 1-based input channel index used to map to the source image
     *         channels
     */
    public int getInputChannelID() {
        return inputChannelID;
    }

    /**
     * @param inputChannelID the 1-based input channel index used to map to the
     *                       source image channels
     */
    public void setInputChannelID(int inputChannelID) {
        this.inputChannelID = inputChannelID;
    }

    /**
     * @return the channel name used throughout configuration and classification
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the channel name and propagates it to
     * {@link WatershedCellDetectionConfig}.
     *
     * @param name the channel name
     */
    public void setName(String name) {
        this.name = name;
        this.parameters.setDetectionImage(name);
    }

    /**
     * @return the watershed cell detection parameters for this channel
     */
    public WatershedCellDetectionConfig getParameters() {
        return parameters;
    }

    /**
     * @param parameters the watershed cell detection parameters for this channel
     */
    public void setParameters(WatershedCellDetectionConfig parameters) {
        this.parameters = parameters;
    }

    /**
     * @return the list of object classifier configurations for this channel
     */
    public List<ChannelClassifierConfig> getClassifiers() {
        return classifiers;
    }

    /**
     * Sets the list of object classifier configurations for this channel.
     * <p>
     * This method also sets {@link ChannelClassifierConfig#setChannel(String)}
     * based on {@link #getName()}.
     *
     * @param classifiers the classifier configurations
     */
    public void setClassifiers(List<ChannelClassifierConfig> classifiers) {
        for (ChannelClassifierConfig classifier : classifiers) {
            classifier.setChannel(this.name);
        }
        this.classifiers = classifiers;
    }

    /**
     * @return true if cell detection is enabled for this channel
     */
    public boolean isEnableCellDetection() {
        return enableCellDetection;
    }

    /**
     * @param enableCellDetection true to enable cell detection for this channel
     */
    public void setEnableCellDetection(boolean enableCellDetection) {
        this.enableCellDetection = enableCellDetection;
    }

    /**
     * @return true if pixel classification is enabled for this channel
     */
    public boolean isEnablePixelClassification() {
        return enablePixelClassification;
    }

    /**
     * @param enablePixelClassification true to enable pixel classification for this
     *                                  channel
     */
    public void setEnablePixelClassification(boolean enablePixelClassification) {
        this.enablePixelClassification = enablePixelClassification;
    }

    /**
     * @return the pixel classifier configurations for this channel
     */
    public List<PixelClassifierConfig> getPixelClassifiers() {
        return pixelClassifiers;
    }

    /**
     * @param pixelClassifiers the pixel classifier configurations for this channel
     */
    public void setPixelClassifiers(List<PixelClassifierConfig> pixelClassifiers) {
        this.pixelClassifiers = pixelClassifiers;
    }
}
