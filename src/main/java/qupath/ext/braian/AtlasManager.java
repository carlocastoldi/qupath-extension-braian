// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package qupath.ext.braian;

import qupath.ext.braian.utils.BraiAn;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.measure.ObservableMeasurementTableData;
import qupath.lib.images.servers.PixelCalibration;
import qupath.imagej.tools.IJTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.projects.ProjectImageEntry;
import qupath.lib.regions.RegionRequest;
import qupath.lib.scripting.QP;

import ij.ImagePlus;
import ij.process.ImageProcessor;
import ij.process.AutoThresholder;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static qupath.ext.braian.BraiAnExtension.getLogger;
import static qupath.ext.braian.BraiAnExtension.logger;

class ImportedAtlasNotFound extends RuntimeException {
    public ImportedAtlasNotFound() {
        super("No previously imported atlas found. Use ABBA to align the slice to an atlas and import the brain annotations in the QuPath project ");
    }
}

class DisruptedAtlasHierarchy extends RuntimeException {
    public DisruptedAtlasHierarchy(PathObject atlas) {
        super("The atlas hierarchy '" + atlas + "' was disrupted. " +
                "You must import the atlas annotations again and delete any previous region annotation.");
    }
}

class ExclusionMistakeException extends RuntimeException {
    public ExclusionMistakeException() {
        super("Some regions in the atlas ontology were wrongly classified as '" + AtlasManager.EXCLUDE_CLASSIFICATION
                + "'.\n" +
                "You can try to fix this by calling fixExclusions() on the AtlasManager instance.");
    }
}

/**
 * This class helps to manage and exporting results for each brain region. It
 * works closely with ABBA's QuPath extension.
 */
public class AtlasManager {
    public final static String um = GeneralTools.micrometerSymbol();
    public final static PathClass EXCLUDE_CLASSIFICATION = PathClass.fromString("Exclude");
    public final static PathClass ABBA_LEFT = PathClass.fromString("Left");
    public final static PathClass ABBA_RIGHT = PathClass.fromString("Right");

    private static final int AUTO_THRESHOLD_RESOLUTION_LEVEL = 4;

    /**
     * Checks whether at least one ABBA atlas was previously imported.
     * 
     * @param hierarchy where to search for the atlas
     * @return true if an ABBA atlas was previously imported
     */
    public static boolean isImported(PathObjectHierarchy hierarchy) {
        return !AtlasManager.search(null, hierarchy).isEmpty();
    }

    /**
     * Checks whether a specific ABBA atlas was previously imported.
     * 
     * @param atlasName the name of the atlas to check.
     *                  If null, it checks whether <i>any</i> atlas was imported
     *                  with ABBA
     * @param hierarchy where to search for the atlas
     * @return true if the atlas was previously imported
     */
    public static boolean isImported(String atlasName, PathObjectHierarchy hierarchy) {
        return !AtlasManager.search(atlasName, hierarchy).isEmpty();
    }

    private static List<PathObject> search(String atlasName, PathObjectHierarchy hierarchy) {
        return hierarchy.getAnnotationObjects()
                .stream()
                .filter(o -> "Root".equals(o.getName()) &&
                        (atlasName == null
                                || (o.getPathClass() != null && o.getPathClass().getName().equals(atlasName))))
                .toList();
    }

    private static Stream<PathObject> flattenObjectStream(PathObject parent) {
        return Stream.concat(
                Stream.of(parent),
                parent.getChildObjects().stream()
                        .filter(o -> o instanceof PathAnnotationObject)
                        .flatMap(AtlasManager::flattenObjectStream));
    }

    private static List<PathObject> flattenObject(PathObject parent) {
        return flattenObjectStream(parent).toList();
    }

    private static List<String> getDetectionsMeasurements(List<AbstractDetections> detections) {
        // TODO: should avoid resorting to QP to get the server metadata
        PixelCalibration cal = QP.getCurrentImageData().getServerMetadata().getPixelCalibration();
        if (!um.equals(cal.getPixelWidthUnit()) || !um.equals(cal.getPixelHeightUnit()))
            throw new RuntimeException(
                    "FAILED to export results. Expected image pixel units to be in 'µm', instead got them in '" +
                            cal.getPixelWidthUnit() + "' and '" + cal.getPixelWidthUnit()
                            + "'. Try setting it with setPixelSizeMicrons()");
        Stream<String> genericMeasurements = Stream.of("Name", "Classification", "Area " + um + "^2", "Num Detections");
        Stream<String> detectionsMeasurements = detections.stream()
                .flatMap(d -> d.getDetectionsPathClasses().stream().map(pc -> "Num " + pc.getName()));
        return Stream.concat(genericMeasurements, detectionsMeasurements).toList();
    }

