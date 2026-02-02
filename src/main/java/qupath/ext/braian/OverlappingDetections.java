// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package qupath.ext.braian;

import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.projects.Project;
import qupath.lib.roi.interfaces.ROI;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class allows to compute and manage double/triple/multiple positive detections.
 * It does so by leveraging {@link AbstractDetections} interface
 */
public class OverlappingDetections extends AbstractDetections {
    /**
     * Delimiter used to build overlap class names (e.g. {@code Ch1~Ch2}).
     */
    public static final String OVERLAP_DELIMITER = "~";

    /**
     * Creates all the names of the possible overlaps between the given PathClasses names
     * @param primitiveClasses a list of PathClasses names
     * @return a list combinations of the given primitiveClasses, delimited by {@link OverlappingDetections#OVERLAP_DELIMITER}
     */
    public static List<String> createAllOverlappingClassNames(List<String> primitiveClasses) {
        if(primitiveClasses.isEmpty())
            return List.of();
        String first = primitiveClasses.get(0);
        List<String> others = primitiveClasses.subList(1, primitiveClasses.size());
        List<String> othersOverlappingClasses = createAllOverlappingClassNames(others);
        return Stream.concat(
                Stream.of(first),
                Stream.concat(
                        othersOverlappingClasses.stream(),
                        othersOverlappingClasses.stream().map(postfix -> first+OverlappingDetections.OVERLAP_DELIMITER+postfix))
        ).toList();
    }

    private static String createOverlappingClassName(String primary, List<String> others) {
        return Stream.concat(Stream.of(primary), others.stream())
                .collect(Collectors.joining(OverlappingDetections.OVERLAP_DELIMITER));
    }

    private static Collection<PathClass> getAllPossibleOverlappingClassifications(AbstractDetections control,
                                                                                  Collection<AbstractDetections> otherDetections) {
        if (otherDetections.isEmpty())
            throw new IllegalArgumentException("You have to overlap at least two detections; 'others' cannot be empty");
        return OverlappingDetections.createAllOverlappingClassNames(
                        otherDetections.stream().map(AbstractDetections::getId).toList())
                .stream()
                .map(name -> control.getId()+OverlappingDetections.OVERLAP_DELIMITER+name)
                .map(PathClass::fromString)
                .toList();
    }

    /**
     * Creates an instance of overlapping detections
     * @param control the detections used to check whether the other detections are overlapping between them and the control
     * @param others the other detections
     * @param compute if true, it will delete any previous overlap and compute the overlap between detections.
     *                If false, it will try to retrieve pre-computed ovarlappings
     * @param hierarchy where to find/compute the overlapping detections
     * @throws NoCellContainersFoundException if no pre-computed overlappings were found in the given hierarchy
     */
    public OverlappingDetections(AbstractDetections control,
                                  Collection<AbstractDetections> others,
                                  boolean compute, PathObjectHierarchy hierarchy) throws NoCellContainersFoundException {
        this(control, others, compute, hierarchy, null, null);
    }

    /**
     * Creates an instance of overlapping detections.
     *
     * @param control the reference detections used to check overlap against
     * @param others other detections to compare with {@code control}
     * @param compute if true, deletes any previous overlaps and recomputes them
     * @param hierarchy where to find/compute the overlapping detections
     * @param project the active QuPath project; may be null
     * @param qupath the QuPath GUI instance; may be null
     * @throws NoCellContainersFoundException if no pre-computed overlaps were found when {@code compute} is false
     */
    public OverlappingDetections(AbstractDetections control,
                                  Collection<AbstractDetections> others,
                                  boolean compute,
                                  PathObjectHierarchy hierarchy,
                                  Project<?> project,
                                  QuPathGUI qupath) throws NoCellContainersFoundException {
        super(control.getId(), getAllPossibleOverlappingClassifications(control, others), hierarchy, project, qupath);
        if (!compute)
            return;
        this.overlap(control, others);
        this.fireUpdate();
    }

    /**
     * Creates an instance based on pre-computed overlapping detections
     * @param control the detections used to check whether the other detections are overlapping between them and the control
     * @param others the other detections
     * @param hierarchy where to find the overlapping detections
     * @throws NoCellContainersFoundException if no pre-computed overlappings were found in the given hierarchy
     */
    public OverlappingDetections(AbstractDetections control,
                                  Collection<AbstractDetections> others,
                                  PathObjectHierarchy hierarchy) throws NoCellContainersFoundException {
        this(control, others, false, hierarchy, null, null);
    }

    /**
     * Creates an instance based on pre-computed overlapping detections.
     *
     * @param control the reference detections used to check overlap against
     * @param others other detections to compare with {@code control}
     * @param hierarchy where to find the overlapping detections
     * @param project the active QuPath project; may be null
     * @param qupath the QuPath GUI instance; may be null
     * @throws NoCellContainersFoundException if no pre-computed overlaps were found in the given hierarchy
     */
    public OverlappingDetections(AbstractDetections control,
                                  Collection<AbstractDetections> others,
                                  PathObjectHierarchy hierarchy,
                                  Project<?> project,
                                  QuPathGUI qupath) throws NoCellContainersFoundException {
        this(control, others, false, hierarchy, project, qupath);
    }

    /**
     * @return the name used for the container annotations storing overlap detections
     */
    @Override
    public String getContainersName() {
        return this.getId()+" overlaps";
    }

    private void overlap(AbstractDetections control, Collection<AbstractDetections> otherDetections) {
        List<PathDetectionObject> overlaps = control.toStream().flatMap(cell -> copyDetectionIfOverlapping(cell, control, otherDetections).stream()).toList();
        this.getHierarchy().addObjects(overlaps);
        // add all duplicated overlapping cells to a new annotation
        for (PathAnnotationObject container : control.getContainers()) {
            PathAnnotationObject containerParent = (PathAnnotationObject) container.getParent();
            PathAnnotationObject overlapsContainer = this.createContainer(containerParent, true);
            ROI containerRoi = overlapsContainer.getROI();
            overlaps.stream()
                    .filter(overlap -> containerRoi.contains(overlap.getROI().getCentroidX(), overlap.getROI().getCentroidY()))
                    .forEach(overlap -> this.getHierarchy().addObjectBelowParent(overlapsContainer, overlap, false));
        }
    }

    private static Optional<PathDetectionObject> copyDetectionIfOverlapping(PathDetectionObject cell,
                                                                            AbstractDetections control,
                                                                            Collection<AbstractDetections> otherDetections) {
        List<String> overlappingDetectionsIds = otherDetections.stream()
                .filter(other -> other.getOverlappingObjectIfPresent(cell).isPresent())
                .map(AbstractDetections::getId)
                .toList();
        if (overlappingDetectionsIds.isEmpty())
            return Optional.empty();
        String className = createOverlappingClassName(control.getId(), overlappingDetectionsIds);
        PathClass overlapClass = PathClass.fromString(className);
        PathDetectionObject cellCopy = (PathDetectionObject) PathObjects.createDetectionObject(cell.getROI(), overlapClass);
        return Optional.of(cellCopy);
    }
}
