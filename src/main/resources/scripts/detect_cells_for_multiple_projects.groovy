// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
// SPDX-FileCopyrightText: 2025 Nash Baughman <nfbaughman@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

/*
 * REQUIREMENTS
 * ============
 * You need to:
 *  - have a BraiAn.yml file in the project folder (or its parent directory)
 *  - have images which have been registered and imported with ABBA (https://github.com/BIOP/qupath-extension-abba/)
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
import qupath.lib.gui.scripting.QPEx
import qupath.lib.projects.ProjectIO
import qupath.lib.projects.Project
import qupath.lib.images.ImageData
import java.awt.image.BufferedImage
import java.nio.file.Paths
import qupath.ext.braian.AtlasManager
import qupath.ext.braian.OverlappingDetections
import qupath.ext.braian.ImageChannelTools
import qupath.ext.braian.ChannelDetections
import qupath.ext.braian.config.ProjectsConfig

// Configuration - UPDATE THESE PATHS
PROJECTS_DIR = "/path/to/QuPath_projects/"
PROJECT_NAMES = [
    "project1", "project2", "project3", "project4",
]
ATLAS_NAME = "allen_mouse_10um_java"

PROJECT_NAMES.each { projectName ->
    def projectPath = Paths.get(PROJECTS_DIR, projectName, "project.qpproj").toFile()
    if (!projectPath.exists()) {
        println "Project not found: ${projectPath}"
        return
    }
    
    println "\n=== Processing project: ${projectName} ==="
    
    Project<BufferedImage> project = ProjectIO.loadProject(projectPath, BufferedImage.class)
    def projectDir = project.getPath().getParent()
    
    try {
        project.getImageList().each { entry ->
            ImageData<BufferedImage> imageData = null
            try {
                println "  Processing image: ${entry.getImageName()}"
                imageData = entry.readImageData()
                QPEx.setBatchProjectAndImage(project, imageData)
                
                // === Begin BraiAn detection logic ===
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
                
                if (allDetections.isEmpty()) {
                    println "  ${entry.getImageName()} : DONE! No annotations found to compute on"
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
                    overlaps = [new OverlappingDetections(controlChannel, otherChannels, true, hierarchy)]
                }
                
                // EXPORT RESULTS
                if (AtlasManager.isImported(ATLAS_NAME, hierarchy)) {
                    var atlas = new AtlasManager(ATLAS_NAME, hierarchy)
                    
                    // Sanitize image name for filesystem
                    INVALID_CHARS_WIN = ['<', '>' ,':', '"', '/', '\\', '|', '?', '*'] as Set<Character>
                    INVALID_CHARS_NIX = ['/'] as Set<Character>
                    def invalid_chars_regex = (INVALID_CHARS_WIN + INVALID_CHARS_NIX).collect { java.util.regex.Pattern.quote(it) }.join('|')
                    def imageName = entry.getImageName().replaceAll(invalid_chars_regex, '')
                    
                    // Build paths relative to project directory
                    var resultsDir = projectDir.resolve("results").toFile()
                    resultsDir.mkdirs()
                    var resultsFile = new File(resultsDir, imageName + "_regions.tsv")
                    atlas.saveResults(allDetections + overlaps, resultsFile, imageData, entry)
                    
                    var exclusionsDir = projectDir.resolve("regions_to_exclude").toFile()
                    exclusionsDir.mkdirs()
                    def exclusionsFile = new File(exclusionsDir, imageName + "_regions_to_exclude.txt")
                    atlas.fixExclusions()
                    atlas.saveExcludedRegions(exclusionsFile)
                }
                
                println "  ${entry.getImageName()} : DONE!"
                // === End BraiAn detection logic ===
                
                // Save the changes back to the project
                entry.saveImageData(imageData)
                println "  Successfully processed and saved ${entry.getImageName()}"
                
            } catch (Exception e) {
                println "  ERROR processing ${entry.getImageName()}: ${e.message}"
                e.printStackTrace()
                throw e // Halt on error
            } finally {
                if (imageData != null && imageData.getServer() != null) {
                    try {
                        imageData.getServer().close()
                    } catch (Exception e) {
                        println "  Warning: Failed to close image server: ${e.message}"
                    }
                }
                QPEx.resetBatchProjectAndImage()
            }
        }
        project.syncChanges()
        println "=== Finished project: ${projectName} ==="
        
    } catch (Exception e) {
        println "ERROR processing project ${projectName}: ${e.message}"
        e.printStackTrace()
        throw e // Halt on error
    } finally {
        project = null
        System.gc()
    }
}

println "\n=== All projects processed successfully! ==="
