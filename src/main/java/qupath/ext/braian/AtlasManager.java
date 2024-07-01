// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package qupath.ext.braian;

import qupath.ext.braian.utils.BraiAn;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.measure.ObservableMeasurementTableData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.projects.ProjectImageEntry;
import qupath.lib.scripting.QP;

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

class ImportedAtlasNotFound extends RuntimeException {
    public ImportedAtlasNotFound() {
        super("No previously imported atlas found. Use ABBA to align the slice to an atlas and import the brain annotations in the QuPath project ");
    }
}

/**
 * This class helps managing and exporting results for each brain region. It works closely with ABBA's QuPath extension.
 */
public class AtlasManager {
    public final static String um = GeneralTools.micrometerSymbol();
    public final static PathClass EXCLUDE_CLASSIFICATION = PathClass.fromString("Exclude");

    /**
     * Checks whether an ABBA atlas was previously imported.
     * @param hierarchy where to search for the atlas
     * @return true if an ABBA atlas was previously imported
     */
    public static boolean isImported(PathObjectHierarchy hierarchy) {
        return !AtlasManager.search(hierarchy).isEmpty();
    }

    private static List<PathObject> search(PathObjectHierarchy hierarchy) {
        return hierarchy.getAnnotationObjects()
                .stream()
                .filter(o -> "Root".equals(o.getName())) //&& o.getPathClass() != null && o.getPathClass().equals(atlasClass))
                .toList();
    }

    private static Stream<PathObject> flattenObjectStream(PathObject parent) {
        return Stream.concat(
                Stream.of(parent),
                parent.getChildObjects().stream().flatMap(AtlasManager::flattenObjectStream)
        );
    }

    private static List<PathObject> flattenObject(PathObject parent) {
        return flattenObjectStream(parent).toList();
    }

    private static List<String> getDetectionsMeasurements(List<AbstractDetections> detections) {
        // TODO: should avoid resorting to QP to get the server metadata
        var cal = QP.getCurrentServer().getMetadata().getPixelCalibration();
        if (!um.equals(cal.getPixelWidthUnit()) || !um.equals(cal.getPixelHeightUnit()))
            throw new RuntimeException("FAILED to export results. Expected image pixel units to be in 'µm', instead got them in '"+
                    cal.getPixelWidthUnit()+"' and '"+cal.getPixelWidthUnit()+"'. Try setting it with setPixelSizeMicrons()");
        Stream<String> genericMeasurements = Stream.of("Name", "Classification", "Area "+um+"^2", "Num Detections");
        Stream<String> detectionsMeasurements = detections.stream().flatMap(d -> d.getDetectionsPathClasses().stream().map(pc -> "Num "+pc.getName()));
        return Stream.concat(genericMeasurements, detectionsMeasurements).toList();
    }

    private static List<PathObject> getExclusionAnnotations(PathObjectHierarchy hierarchy) {
        return hierarchy.getAnnotationObjects().stream()
                .filter( ann -> AtlasManager.EXCLUDE_CLASSIFICATION.equals(ann.getPathClass()) )
                .toList();
    }

    private final PathObject atlasObject;
    private final PathObjectHierarchy hierarchy;

    /**
     * @param hierarchy where to search for the atlas
     */
    public AtlasManager(PathObjectHierarchy hierarchy) {
        this.hierarchy = hierarchy;
        List<PathObject> atlases = AtlasManager.search(hierarchy);
        if (atlases.isEmpty())
            throw new ImportedAtlasNotFound();
        this.atlasObject = atlases.get(0);
        if (atlases.size()>1)
            getLogger().warn("Several imported atlases have been found. Selecting: {}", this.atlasObject);
    }

    /**
     * Flattens the atlas's ontology into a list of annotations
     * @return the list of all brain regions in the atlas
     */
    public List<PathObject> flatten() {
        return AtlasManager.flattenObject(this.atlasObject);
    }

