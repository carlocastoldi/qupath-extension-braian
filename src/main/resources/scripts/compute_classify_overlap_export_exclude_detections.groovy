// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

/*
 * REQUIREMENTS
 * ============
 * You need to:
 *  - have a BraiAn.yml file in the project folder (or its parent directory)
 *  - have an opened image which has been registered and imported with ABBA (https://github.com/BIOP/qupath-extension-abba/)
 *  - have excluded the brain regions that are missing or badly aligned to the image
 *    This can be done by:
 *      + either drawing a larger annotation, classified as "Exclude", that contains the above mentioned regions
 *      + or DUPLICATING (SHIFT+D) a brain region annotation and then classifying it as "Exclude"
 *
 * This script then reads the config file, and based on that, for each of the chosen image channels:
 *  - computes the detection using WatershedCellDetection algorithm with an automatic threshold
 *  - classifies the detection on each regions with the chosen classifier
 *  - finds all detections that are double (or triple/multiple) positive
 *  - exports to file the number of detections/double+ found in each brain region
 *  - exports to file a list of regions that have to be excluded from further analysis
 */
import qupath.ext.braian.AtlasManager
import qupath.ext.braian.OverlappingDetections
import qupath.ext.braian.ImageChannelTools
import qupath.ext.braian.ChannelDetections
import qupath.ext.braian.config.ProjectsConfig

import static qupath.lib.scripting.QP.*

var imageData = getCurrentImageData()
// unless explicitly needed, from QuPath 0.6.* avoid calling imageData.getServer(). It makes scripts considerably slower
var server = imageData.getServer()
var hierarchy = imageData.getHierarchy()
var config = ProjectsConfig.read("BraiAn.yml")
var annotations = config.getAnnotationsForDetections(hierarchy)

// COMPUTE CHANNEL DETECTIONS
var allDetections = config.channelDetections.collect { detectionsConf ->
    var channel = new ImageChannelTools(detectionsConf.name, imageData)
    try {
        new ChannelDetections(channel, annotations, detectionsConf.parameters, hierarchy)
    } catch (IllegalArgumentException ignored) {
        null
    }
}.findAll { it != null }

// RETRIEVE PRE-COMPUTED CHANNEL DETECTIONS
// var allDetections = config.channelDetections.collect { detectionsConf -> new ChannelDetections(detectionsConf.name, hierarchy) }

if (allDetections.isEmpty()) {
    println getCurrentImageName()+" : DONE! No annotations found to compute on"
    return
}

// CLASSIFY CHANNEL DETECTIONS
allDetections.forEach { detections ->
    var detectionsConfig = config.channelDetections.find { detectionsConf -> detectionsConf.name == detections.getId() }
    if (detectionsConfig.classifiers == null)
        return
    var partialClassifiers = detectionsConfig.classifiers.collect{ it.toPartialClassifier(hierarchy) }
    detections.applyClassifiers(partialClassifiers, imageData)
}

var overlaps = []
Optional<String> control
if ((control = config.getControlChannel()).isPresent() ) {
    String controlChannelName = control.get()
    var controlChannel = allDetections.find { it.getId() == controlChannelName }
    var otherChannels = allDetections.findAll { it.getId() != controlChannelName }
    // COMPUTE OVERLAPS
    overlaps = [new OverlappingDetections(controlChannel, otherChannels, true, hierarchy)]
    // RETRIEVE PRE-COMPUTED OVERLAPS
    // overlaps = [new OverlappingDetections(controlChannel, otherChannels, false, hierarchy)]
}

// EXPORT RESULTS
var atlasName = "allen_mouse_10um_java"
if (AtlasManager.isImported(atlasName, hierarchy)) {
    var atlas = new AtlasManager(atlasName, hierarchy)
    atlas.fixExclusions() // just in case

    INVALID_CHARS_WIN = ['<', '>' ,':', '"', '/', '\\', '|', '?', '*'] as Set<Character>
    INVALID_CHARS_NIX = ['/'] as Set<Character>
    def invalid_chars_regex = (INVALID_CHARS_WIN+INVALID_CHARS_NIX).collect { java.util.regex.Pattern.quote(it) }.join('|')
    def imageName = getProjectEntry().getImageName().replaceAll(invalid_chars_regex, '')

    var resultsFile = new File(buildPathInProject("results", imageName + "_regions.tsv")) // can be .csv too
    atlas.saveResults(allDetections + overlaps, resultsFile, imageData, getProjectEntry())

    def exclusionsFile = new File(buildPathInProject("regions_to_exclude", imageName + "_regions_to_exclude.txt"))
    atlas.saveExcludedRegions(exclusionsFile)
}

println getCurrentImageName()+" : DONE!"
