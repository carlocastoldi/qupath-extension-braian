// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
// SPDX-FileCopyrightText: 2025 Nash Baughman <nfbaughman@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package qupath.ext.braian.runners;

import ij.measure.ResultsTable;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.braian.AbstractDetections;
import qupath.ext.braian.AtlasManager;
import qupath.ext.braian.config.ChannelDetectionsConfig;
import qupath.ext.braian.config.PixelClassifierConfig;
import qupath.ext.braian.config.ProjectsConfig;
import qupath.ext.braian.utils.BraiAn;
import qupath.lib.classifiers.pixel.PixelClassifier;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.measure.ObservableMeasurementTableData;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;
import qupath.lib.projects.Projects;
import qupath.opencv.ml.pixel.PixelClassifierTools;
import qupath.opencv.ml.pixel.PixelClassifiers;

import java.awt.image.BufferedImage;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

/**
 * Runner for pixel classifier execution and results export.
 * <p>
 * This class applies configured pixel classifiers to atlas regions and
 * optionally exports measurements
 * to TSV files.
 */
public final class PixelClassifierRunner {
    private static final Logger logger = LoggerFactory.getLogger(PixelClassifierRunner.class);

    private PixelClassifierRunner() {
    }

    /**
     * Runs configured pixel classifiers for an image and optionally exports
     * results.
     *
     * @param qupath     the QuPath GUI instance
     * @param imageData  the image to process
     * @param project    the current project; required for export
     * @param entry      the project entry corresponding to {@code imageData};
     *                   required for export
     * @param config     the BraiAn configuration
     * @param detections optional list of detections used to determine which atlas
     *                   regions to include
     * @param export     if true, exports results to the project {@code results/}
     *                   directory
     * @throws IllegalArgumentException if {@code imageData} is null
     */
    public static void runPixelClassifiers(QuPathGUI qupath,
            ImageData<BufferedImage> imageData,
            Project<BufferedImage> project,
            ProjectImageEntry<BufferedImage> entry,
            ProjectsConfig config,
            List<? extends AbstractDetections> detections,
            boolean export) {
        if (imageData == null) {
            throw new IllegalArgumentException("ImageData is required for pixel classification.");
        }

        List<ChannelDetectionsConfig> channelConfigs = Optional.ofNullable(config.getChannelDetections())
                .orElse(List.of());
        List<PixelClassifierConfig> classifierConfigs = new ArrayList<>();
        for (ChannelDetectionsConfig channelConfig : channelConfigs) {
            if (!channelConfig.isEnablePixelClassification()) {
                continue;
            }
            List<PixelClassifierConfig> pixelClassifiers = Optional.ofNullable(channelConfig.getPixelClassifiers())
                    .orElse(List.of());
            classifierConfigs.addAll(pixelClassifiers);
        }
        if (classifierConfigs.isEmpty()) {
            logger.info("No pixel classifiers configured.");
            return;
        }

        String atlasName = config.getAtlasName();
        if (atlasName == null || atlasName.isBlank()) {
            atlasName = "allen_mouse_10um_java";
        }

        var hierarchy = imageData.getHierarchy();
        String label = entry != null ? entry.getImageName() : imageData.getServerMetadata().getName();
        if (!AtlasManager.isImported(atlasName, hierarchy)) {
            logger.warn("No atlas '{}' imported for {}", atlasName, label);
            return;
        }

        AtlasManager atlas = new AtlasManager(atlasName, hierarchy);
        List<PathObject> baseRegions = detections != null && !detections.isEmpty()
                ? atlas.flatten(new ArrayList<>(detections))
                : atlas.flatten();
        if (baseRegions.isEmpty()) {
            logger.warn("No atlas regions available for pixel classification.");
            return;
        }

        List<String> measurementIds = new ArrayList<>();
        for (PixelClassifierConfig classifierConfig : classifierConfigs) {
            String classifierName = trimToNull(classifierConfig.getClassifierName());
            String measurementId = trimToNull(classifierConfig.getMeasurementId());
            if (classifierName == null) {
                logger.warn("Skipping pixel classifier with empty name.");
                continue;
            }
            if (measurementId == null) {
                logger.warn("Skipping pixel classifier '{}' without measurement ID.", classifierName);
                continue;
            }

            Path classifierPath;
            try {
                classifierPath = resolveClassifierPath(project, classifierName);
            } catch (FileNotFoundException e) {
                logger.warn("Failed to resolve pixel classifier {}: {}", classifierName, e.getMessage());
                continue;
            }

            PixelClassifier classifier;
            try {
                classifier = PixelClassifiers.readClassifier(classifierPath);
            } catch (IOException e) {
                logger.warn("Failed to load pixel classifier {}: {}", classifierName, e.getMessage());
                continue;
            }

            List<PathObject> targetRegions = filterRegions(baseRegions, classifierConfig.getRegionFilter());
            if (targetRegions.isEmpty()) {
                logger.warn("No atlas regions matched filter for pixel classifier '{}'", classifierName);
                continue;
            }

            boolean applied = applyPixelClassifierOnFxThread(imageData, classifier, measurementId, targetRegions);
            if (applied && !measurementIds.contains(measurementId)) {
                measurementIds.add(measurementId);
            }
        }

        if (!export || project == null || entry == null || measurementIds.isEmpty()) {
            return;
        }

        Path projectDir = Projects.getBaseDirectory(project).toPath();
        exportResults(projectDir, entry, imageData, baseRegions, measurementIds);
    }

