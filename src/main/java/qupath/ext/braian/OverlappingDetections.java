// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package qupath.ext.braian;

import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.roi.interfaces.ROI;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class OverlappingDetections extends AbstractDetections {
    private static final String OVERLAP_DELIMITER = "~";

    /**
     * Creates all the names of the possible overlaps between the given PathClasses names
     * @param primitiveClasses a list of PathClasses names
     * @return a list combinations of the given primitiveClasses, delimited by ChannelDetections.OVERLAP_DELIMITER
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

    private static Collection<PathClass> getAllPossibleOverlappingClassifications(AbstractDetections control, Collection<AbstractDetections> otherDetections) {
        return OverlappingDetections.createAllOverlappingClassNames(
                        otherDetections.stream().map(AbstractDetections::getId).toList())
                .stream()
                .map(name -> control.getId()+OverlappingDetections.OVERLAP_DELIMITER+name)
                .map(PathClass::fromString)
                .toList();
    }

    public OverlappingDetections(AbstractDetections control, Collection<AbstractDetections> others, boolean compute, PathObjectHierarchy hierarchy) {
        super(control.getId(), getAllPossibleOverlappingClassifications(control, others), hierarchy);
        if (!compute)
            return;
        this.overlap(control, others);
        this.fireUpdate();
    }

    public OverlappingDetections(AbstractDetections control, Collection<AbstractDetections> others, PathObjectHierarchy hierarchy) {
        this(control, others, false, hierarchy);
    }

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