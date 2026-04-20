// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
// SPDX-FileCopyrightText: 2025 Nash Baughman <nfbaughman@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package qupath.ext.braian.runners;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.braian.AtlasManager;
import qupath.ext.braian.ExclusionReport;
import qupath.fx.utils.FXUtils;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.Commands;
import qupath.lib.images.ImageData;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectIO;
import qupath.lib.projects.ProjectImageEntry;
import qupath.lib.projects.Projects;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Runner for automatically excluding empty atlas regions.
 * <p>
 * This class orchestrates
 * {@link AtlasManager#autoExcludeEmptyRegions(ImageData, List, Map)}
 * across different scopes
 * (current image, current project, or a batch of projects) and ensures results
 * are persisted.
 */
public final class AutoExcludeEmptyRegionsRunner {
    private static final Logger logger = LoggerFactory.getLogger(AutoExcludeEmptyRegionsRunner.class);

    private AutoExcludeEmptyRegionsRunner() {
    }

    /**
     * Runs auto-exclusion on the image currently open in QuPath.
     *
     * @param qupath               the QuPath GUI instance
     * @param channelNames         the list of channel names to evaluate
     * @param useMaxAcrossChannels if true, use the max intensity across channels
     * @param thresholdMultiplier  multiplier applied to the adaptive threshold
     * @return the list of excluded regions for the current image
     * @throws IllegalStateException if no image is open
     */
    public static List<ExclusionReport> runCurrentImage(
            QuPathGUI qupath,
            List<String> channelNames,
            boolean useMaxAcrossChannels,
            double thresholdMultiplier) {
        ImageData<BufferedImage> imageData = qupath.getViewer() != null ? qupath.getViewer().getImageData() : null;
        if (imageData == null) {
            throw new IllegalStateException("No image is currently open.");
        }

        Project<BufferedImage> project = qupath.getProject();
        ProjectImageEntry<BufferedImage> entry = project != null ? project.getEntry(imageData) : null;
        Path projectFile = project != null
                ? Projects.getBaseDirectory(project).toPath().resolve("project." + ProjectIO.DEFAULT_PROJECT_EXTENSION)
                : null;
        String projectName = project != null ? project.getName() : null;
        String imageName = entry != null ? entry.getImageName() : imageData.getServerMetadata().getName();

        List<ExclusionReport> reports;
        try {
            AtlasManager atlas = new AtlasManager(imageData.getHierarchy());
            reports = atlas.autoExcludeEmptyRegions(imageData, channelNames, useMaxAcrossChannels, thresholdMultiplier);
        } catch (Exception e) {
            throw new IllegalStateException("Auto-exclusion failed for " + imageName + ": " + e.getMessage(), e);
        }

        List<ExclusionReport> withContext = reports.stream()
                .map(r -> new ExclusionReport(projectFile, projectName, imageName, r.excludedAnnotationId(),
                        r.regionName(), r.percentile()))
                .toList();

        if (entry != null) {
            try {
                entry.saveImageData(imageData);
            } catch (IOException e) {
                logger.warn("Failed to save image data after auto-exclusion: {}", e.getMessage());
            }
        }
        return withContext;
    }

    /**
     * Runs auto-exclusion for every entry of the current QuPath project.
     *
     * @param qupath               the QuPath GUI instance
     * @param channelNames         the list of channel names to evaluate
     * @param useMaxAcrossChannels if true, use the max intensity across channels
     * @param thresholdMultiplier  multiplier applied to the adaptive threshold
     * @return a flattened list of excluded regions for all entries
     * @throws IllegalStateException if no project is open
     */
    public static List<ExclusionReport> runProject(
            QuPathGUI qupath,
            List<String> channelNames,
            boolean useMaxAcrossChannels,
            double thresholdMultiplier) {
        Project<BufferedImage> project = qupath.getProject();
        if (project == null) {
            throw new IllegalStateException("No project open.");
        }

        Path projectFile = Projects.getBaseDirectory(project).toPath()
                .resolve("project." + ProjectIO.DEFAULT_PROJECT_EXTENSION);
        String projectName = project.getName();
        List<ExclusionReport> allReports = new ArrayList<>();

        for (ProjectImageEntry<BufferedImage> entry : project.getImageList()) {
            ImageData<BufferedImage> imageData;
            try {
                imageData = entry.readImageData();
            } catch (IOException e) {
                logger.error("Failed to read image data {}: {}", entry.getImageName(), e.getMessage());
                continue;
            }
            try {
                if (!AtlasManager.isImported(imageData.getHierarchy())) {
                    logger.warn("No imported atlas found for {}", entry.getImageName());
                    continue;
                }
                AtlasManager atlas = new AtlasManager(imageData.getHierarchy());
                List<ExclusionReport> reports = atlas.autoExcludeEmptyRegions(imageData, channelNames,
                        useMaxAcrossChannels, thresholdMultiplier);
                for (ExclusionReport r : reports) {
                    allReports.add(new ExclusionReport(projectFile, projectName, entry.getImageName(),
                            r.excludedAnnotationId(), r.regionName(), r.percentile()));
                }
                entry.saveImageData(imageData);
            } catch (Exception e) {
                logger.error("Failed to auto-exclude regions for {}: {}", entry.getImageName(), e.getMessage());
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
        return allReports;
    }

    /**
     * Runs auto-exclusion for a list of QuPath projects.
     *
     * @param qupath               the QuPath GUI instance
     * @param projectFiles         list of QuPath project files (e.g.
     *                             {@code project.qpproj})
     * @param channelNames         the list of channel names to evaluate
     * @param useMaxAcrossChannels if true, use the max intensity across channels
     * @param thresholdMultiplier  multiplier applied to the adaptive threshold
     * @return a flattened list of excluded regions for all entries across all
     *         projects
     * @throws IllegalArgumentException if {@code projectFiles} is null or empty
     */
    public static List<ExclusionReport> runBatch(
            QuPathGUI qupath,
            List<Path> projectFiles,
            List<String> channelNames,
            boolean useMaxAcrossChannels,
            double thresholdMultiplier) {
        if (projectFiles == null || projectFiles.isEmpty()) {
            throw new IllegalArgumentException("No project files provided.");
        }

        List<ExclusionReport> allReports = new ArrayList<>();

        for (Path projectFile : projectFiles) {
            Project<BufferedImage> project;
            try {
                project = ProjectIO.loadProject(projectFile.toFile(), BufferedImage.class);
            } catch (IOException e) {
                logger.error("Failed to load project {}: {}", projectFile, e.getMessage());
                continue;
            }
            FXUtils.runOnApplicationThread(() -> qupath.setProject(project));
            String projectName = project.getName();

            for (ProjectImageEntry<BufferedImage> entry : project.getImageList()) {
                ImageData<BufferedImage> imageData;
                try {
                    imageData = entry.readImageData();
                } catch (IOException e) {
                    logger.error("Failed to read image data {}: {}", entry.getImageName(), e.getMessage());
                    continue;
                }
                try {
                    if (!AtlasManager.isImported(imageData.getHierarchy())) {
                        logger.warn("No imported atlas found for {}", entry.getImageName());
                        continue;
                    }
                    AtlasManager atlas = new AtlasManager(imageData.getHierarchy());
                    List<ExclusionReport> reports = atlas.autoExcludeEmptyRegions(imageData, channelNames,
                            useMaxAcrossChannels, thresholdMultiplier);
                    for (ExclusionReport r : reports) {
                        allReports.add(new ExclusionReport(projectFile, projectName, entry.getImageName(),
                                r.excludedAnnotationId(), r.regionName(), r.percentile()));
                    }
                    entry.saveImageData(imageData);
                } catch (Exception e) {
                    logger.error("Failed to auto-exclude regions for {}: {}", entry.getImageName(), e.getMessage());
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
        return allReports;
    }

    /**
     * Retrieves the excluded regions for every entry of the current QuPath project.
     *
     * @param qupath the QuPath GUI instance
     * @return a flattened list of excluded regions for all entries
     * @throws IllegalStateException if no project is open
     */
    public static List<ExclusionReport> getExcludedRegionsCurrentProject(QuPathGUI qupath) {
        Project<BufferedImage> project = qupath.getProject();
        if (project == null) {
            throw new IllegalStateException("No project open.");
        }
        Path projectFile = Projects.getBaseDirectory(project).toPath()
                .resolve("project." + ProjectIO.DEFAULT_PROJECT_EXTENSION);
        String projectName = project.getName();
        List<ExclusionReport> all = new ArrayList<>();

        for (ProjectImageEntry<BufferedImage> entry : project.getImageList()) {
            ImageData<BufferedImage> imageData;
            try {
                imageData = entry.readImageData();
            } catch (IOException e) {
                logger.error("Failed to read image data {}: {}", entry.getImageName(), e.getMessage());
                continue;
            }
            try {
                List<ExclusionReport> reports;
                if (AtlasManager.isImported(imageData.getHierarchy())) {
                    AtlasManager atlas = new AtlasManager(imageData.getHierarchy());
                    reports = atlas.getExcludedRegions(imageData);
                } else {
                    reports = imageData.getHierarchy().getAnnotationObjects().stream()
                            .filter(o -> o.getPathClass() == AtlasManager.EXCLUDE_CLASSIFICATION)
                            .map(o -> new ExclusionReport(null, null, entry.getImageName(), o.getID(), o.getName(),
                                    Double.NaN))
                            .toList();
                }
                for (ExclusionReport r : reports) {
                    all.add(new ExclusionReport(projectFile, projectName, entry.getImageName(),
                            r.excludedAnnotationId(), r.regionName(), r.percentile()));
                }
            } catch (Exception e) {
                logger.error("Failed to gather exclusions for {}: {}", entry.getImageName(), e.getMessage());
            } finally {
                closeServer(imageData);
            }
        }

        return all;
    }

    private static void closeServer(ImageData<BufferedImage> imageData) {
        try {
            imageData.getServer().close();
        } catch (Exception e) {
            logger.warn("Failed to close image server: {}", e.getMessage());
        }
    }
}
