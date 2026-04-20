// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
// SPDX-FileCopyrightText: 2025 Nash Baughman <nfbaughman@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package qupath.ext.braian.runners;

import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.braian.utils.ProjectDiscoveryService;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectIO;
import qupath.lib.projects.ProjectImageEntry;
import qupath.lib.projects.Projects;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Runner to sample images from multiple projects and create a new project for
 * classifier training.
 */
public class ClassifierSampleRunner {

    private static final Logger logger = LoggerFactory.getLogger(ClassifierSampleRunner.class);

    /**
     * Runs the sampling process.
     *
     * @param qupath            The QuPath GUI instance.
     * @param experimentRoot    The root directory containing experiment projects.
     * @param samplesPerProject Number of images to sample from each project.
     * @param newProjectName    Name of the new project to create.
     * @throws IOException If creating the project or copying files fails.
     */
    public static void run(QuPathGUI qupath, Path experimentRoot, int samplesPerProject, String newProjectName)
            throws IOException {
        logger.info("Starting classifier sample run...");
        logger.info("Root: {}, Samples: {}, Name: {}", experimentRoot, samplesPerProject, newProjectName);

        List<Path> projectFiles = ProjectDiscoveryService.discoverProjectFiles(experimentRoot);
        if (projectFiles.isEmpty()) {
            throw new IOException("No projects found in " + experimentRoot);
        }

        Path newProjectDir = experimentRoot.resolve(newProjectName);
        if (Files.exists(newProjectDir)) {
            throw new IOException("Project directory already exists: " + newProjectDir);
        }
        Files.createDirectories(newProjectDir);

        Project<BufferedImage> newProject = Projects.createProject(newProjectDir.toFile(), BufferedImage.class);
        newProject.syncChanges();

        for (Path projectFile : projectFiles) {
            // Avoid recursively processing the new project if it gets discovered or is in
            // the list
            if (projectFile.getParent() != null && projectFile.getParent().equals(newProjectDir))
                continue;

            try {
                // Must ensure project is not read-only for us to read from it
                Project<BufferedImage> sourceProject = ProjectIO.loadProject(projectFile.toFile(), BufferedImage.class);
                List<ProjectImageEntry<BufferedImage>> images = new ArrayList<>(sourceProject.getImageList());
                Collections.shuffle(images);

                int count = 0;
                for (ProjectImageEntry<BufferedImage> entry : images) {
                    if (count >= samplesPerProject)
                        break;

                    // Use copyData = true to let QuPath handle file copying (images, thumbnails,
                    // data.qpdata)
                    newProject.addDuplicate(entry, true);
                    count++;
                }

            } catch (Exception e) {
                logger.error("Error processing project " + projectFile, e);
            }
        }

        newProject.syncChanges();

        final Project<BufferedImage> finalProject = newProject;
        Platform.runLater(() -> {
            qupath.setProject(finalProject);
            Dialogs.showInfoNotification("Classifier Training", "Created and opened " + newProjectName);
        });
    }
}
