// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package qupath.ext.braian.runners;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.fx.utils.FXUtils;
import qupath.ext.braian.utils.ProjectDiscoveryService;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.Commands;
import qupath.lib.images.ImageData;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectIO;
import qupath.lib.projects.ProjectImageEntry;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.List;

/**
 * Runner for importing warped atlas annotations from the ABBA extension.
 * <p>
 * This class provides helpers to import the atlas for the current image, the
 * current project, or
 * for a batch of QuPath projects.
 */
public final class ABBAImporterRunner {
    private static final Logger logger = LoggerFactory.getLogger(ABBAImporterRunner.class);

    private static final String NAMING_PROPERTY = "acronym";
    private static final boolean SPLIT_LEFT_RIGHT = true;
    private static final boolean OVERWRITE = true;

    private ABBAImporterRunner() {
    }

    /**
     * Imports the atlas into the image currently open in QuPath.
     *
     * @param qupath the QuPath GUI instance
     * @throws IllegalStateException if no image is open or if the ABBA extension is
     *                               not available
     */
    public static void runCurrentImage(QuPathGUI qupath) {
        if (!AbbaReflectionBridge.isAvailable()) {
            throw new IllegalStateException(AbbaReflectionBridge.getFailureReason());
        }
        ImageData<BufferedImage> imageData = qupath.getViewer() != null ? qupath.getViewer().getImageData() : null;
        if (imageData == null) {
            throw new IllegalStateException("No image is currently open.");
        }

        importAtlas(imageData);
        Project<BufferedImage> project = qupath.getProject();
        if (project != null) {
            ProjectImageEntry<BufferedImage> entry = project.getEntry(imageData);
            if (entry != null) {
                try {
                    entry.saveImageData(imageData);
                } catch (IOException e) {
                    logger.warn("Failed to save image data after import: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * Imports the atlas into every image entry of the current QuPath project.
     *
     * @param qupath the QuPath GUI instance
     * @throws IllegalStateException if no project is open or if the ABBA extension
     *                               is not available
     */
    public static void runProject(QuPathGUI qupath) {
        if (!AbbaReflectionBridge.isAvailable()) {
            throw new IllegalStateException(AbbaReflectionBridge.getFailureReason());
        }
        Project<BufferedImage> project = qupath.getProject();
        if (project == null) {
            throw new IllegalStateException("No project open.");
        }

        for (ProjectImageEntry<BufferedImage> entry : project.getImageList()) {
            ImageData<BufferedImage> imageData;
            try {
                imageData = entry.readImageData();
            } catch (IOException e) {
                logger.error("Failed to read image data {}: {}", entry.getImageName(), e.getMessage());
                continue;
            }
            try {
                importAtlas(imageData);
                entry.saveImageData(imageData);
            } catch (Exception e) {
                logger.error("Failed to import atlas for {}: {}", entry.getImageName(), e.getMessage());
            } finally {
                closeServer(imageData);
            }
        }

        try {
            project.syncChanges();
        } catch (Exception e) {
            logger.warn("Failed to sync project {}: {}", project.getName(), e.getMessage());
        }
        System.gc();
    }

    /**
     * Discovers QuPath projects under {@code rootPath} and imports the atlas into
     * each of them.
     *
     * @param qupath   the QuPath GUI instance
     * @param rootPath root directory containing QuPath projects
     * @throws IllegalArgumentException if {@code rootPath} is invalid
     * @throws IllegalStateException    if no projects are found or if the ABBA
     *                                  extension is not available
     * @see ProjectDiscoveryService#discoverProjectFiles(Path)
     */
    public static void runBatch(QuPathGUI qupath, Path rootPath) {
        if (!AbbaReflectionBridge.isAvailable()) {
            throw new IllegalStateException(AbbaReflectionBridge.getFailureReason());
        }
        if (rootPath == null || !Files.isDirectory(rootPath)) {
            throw new IllegalArgumentException("Invalid projects directory: " + rootPath);
        }
        List<Path> projectFiles = ProjectDiscoveryService.discoverProjectFiles(rootPath);
        if (projectFiles.isEmpty()) {
            throw new IllegalStateException("No QuPath projects found in " + rootPath);
        }

        runBatch(qupath, projectFiles);
    }

    /**
     * Imports the atlas into a batch of QuPath projects.
     *
     * @param qupath       the QuPath GUI instance
     * @param projectFiles list of QuPath project files (e.g.
     *                     {@code project.qpproj})
     * @throws IllegalArgumentException if {@code projectFiles} is null or empty
     * @throws IllegalStateException    if the ABBA extension is not available
     */
    public static void runBatch(QuPathGUI qupath, List<Path> projectFiles) {
        if (!AbbaReflectionBridge.isAvailable()) {
            throw new IllegalStateException(AbbaReflectionBridge.getFailureReason());
        }
        if (projectFiles == null || projectFiles.isEmpty()) {
            throw new IllegalArgumentException("No project files provided.");
        }

        // BraiAn Config is not needed for Atlas import

        for (Path projectFile : projectFiles) {
            Project<BufferedImage> project;
            try {
                project = ProjectIO.loadProject(projectFile.toFile(), BufferedImage.class);
            } catch (IOException e) {
                logger.error("Failed to load project {}: {}", projectFile, e.getMessage());
                continue;
            }
            FXUtils.runOnApplicationThread(() -> qupath.setProject(project));

            for (ProjectImageEntry<BufferedImage> entry : project.getImageList()) {
                ImageData<BufferedImage> imageData;
                try {
                    imageData = entry.readImageData();
                } catch (IOException e) {
                    logger.error("Failed to read image data {}: {}", entry.getImageName(), e.getMessage());
                    continue;
                }
                try {
                    importAtlas(imageData);
                    entry.saveImageData(imageData);
                } catch (Exception e) {
                    logger.error("Failed to import atlas for {}: {}", entry.getImageName(), e.getMessage());
                } finally {
                    closeServer(imageData);
                }
            }

            try {
                project.syncChanges();
            } catch (Exception e) {
                logger.warn("Failed to sync project {}: {}", projectFile, e.getMessage());
            }
            System.gc();
            FXUtils.runOnApplicationThread(() -> Commands.closeProject(qupath));
        }
    }

    private static void importAtlas(ImageData<BufferedImage> imageData) {
        imageData.setImageType(ImageData.ImageType.FLUORESCENCE);
        imageData.getHierarchy().clearAll();
        AbbaReflectionBridge.loadWarpedAtlasAnnotations(imageData, null, NAMING_PROPERTY, SPLIT_LEFT_RIGHT,
                OVERWRITE);
    }

    private static void closeServer(ImageData<BufferedImage> imageData) {
        try {
            imageData.getServer().close();
        } catch (Exception e) {
            logger.warn("Failed to close image server: {}", e.getMessage());
        }
    }

    // Project file discovery is delegated to ProjectDiscoveryService.
}