    private static List<PathObject> getExclusionAnnotations(PathObjectHierarchy hierarchy) {
        return hierarchy.getAnnotationObjects().stream()
                .filter(ann -> ann.getPathClass() == AtlasManager.EXCLUDE_CLASSIFICATION)
                .toList();
    }

    private final PathObject atlasObject;
    private final PathObjectHierarchy hierarchy;

    /**
     * Constructs a manager of the specified atlas imported with ABBA.
     * 
     * @param atlasName the name of the atlas that was imported with ABBA.
     *                  If null, it selects the first available.
     * @param hierarchy where to search for the atlas
     * @throws ImportedAtlasNotFound   if the specified atlas was not found
     * @throws DisruptedAtlasHierarchy if the found atlas hierarchy was disrupted
     */
    public AtlasManager(String atlasName, PathObjectHierarchy hierarchy) {
        this.hierarchy = hierarchy;
        List<PathObject> atlases = AtlasManager.search(atlasName, hierarchy);
        if (atlases.isEmpty())
            throw new ImportedAtlasNotFound();
        this.atlasObject = atlases.getFirst();
        if (this.atlasObject.getChildObjects().isEmpty())
            throw new DisruptedAtlasHierarchy(this.atlasObject);
        if (atlases.size() > 1)
            getLogger().warn("Several imported atlases have been found. Selecting: {}", this.atlasObject);
    }

    /**
     * Constructs a manager of the first available atlas imported with ABBA.
     * 
     * @param hierarchy where to search for an atlas
     * @throws ImportedAtlasNotFound   if no atlas was not found in the object
     *                                 hierarchy
     * @throws DisruptedAtlasHierarchy if the found atlas hierarchy was disrupted
     * @see AtlasManager(String, PathObjectHierarchy)
     */
    public AtlasManager(PathObjectHierarchy hierarchy) {
        this(null, hierarchy);
    }

    /**
     * @return the annotations that contains all atlas annotations.
     */
    public PathObject getRoot() {
        return this.atlasObject;
    }

    /**
     * Flattens the atlas's ontology into a list of annotations.
     * It may return some non-region annotations too, if the atlas hierarchy was
     * modified with added/removed elements.
     * 
     * @return the list of all brain regions in the atlas
     * @throws DisruptedAtlasHierarchy if the current atlas hierarchy was disrupted,
     *                                 and it cannot find all the brain region
     *                                 organised according to the atlas's hierarchy
     * @see #flatten(List)
     */
    public List<PathObject> flatten() {
        if (this.atlasObject.getChildObjects().isEmpty())
            throw new DisruptedAtlasHierarchy(this.atlasObject);
        return AtlasManager.flattenObject(this.atlasObject);
    }

    /**
     * Flattens the atlas ontology into a list of annotations.
     * If the atlas hierarchy was modified by adding {@link AbstractDetections}'s
     * containers,
     * it filters them from the current brain hierarchy.
     * <br>
     * It may still return some non-region annotations, if the atlas hierarchy was
     * further modified with added/removed elements.
     * 
     * @return the list of all brain regions in the atlas
     * @throws DisruptedAtlasHierarchy if the current atlas hierarchy was disrupted,
     *                                 and it cannot find all the brain region
     *                                 organised according to the atlas's hierarchy
     * @see #flatten()
     */
    public List<PathObject> flatten(List<AbstractDetections> detections) {
        if (this.atlasObject.getChildObjects().isEmpty())
            throw new DisruptedAtlasHierarchy(this.atlasObject);
        List<PathAnnotationObject> containers = detections.stream().flatMap(d -> d.getContainers().stream()).toList();
        List<PathObject> brainRegions = AtlasManager.flattenObjectStream(this.atlasObject)
                .filter(ann -> !containers.contains(ann))
                .toList();
        return brainRegions;
    }

