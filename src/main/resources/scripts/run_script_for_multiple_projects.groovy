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
import qupath.lib.gui.scripting.QPEx;
import qupath.lib.projects.ProjectIO
import java.awt.image.BufferedImage;
import java.nio.file.Paths

PROJECTS_DIR = "/path/to/QuPath_projects/"
PROJECT_NAMES = [
    "287HC",  "342HC", "343HC",  "346HC",  "371HC",
    "329CTX", "331CTX", "355CTX", "400CTX", "401CTX", "402CTX",
    "367FC",  "368FC",  "369FC",  "426FC",  "427FC",  "428FC",
]

SCRIPT_PATH = "/path/to/script.groovy"

def qupathGUI = QPEx.getQuPath()
PROJECT_NAMES.each {
    var projectFile = Paths.get(PROJECTS_DIR, it, "project.qpproj").toFile()
    var thisProject = ProjectIO.loadProject(projectFile, BufferedImage.class)
    FXUtils.runOnApplicationThread({qupathGUI.setProject(thisProject)})
    run(new File(SCRIPT_PATH))
    FXUtils.runOnApplicationThread({Commands.closeProject(qupathGUI)})
}