    private static Path resolveClassifierPath(Project<?> project, String classifierName) throws FileNotFoundException {
        String fileName = classifierName.toLowerCase().endsWith(".json")
                ? classifierName
                : classifierName + ".json";
        return BraiAn.resolvePath(project, fileName);
    }

    private static void exportResults(Path projectDir,
            ProjectImageEntry<BufferedImage> entry,
            ImageData<BufferedImage> imageData,
            List<PathObject> regions,
            List<String> measurementIds) {
        ResultsTable results = new ResultsTable();
        ObservableMeasurementTableData ob = new ObservableMeasurementTableData();
        ob.setImageData(imageData, regions);

        String rawImageName = entry.getImageName();
        String imageName = sanitizeFileName(rawImageName);
        String areaColumn = "Area " + AtlasManager.um + "^2";

        for (PathObject region : regions) {
            results.incrementCounter();
            results.addValue("Image Name", rawImageName);

            Map<String, String> metadata = entry.getMetadata();
            if (metadata != null && !metadata.isEmpty()) {
                for (String key : metadata.keySet()) {
                    results.addValue("Metadata_" + key, metadata.get(key));
                }
            }

            String regionName = region.getName();
            results.addValue("Region", regionName == null ? "" : regionName);
            if (region.getPathClass() != null) {
                results.addValue("Classification", region.getPathClass().toString());
            }

            if (ob.isNumericMeasurement(areaColumn)) {
                double areaValue = ob.getNumericValue(region, areaColumn);
                results.addValue(areaColumn.replace(AtlasManager.um, "um"), areaValue);
            }

            for (String measurementId : measurementIds) {
                double value = ob.getNumericValue(region, measurementId);
                results.addValue(measurementId, value);
            }
        }

        Path resultsPath = projectDir.resolve("results").resolve(imageName + "_pixel_classifiers.tsv");
        try {
            Files.createDirectories(resultsPath.getParent());
        } catch (IOException e) {
            logger.error("Failed to create directory {}: {}", resultsPath.getParent(), e.getMessage());
            return;
        }

        boolean saved = results.save(resultsPath.toString());
        if (saved) {
            logger.info("Pixel classifier results saved under '{}'", resultsPath);
        } else {
            logger.warn("Failed to save pixel classifier results under '{}'", resultsPath);
        }
    }

    private static List<PathObject> filterRegions(List<PathObject> regions, List<String> filter) {
        if (filter == null || filter.isEmpty()) {
            return regions;
        }
        List<String> names = filter.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .toList();
        if (names.isEmpty()) {
            return regions;
        }
        List<PathObject> filtered = new ArrayList<>();
        for (PathObject region : regions) {
            String regionName = region.getName();
            if (regionName != null && names.contains(regionName)) {
                filtered.add(region);
            }
        }
        return filtered;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String sanitizeFileName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "image";
        }
        String sanitized = raw.replaceAll("[<>:\"/\\\\|?*]", "");
        if (sanitized.isBlank()) {
            return "image";
        }
        return sanitized;
    }

    private static boolean applyPixelClassifierOnFxThread(ImageData<BufferedImage> imageData,
            PixelClassifier classifier,
            String measurementId,
            List<PathObject> targetRegions) {
        if (Platform.isFxApplicationThread()) {
            return applyPixelClassifier(imageData, classifier, measurementId, targetRegions);
        }
        FutureTask<Boolean> task = new FutureTask<>(
                () -> applyPixelClassifier(imageData, classifier, measurementId, targetRegions));
        Platform.runLater(task);
        try {
            return task.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Pixel classifier execution interrupted for {}", measurementId, e);
        } catch (ExecutionException e) {
            logger.warn("Pixel classifier execution failed for {}", measurementId, e.getCause());
        }
        return false;
    }

    private static boolean applyPixelClassifier(ImageData<BufferedImage> imageData,
            PixelClassifier classifier,
            String measurementId,
            List<PathObject> targetRegions) {
        var hierarchy = imageData.getHierarchy();
        hierarchy.getSelectionModel().clearSelection();
        hierarchy.getSelectionModel().setSelectedObjects(targetRegions, null);
        try {
            return PixelClassifierTools.addMeasurementsToSelectedObjects(imageData, classifier, measurementId);
        } finally {
            hierarchy.getSelectionModel().clearSelection();
        }
    }
}