    /**
     * Saves a TSV file containing data for each brain region of the atlas.
     * Namely, Image name, brain region name, hemisphere, area in µm², number of
     * detections for each of the given types.
     * The table is saved as a CSV (comma-separated values) file if 'file' ends with
     * ".csv"
     * 
     * @param detections the list of detection of which to gather the data for each
     *                   region
     * @param file       the file where it should write to. Note that if the file
     *                   exists, it will be overwritten
     * @throws ExclusionMistakeException if the atlas hierarchy contains regions
     *                                   classified as
     *                                   {@link #EXCLUDE_CLASSIFICATION}.
     * @throws DisruptedAtlasHierarchy   if the current atlas hierarchy was
     *                                   disrupted,
     *                                   and it cannot find all the brain region
     *                                   organised according to the atlas's
     *                                   hierarchy
     */
    // Olivier Burri <https://github.com/lacan> wrote mostly of this function and
    // published under Apache-2.0 license for qupath-extension-biop
    public boolean saveResults(List<AbstractDetections> detections, File file) {
        if (this.atlasObject.getChildObjects().isEmpty())
            throw new DisruptedAtlasHierarchy(this.atlasObject);
        if (file.exists())
            if (!file.delete()) {
                getLogger().error("Could not delete previous results file {}, the file could be locked.",
                        file.getName());
                return false;
            }
        QP.mkdirs(file.getAbsoluteFile().getParent());

        // We use a ResultsTable to store the data
        ResultsTable results = new ResultsTable();

        ObservableMeasurementTableData ob = new ObservableMeasurementTableData();
        List<PathObject> brainRegions = this.flatten(detections);
        if (brainRegions.stream().anyMatch(p -> p.getPathClass() == EXCLUDE_CLASSIFICATION))
            throw new ExclusionMistakeException();
        // This line creates all the measurements
        ob.setImageData(QP.getCurrentImageData(), brainRegions);

        ProjectImageEntry<BufferedImage> entry = QP.getProjectEntry();

        assert entry != null;
        String rawImageName = entry.getImageName();
        double numericValue;

        // Add value for each selected object
        for (PathObject brainRegion : brainRegions) {
            results.incrementCounter();
            results.addValue("Image Name", rawImageName);

            // Check if image has associated metadata and add it as columns
            if (!entry.getMetadata().isEmpty()) {
                Map<String, String> metadata = entry.getMetadata();
                for (String key : metadata.keySet()) {
                    results.addValue("Metadata_" + key, metadata.get(key));
                }
            }

            // Then we can add the results the user requested
            // Because the Mu is sometimes poorly formatted, we remove them in favor of a
            // 'u'
            for (String col : AtlasManager.getDetectionsMeasurements(detections)) {
                if (ob.isNumericMeasurement(col)) {
                    numericValue = ob.getNumericValue(brainRegion, col);
                    if (col.startsWith("Num ") && Double.isNaN(numericValue))
                        numericValue = 0.;
                    results.addValue(col.replace(um, "um"), numericValue);
                }
                if (ob.isStringMeasurement(col))
                    results.addValue(col.replace(um, "um"), ob.getStringValue(brainRegion, col));
            }
        }
        boolean isSaved = results.save(file.getAbsolutePath());
        getLogger().info("Results '{}' Saved under '{}', contains {} rows", file.getName(),
                file.getParentFile().getAbsolutePath(), results.size());
        return isSaved;
    }

    /**
     * Gets all the annotations that are covered by another annotation, classified
     * as "Exclude"
     * 
     * @throws DisruptedAtlasHierarchy if the current atlas hierarchy was disrupted,
     *                                 and it cannot find all the brain region
     *                                 organised according to the atlas's hierarchy
     */
    private Set<PathObject> getExcludedAnnotations() {
        if (this.atlasObject.getChildObjects().isEmpty())
            throw new DisruptedAtlasHierarchy(this.atlasObject);
        List<PathObject> excludeAnnotations = AtlasManager.getExclusionAnnotations(this.hierarchy);
        getLogger().info("Exclusion annotations: [{}]", BraiAn.join(excludeAnnotations, ", "));
        // We export all the annotations that are not "Exclude" or named "Root".
        // This serves as a downstream ~check~ that the slice has nothing else other
        // than the atlas annotations and the exclusions
        var otherAnnotations = this.hierarchy.getAnnotationObjects().stream()
                .filter(ann -> ann.getPathClass() != AtlasManager.EXCLUDE_CLASSIFICATION && ann != this.atlasObject)
                .toList();
        // Loop over exclusions that contain the annotations to be removed/excluded
        return excludeAnnotations.stream()
                .map(exclusion -> exclusion.getROI().getGeometry())
                .flatMap(exclusion -> otherAnnotations.stream()
                        .filter(ann -> ann.getROI().getGeometry().coveredBy(exclusion)))
                .collect(Collectors.toSet());
    }