    /**
     * Saves a TSV file containing data for each bran region of the atlas.
     * Namely, Image name, brain region name, hemisphere, area in µm², number of detection for each of the given types
     * @param detections the list of detection of which to gather the data for each region
     * @param file the file where it should write to. Note that if the file exists, it will be overwritten
     */
    // Olivier Burri <https://github.com/lacan> wrote mostly of this function and published under Apache-2.0 license for qupath-extension-biop
    public boolean saveResults(List<AbstractDetections> detections, File file) {
        if (file.exists())
            if(!file.delete()) {
                getLogger().error("Could not delete previous results file {}, the file could be locked.", file.getName());
                return false;
            }
        QP.mkdirs(file.getAbsoluteFile().getParent());

        // We use a ResultsTable to store the data
        ResultsTable results = new ResultsTable();

        ObservableMeasurementTableData ob = new ObservableMeasurementTableData();
            List<PathObject> brainRegions = this.flatten();
        // This line creates all the measurements
        ob.setImageData(QP.getCurrentImageData(), brainRegions);

        ProjectImageEntry<BufferedImage> entry = QP.getProjectEntry();

        String rawImageName = entry.getImageName();
        double numericValue;

        // Add value for each selected object
        for (PathObject brainRegion : brainRegions) {
            results.incrementCounter();
            results.addValue("Image Name", rawImageName);

            // Check if image has associated metadata and add it as columns
            if (!entry.getMetadataKeys().isEmpty()) {
                Collection<String> keys = entry.getMetadataKeys();
                for (String key : keys) {
                    results.addValue("Metadata_" + key, entry.getMetadataValue(key));
                }
            }

            // Then we can add the results the user requested
            // Because the Mu is sometimes poorly formatted, we remove them in favor of a 'u'
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
        getLogger().info("Results '{}' Saved under '{}', contains {} rows", file.getName(), file.getParentFile().getAbsolutePath(), results.size());
        return isSaved;
    }

    /**
     * Gets all the brain regions that should be excluded from further analysis due to being missing or badly aligned to the image.
     * A brain region, in order to be excluded, must:
     * <ul>
     *   <li>either be contained into a larger annotation, classified as "Exclude"
     * 	 <li>or be <b>duplicated</b> (SHIFT+D) outside the Atlas's hierarchy, and then classified as "Exclude"
     * </ul>
     * @return a set of brain regions' annotations that should be excluded
     */
    public Set<PathObject> getExcludedBrainRegions() {
        List<PathObject> excludeAnnotations = AtlasManager.getExclusionAnnotations(this.hierarchy);
        getLogger().info("Exclusion annotations: [{}]", BraiAn.join(excludeAnnotations, ", "));
        // We export all the annotations that are not "Exclude" or named "Root".
        // This serves as a downstream ~check~ that the slice has nothing else other than the atlas annotations and the exclusions
        var otherAnnotations = this.hierarchy.getAnnotationObjects().stream()
                .filter( ann -> !(AtlasManager.EXCLUDE_CLASSIFICATION.equals(ann.getPathClass()) || ann.equals(this.atlasObject)))
                .toList();
        // Loop over exclusions that contain the annotations to be removed/excluded
        Set<PathObject> regionsToExcludeFlattened = excludeAnnotations.stream()
                .map(exclusion -> exclusion.getROI().getGeometry())
                .flatMap(exclusion -> otherAnnotations.stream().filter(ann -> ann.getROI().getGeometry().coveredBy(exclusion)))
                .collect(Collectors.toSet());
        // remove 'child' annotations that are descendant of an excluded parent
        var regionsToExclude = new HashSet<>(regionsToExcludeFlattened);
        for (PathObject r1: regionsToExcludeFlattened) {
            List<PathObject> descendants = AtlasManager.flattenObject(r1);
            for (PathObject r2: regionsToExcludeFlattened)
                if (descendants.indexOf(r2) > 0) // in 0 there is r1 itself, the parent annotation
                    regionsToExclude.remove(r2);
        }
        return regionsToExclude;
    }

    /**
     * Saves a file containing, on each line, the name and hemisphere of the regions to be excluded.
     * @param file the file where it should write to. Note that if the file exists, it will be overwritten
     * @see #getExcludedBrainRegions()
     * @see #saveResults(List, File)
     */
    public boolean saveExcludedRegions(File file) {
        if (file.exists())
            if(!file.delete()) {
                getLogger().error("Could not delete previous exclusion file {}, the file could be locked.", file.getName());
                return false;
            }
        QP.mkdirs(file.getAbsoluteFile().getParent());

        Set<PathObject> regionsToExcludeSet = this.getExcludedBrainRegions();
        List<PathObject> regionsToExclude = regionsToExcludeSet.stream()
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
        getLogger().info("Exclusions '{}' Saved under '{}', contains {} rows", file.getName(), file.getParentFile().getAbsolutePath(), regionsToExclude.size());
        return true;
    }
}
