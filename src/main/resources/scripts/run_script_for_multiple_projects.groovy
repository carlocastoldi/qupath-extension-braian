// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

/*
 * REQUIREMENTS
 * ============
 * You need to:
 *  - write all projects' names into PROJECT_NAMES
 *  - have have all your QuPath projects in the same folder PROJECTS_DIR
 *
 * This script runs SCRIPT_PATH on all projects.
 * Compared to command-line QuPath, it has the advantage of being able to visually see the progress
 * Contrary to QuPath command-line, it has also the great benefit of being compatible with LightScriptRunner
 * (see https://forum.image.sc/t/run-a-script-on-a-project-image-without-accessing-the-image-file/88363/)
 */
import qupath.fx.utils.FXUtils
import qupath.lib.gui.commands.Commands
import qupath.lib.gui.scripting.QPEx
import qupath.lib.projects.ProjectIO
import java.awt.image.BufferedImage
import java.nio.file.Paths

PROJECTS_DIR = "/path/to/QuPath_projects/"
PROJECT_FILE_NAME = "project"
PROJECT_NAMES = [
    "animal1", "animal2",  "animal3",  "animal4",
    "animal5", "animal6",  "animal7",  "animal8",
    "animal9", "animal10", "animal11", "animal12",
]

SCRIPT_PATH = "/path/to/script.groovy"

var qupathGUI = QPEx.getQuPath()
var script = new File(SCRIPT_PATH)
if (!script.exists()) {
    println "Can't find the given script: "+script
    return
}
PROJECT_NAMES.each {
    var projectFile = Paths.get(PROJECTS_DIR, it, PROJECT_FILE_NAME+"."+ProjectIO.DEFAULT_PROJECT_EXTENSION).toFile()
    var thisProject = ProjectIO.loadProject(projectFile, BufferedImage.class)
    FXUtils.runOnApplicationThread({qupathGUI.setProject(thisProject)})
    run(script)
    FXUtils.runOnApplicationThread({Commands.closeProject(qupathGUI)})
}