    /**
     * Gets all the brain regions that should be excluded from further analysis due
     * to being missing or badly aligned to the image.
     * A brain region, in order to be excluded, must:
     * <ul>
     * <li>either be contained into a larger annotation, classified as "Exclude"
     * <li>or be <b>duplicated</b> (SHIFT+D) outside the Atlas's hierarchy, and then
     * classified as "Exclude"
     * </ul>
     * 
     * @return a set of brain regions' annotations that should be excluded.
     * @throws DisruptedAtlasHierarchy if the current atlas hierarchy was disrupted,
     *                                 and it cannot find all the brain region
     *                                 organised according to the atlas's hierarchy
     */
    public Set<PathObject> getExcludedBrainRegions() {
        Set<PathObject> annotationsToExcludeFlattened = this.getExcludedAnnotations();
        Set<PathObject> brainRegions = new HashSet<>(this.flatten());
        Set<PathObject> regionsToExcludeFlattened = annotationsToExcludeFlattened.stream()
                .filter(o -> o.getPathClass() != null && brainRegions.contains(o)).collect(Collectors.toSet());
        Set<PathObject> weirdExcludedAnnotations = annotationsToExcludeFlattened.stream()
                .filter(o -> !regionsToExcludeFlattened.contains(o)).collect(Collectors.toSet());
        if (!weirdExcludedAnnotations.isEmpty())
            getLogger().error(
                    "Annotations excluded outside atlas ontology will be ignored. Make sure these annotations weren't meant to be classified as  '{}': [{}]",
                    EXCLUDE_CLASSIFICATION.toString(),
                    BraiAn.join(weirdExcludedAnnotations, ", "));
        // remove 'child' annotations that are descendant of an excluded parent
        var regionsToExclude = new HashSet<>(regionsToExcludeFlattened);
        for (PathObject r1 : regionsToExcludeFlattened) {
            List<PathObject> descendants = AtlasManager.flattenObject(r1);
            for (PathObject r2 : regionsToExcludeFlattened)
                if (descendants.indexOf(r2) > 0) // in 0 there is r1 itself, the parent annotation
                    regionsToExclude.remove(r2);
        }
        return regionsToExclude;
    }

