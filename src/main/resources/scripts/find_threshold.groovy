// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

/*
 * REQUIREMENTS
 * ============
 * You need to:
 *  - have an opened image with a channel called 'AF647'
 *
 * This script then defines a threshold to apply with WatershedCellDetection algorithm
 * by choosing a local maximum from the image's histogram
 */
import qupath.ext.braian.ImageChannelTools
import qupath.ext.braian.config.AutoThresholdParmameters
import qupath.ext.braian.config.ProjectsConfig
import qupath.ext.braian.config.WatershedCellDetectionConfig

import static qupath.lib.scripting.QP.*

var imageData = getCurrentImageData()

var channel = new ImageChannelTools("AF647", imageData)
var thresholder = new AutoThresholdParmameters() // with default parameters
WatershedCellDetectionConfig.findThreshold(channel, thresholder)
thresholder.resolutionLevel = 2  // increase the resolution (lower level) from which the histogram is computed
thresholder.peakProminence = 500 // a putative threshold must have at least 500 pixels more than the surrounding proximity (in intensity)
WatershedCellDetectionConfig.findThreshold(channel, thresholder)
thresholder.resolutionLevel = 4
thresholder.peakProminence = 100
thresholder.smoothWindowSize = 5 // decrease the smoothing of the histogram, making it more susceptible to local changes
thresholder.nPeak = 2            // take the second peak as threshold
WatershedCellDetectionConfig.findThreshold(channel, thresholder)

// you can also check it using BraiAn.yml's settings
ProjectsConfig.read("BraiAn.yml").channelDetections
        .find { detectionsConf -> detectionsConf.name == channel.name }
        .parameters.build(channel)