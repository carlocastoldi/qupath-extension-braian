// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

/*
 * REQUIREMENTS
 * ============
 * You need to:
 *  - have created and opened a QuPath project
 *  - have all the QuPath projects from which you want to sample images from in the same folder. later defined as `projDir`
 *
 * This script then sample a number of images from each project equals to SAMPLE_PER_PROJECT.
 * Each of these images, along with its data, will be copied and saved in the currently opened project.
 */

import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI
import qupath.lib.projects.ProjectImageEntry;
import qupath.lib.projects.ProjectIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

var SAMPLE_PER_PROJECT = 3

var projDir = "/path/to/QuPath/projects"
var groupsProjects = [
    ["1R",  "3R",  "4R",  "11R"],
    ["5S",  "8S",  "10S", "13S"],
    ["16C", "17C", "18C", "19C"],
]

// SCRIPT START ///////////////////////////////////

// REMOVE ALL IMAGES
/*
import qupath.lib.gui.QuPathGUI
var p = getProject()
p.removeAllImages(p.getImageList(), true)
QuPathGUI.getInstance().refreshProject();
*/

var project = getProject()
groupsProjects = sameNumberPerGroup(groupsProjects)
groupsProjects = groupsProjects.flatten()

println "Selected projects to sample from: $groupsProjects"

List<ProjectImageEntry<BufferedImage>> projectImages = groupsProjects.collect { sampledProject ->
    sampleImagesFromProject(projDir, sampledProject, SAMPLE_PER_PROJECT)
}.flatten()

println "Sampled ${projectImages.size()} images: $projectImages"

for (var selectedImage : projectImages) {
    var duplicate = project.addDuplicate(selectedImage, false); // copyData
    project.syncChanges();
    selectedImage.getEntryPath().toFile().listFiles().each {filePath ->
        var target = Paths.get(duplicate.getEntryPath().toString(), filePath.getName())
        Files.copy(filePath.toPath(), target, StandardCopyOption.REPLACE_EXISTING)
    }
}
project.syncChanges();
QuPathGUI.getInstance().refreshProject();

def sameNumberPerGroup(Collection<Collection> ll) {
    var minimumSublistLength = ll.collect {it.size()}.min()
    return ll.collect {
        Collections.shuffle(it);
        it[0..minimumSublistLength-1]
    }
}

def sampleImagesFromProject(String projectsDir, String projectName, int n) {
    var projectFile = Paths.get(projectsDir, projectName, "project."+ProjectIO.DEFAULT_PROJECT_EXTENSION)
    var tempProject = ProjectIO.loadProject(GeneralTools.toURI(projectFile.toString()), BufferedImage.class);
    var tempImages = tempProject.getImageList()
    Collections.shuffle(tempImages)
    return tempImages[0..n-1]
}