    /**
     * Saves a file containing, on each line, the name and hemisphere of the regions
     * to be excluded.
     * 
     * @param file the file where it should write to. Note that if the file exists,
     *             it will be overwritten
     * @return true if the file was correctly saved.
     * @throws DisruptedAtlasHierarchy if the current atlas hierarchy was disrupted,
     *                                 and it cannot find all the brain region
     *                                 organised according to the atlas's hierarchy
     * @see #getExcludedBrainRegions()
     * @see #saveResults(List, File)
     */
    public boolean saveExcludedRegions(File file) {
        if (this.atlasObject.getChildObjects().isEmpty())
            throw new DisruptedAtlasHierarchy(this.atlasObject);
        if (file.exists())
            if (!file.delete()) {
                getLogger().error("Could not delete previous exclusion file {}, the file could be locked.",
                        file.getName());
                return false;
            }
        QP.mkdirs(file.getAbsoluteFile().getParent());

        Set<PathObject> regionsToExcludeSet = this.getExcludedBrainRegions(); // NOTE: may return things that aren't
                                                                              // brain regions

        List<PathObject> regionsToExclude = regionsToExcludeSet.stream()
                .filter(o -> o.getPathClass() != null)
                .sorted(Comparator.comparing(o -> o.getPathClass().toString()))
                .toList();
        getLogger().info("Excluded regions: [{}]", BraiAn.join(regionsToExclude, ", "));

        QP.resetSelection();
        QP.selectObjects(regionsToExclude);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            for (PathObject region : regionsToExclude) {
                writer.write(region.getPathClass().toString());
                writer.newLine();
            }
        } catch (IOException e) {
            getLogger().error("Error saving excluded regions: {}", e.getMessage());
            return false;
        }
        getLogger().info("Exclusions '{}' Saved under '{}', contains {} rows", file.getName(),
                file.getParentFile().getAbsolutePath(), regionsToExclude.size());
        return true;
    }

    /**
     * Automatically excludes atlas regions that appear empty based on adaptive Otsu
     * thresholding.
     *
     * @param imageData            the image data
     * @param channelNames         the list of channel names to consider
     * @param useMaxAcrossChannels if true, a region is kept if its max intensity
     *                             across all channels
     *                             is above the threshold
     * @param thresholdMultiplier  multiplier applied to the calculated Otsu
     *                             threshold
     * @return a list of exclusion reports
     */
    public List<ExclusionReport> autoExcludeEmptyRegions(
            ImageData<BufferedImage> imageData,
            List<String> channelNames,
            boolean useMaxAcrossChannels,
            double thresholdMultiplier) {
        Objects.requireNonNull(imageData, "imageData");
        Objects.requireNonNull(channelNames, "channelNames");

        if (channelNames.isEmpty()) {
            return List.of();
        }

        // 1. Calculate Otsu thresholds for each channel
        Map<String, Integer> otsuThresholds = new HashMap<>();
        for (String channelName : channelNames) {
            try {
                ImageChannelTools channel = new ImageChannelTools(channelName, imageData);
                ImageProcessor ip = channel.getImageProcessor(AUTO_THRESHOLD_RESOLUTION_LEVEL);
                AutoThresholder thresholder = new AutoThresholder();
                int threshold = thresholder.getThreshold(AutoThresholder.Method.Otsu, ip.getHistogram());
                otsuThresholds.put(channelName, threshold);
                getLogger().info("Computed Otsu threshold for channel '{}': {}", channelName, threshold);
            } catch (Exception e) {
                getLogger().error("Failed to compute Otsu threshold for channel '{}': {}", channelName, e.getMessage());
            }
        }

        if (otsuThresholds.isEmpty()) {
            return List.of();
        }

        // 2. Gather intensities for all regions
        List<PathObject> regions = flatten().stream()
                .filter(o -> o instanceof PathAnnotationObject)
                .filter(o -> o != this.atlasObject)
                .filter(o -> {
                    String name = o.getName();
                    return name == null || (!"Root".equalsIgnoreCase(name));
                })
                .toList();

        // Avoid creating duplicate exclusions for regions already excluded
        Set<PathObject> alreadyExcludedData = getExcludedBrainRegions();

        // Map to store relevant intensity for each region
        Map<PathObject, Double> regionIntensities = new HashMap<>();
        List<Double> allIntensitiesForDistribution = new ArrayList<>();

        for (PathObject region : regions) {
            if (alreadyExcludedData.contains(region))
                continue;

            double bestIntensity = -1.0;
            for (String channelName : channelNames) {
                if (!otsuThresholds.containsKey(channelName))
                    continue;

                try {
                    double mean = getRegionMean(imageData, (PathAnnotationObject) region, channelName,
                            AUTO_THRESHOLD_RESOLUTION_LEVEL);
                    double normalized = mean / otsuThresholds.get(channelName);

                    if (useMaxAcrossChannels) {
                        if (normalized > bestIntensity)
                            bestIntensity = normalized;
                    } else {
                        bestIntensity = normalized;
                        break; // Only use first channel (Nuclei)
                    }
                } catch (Exception e) {
                    getLogger().warn("Failed to compute mean for region '{}' channel '{}'",
                            region.getName(), channelName, e.getMessage());
                }
            }

            if (bestIntensity >= 0) {
                regionIntensities.put(region, bestIntensity);
                allIntensitiesForDistribution.add(bestIntensity);
            }
        }

        if (allIntensitiesForDistribution.isEmpty())
            return List.of();

        // 3. Sort for percentile calculation
        Collections.sort(allIntensitiesForDistribution);

        List<ExclusionReport> reports = new ArrayList<>();
        String imageName = imageData.getServerMetadata().getName();

        for (Map.Entry<PathObject, Double> entry : regionIntensities.entrySet()) {
            PathObject region = entry.getKey();
            double intensity = entry.getValue();

            // intensity is "mean / Otsu"
            // Exclusion condition: mean < Otsu * multiplier => mean / Otsu < multiplier
            if (intensity < thresholdMultiplier) {
                PathAnnotationObject exclude = (PathAnnotationObject) PathObjects
                        .createAnnotationObject(region.getROI());
                exclude.setName(region.getName());
                exclude.setPathClass(EXCLUDE_CLASSIFICATION);

                // Calculate percentile rank (informational)
                int rank = Collections.binarySearch(allIntensitiesForDistribution, intensity);
                if (rank < 0)
                    rank = -(rank + 1);
                double percentile = (double) rank / allIntensitiesForDistribution.size() * 100.0;

                // Stamp measurements
                exclude.getMeasurementList().put("Auto-Exclude: Normalized Intensity", intensity);
                exclude.getMeasurementList().put("Auto-Exclude: Percentile Rank", percentile);

                this.hierarchy.addObject(exclude);

                reports.add(new ExclusionReport(
                        null,
                        null,
                        imageName,
                        exclude.getID(),
                        region.getName(),
                        percentile));
            }
        }

        return reports;
    }

    /**
     * Gets currently excluded annotations (classified as
     * {@link #EXCLUDE_CLASSIFICATION}).
     *
     * @param imageData the image data containing the hierarchy
     * @return a list of exclusion reports for all excluded regions
     */
    public List<ExclusionReport> getExcludedRegions(ImageData<BufferedImage> imageData) {
        Objects.requireNonNull(imageData, "imageData");
        String imageName = imageData.getServerMetadata().getName();
        return getExclusionAnnotations(this.hierarchy).stream()
                .filter(o -> o instanceof PathAnnotationObject)
                .map(o -> (PathAnnotationObject) o)
                .map(ann -> new ExclusionReport(
                        null,
                        null,
                        imageName,
                        ann.getID(),
                        ann.getName(),
                        ann.getMeasurementList().get("Auto-Exclude: Percentile Rank")))
                .toList();
    }

    private static double getRegionMean(
            ImageData<BufferedImage> imageData,
            PathAnnotationObject region,
            String channelName,
            int resolutionLevel) throws IOException {
        Objects.requireNonNull(imageData, "imageData");
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(channelName, "channelName");

        ImageServer<BufferedImage> server = imageData.getServer();
        ImageChannelTools channel = new ImageChannelTools(channelName, imageData);
        double downsample = server.getDownsampleForResolution(Math.min(server.nResolutions() - 1, resolutionLevel));
        RegionRequest request = RegionRequest.createInstance(server.getPath(), downsample, region.getROI());

        var pathImage = IJTools.convertToImagePlus(server, request);
        ImagePlus imp = pathImage.getImage();

        int ijChannel = channel.getnChannel() + 1; // ImageJ channels are 1-based
        imp.setC(ijChannel);

        ImageProcessor ip = imp.getChannelProcessor().duplicate();
        Roi ijRoi = IJTools.convertToIJRoi(region.getROI(), request);
        if (ijRoi != null) {
            ip.setRoi(ijRoi);
        }
        return ip.getStats().mean;
    }

    /**
     * Return whether the current atlas is split between left and right hemispheres.
     * Atlas hierarchies that were modified by deleting one of the two hemispheres,
     * are still recognised as split.
     * 
     * @return true if the current atlas is split between left and right
     *         hemispheres.
     * @throws DisruptedAtlasHierarchy if the current atlas hierarchy was disrupted,
     *                                 and it cannot find all the brain region
     *                                 organised according to the atlas's hierarchy
     *                                 whether the atlas was split between left and
     *                                 right.
     */
    public boolean isSplit() {
        if (this.atlasObject.getChildObjects().isEmpty())
            throw new DisruptedAtlasHierarchy(this.atlasObject);
        List<PathObject> roots = this.atlasObject.getChildObjects().stream()
                .filter(ann -> "root".equals(ann.getName()))
                .toList();
        return roots.stream()
                // anyMatch because if there are multiple "root" annotations, an
                .anyMatch(root -> {
                    Set<PathClass> hemispheres = flattenObject(root).stream()
                            .map(PathObject::getPathClass)
                            .filter(c -> c.isDerivedFrom(ABBA_LEFT) || c.isDerivedFrom(ABBA_RIGHT))
                            .map(PathClass::getParentClass)
                            .collect(Collectors.toSet());
                    if (hemispheres.size() == 2)
                        throw new DisruptedAtlasHierarchy(this.atlasObject);
                    return hemispheres.size() == 1;
                });
    }

    /**
     * Attempts to fix possible mistakes with the exclusions, such as:
     * <ul>
     * <li>Exclusion of the "Root" hierarchy annotation rather than a proper
     * region</li>
     * <li>If a region was excluded within the atlas hierarchy</li>
     * <li>If a region's classifications was removed</li>
     * </ul>
     * 
     * @throws DisruptedAtlasHierarchy if the current atlas hierarchy was disrupted,
     *                                 and it cannot find all the brain region
     *                                 organised according to the atlas's hierarchy
     */
    public void fixExclusions() {
        if (this.atlasObject.getChildObjects().isEmpty())
            throw new DisruptedAtlasHierarchy(this.atlasObject);
        if (this.atlasObject.getPathClass() == AtlasManager.EXCLUDE_CLASSIFICATION) {
            for (PathObject root : this.atlasObject.getChildObjects()) {
                PathObject duplicate = PathObjectTools.transformObject(root, null, true, true);
                duplicate.setPathClass(AtlasManager.EXCLUDE_CLASSIFICATION);
                this.hierarchy.addObject(duplicate);
            }
            this.atlasObject.setPathClass(null); // TODO: set the proper path class, like ABBA 0.3.2 does
        }
        this.flatten().stream()
                .filter(
                        region -> (region.getPathClass() != null
                                && region.getPathClass() == AtlasManager.EXCLUDE_CLASSIFICATION) ||
                                (region.getPathClass() == null && region != this.atlasObject))
                .forEach(
                        mistakenlyExcludedRegion -> this.fixMistakenlyExcludedRegion(mistakenlyExcludedRegion,
                                this.isSplit()));
    }

    private void fixMistakenlyExcludedRegion(PathObject mistakenlyExcludedRegion, boolean isSplit) {
        String regionName;
        if ((regionName = mistakenlyExcludedRegion.getName()) == null)
            throw new RuntimeException("Can't deduce the name for brain region '" + mistakenlyExcludedRegion + "'.");
        PathClass classification;
        if (isSplit)
            classification = PathClass.getInstance(this.getHemisphereOfMisclassifiedRegion(mistakenlyExcludedRegion),
                    regionName, null);
        else
            classification = PathClass.fromString(regionName);
        List<PathObject> wrongDuplicates = this.hierarchy.getRootObject().getChildObjects().stream().filter(
                ann -> regionName.equals(ann.getName()) && ann.getPathClass() == classification).toList();
        if (wrongDuplicates.isEmpty()) {
            PathObject duplicate = PathObjectTools.transformObject(mistakenlyExcludedRegion, null, true, true);
            this.hierarchy.addObject(duplicate);
            wrongDuplicates = List.of(duplicate);
        }
        for (PathObject wrongDuplicate : wrongDuplicates) {
            wrongDuplicate.setPathClass(AtlasManager.EXCLUDE_CLASSIFICATION);
        }
        mistakenlyExcludedRegion.setPathClass(classification);
    }

    private PathClass getHemisphereOfMisclassifiedRegion(PathObject region) {
        PathObject hemisphereObject = region;
        while (hemisphereObject.getParent() != this.atlasObject)
            hemisphereObject = hemisphereObject.getParent();
        Set<PathClass> hemisphere = flattenObject(hemisphereObject).stream()
                .map(PathObject::getPathClass)
                .filter(c -> c != null && c.isDerivedClass()
                        && (c.isDerivedFrom(ABBA_LEFT) || c.isDerivedFrom(ABBA_RIGHT)))
                .map(PathClass::getParentClass)
                .collect(Collectors.toSet());
        if (hemisphere.size() != 1)
            throw new RuntimeException("Can't deduce the hemisphere for '" + region + "'.");
        return hemisphere.iterator().next();
    }
}
