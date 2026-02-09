// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package qupath.ext.braian;

import qupath.ext.braian.config.WatershedCellDetectionConfig;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.scripting.QP;

import java.util.*;

/**
 * This class allows to manage detections computed with {@link qupath.imagej.detect.cells.WatershedCellDetection}
 * on a given image channel. It does so by leveraging {@link AbstractDetections} interface
 */
public class ChannelDetections extends AbstractDetections {
    /**
     * Name used for synthetic full-image annotation that hosts detections.
     */
    public static final String FULL_IMAGE_DETECTIONS_NAME = "AllDetections";

    /**
     * Returns the annotation used when working with detections on the whole image
     * @param hierarchy where to find/compute the detections
     * @return the full image detection named as {@link ChannelDetections#FULL_IMAGE_DETECTIONS_NAME}
     */
    public static PathAnnotationObject getFullImageDetectionAnnotation(PathObjectHierarchy hierarchy) {
        List<PathObject> fullImageAnnotations = hierarchy.getAnnotationObjects().stream()
                .filter(a -> FULL_IMAGE_DETECTIONS_NAME.equals(a.getName())).toList();
        switch (fullImageAnnotations.size()) {
            case 0:
                PathAnnotationObject fullImageAnnotation = (PathAnnotationObject) QP.createFullImageAnnotation(true);
                fullImageAnnotation.setName(FULL_IMAGE_DETECTIONS_NAME);
                return fullImageAnnotation;
            case 1:
                return (PathAnnotationObject) fullImageAnnotations.get(0);
            default:
                throw new RuntimeException("There are multiple annotations called '"+FULL_IMAGE_DETECTIONS_NAME+"'. Delete them!");
        }
    }

    /**
     * Creates the base classification label used for detections from one channel.
     *
     * @param channelName channel name
     * @return path class with same name as channel
     */
    public static PathClass createClassification(String channelName) {
        return PathClass.fromString(channelName);
    }

    /**
     * Creates an instance based on pre-computed cell detections.
     * It selects all detections that are computed in a container identified by {@code channelName}.
     * @param channelName the name of the channel to which the detections are linked to
     * @param hierarchy where to find the detections
     * @throws NoCellContainersFoundException if no pre-computed detection was found in the given hierarchy
     * @see #getContainersName()
     * @see AbstractDetections
     */
    public ChannelDetections(String channelName, PathObjectHierarchy hierarchy) throws NoCellContainersFoundException {
        super(channelName, List.of(createClassification(channelName)), hierarchy);
    }

    /**
     * Creates an instance based on pre-computed cell detections.
     * It selects all detections that are computed in a container identified by {@code channel}.
     * @param channel the channel to which the detections are linked to
     * @param hierarchy where to find the detections
     * @throws NoCellContainersFoundException if no pre-computed detection was found in the given hierarchy
     * @see #getContainersName()
     * @see AbstractDetections
     */
    public ChannelDetections(ImageChannelTools channel, PathObjectHierarchy hierarchy) throws NoCellContainersFoundException {
        this(channel.getName(), hierarchy);
    }

    /**
     * Computes the detections using {@link qupath.imagej.detect.cells.WatershedCellDetection} algorithm inside the given annotations
     * @param channel the channel to which the detections are linked to
     * @param annotations the annotations inside of which to compute the detections. If null or empty, it will compute them on the whole image
     * @param config parameters to give to the {@link qupath.imagej.detect.cells.WatershedCellDetection}
     * @param hierarchy where to compute the detections
     * @throws NoCellContainersFoundException
     * @see #getFullImageDetectionAnnotation(PathObjectHierarchy)
     */
    public ChannelDetections(ImageChannelTools channel,
                             Collection<PathAnnotationObject> annotations,
                             WatershedCellDetectionConfig config,
                             PathObjectHierarchy hierarchy) throws NoCellContainersFoundException {
        this(channel, hierarchy);

        if(annotations == null) {
            PathAnnotationObject fullImage = ChannelDetections.getFullImageDetectionAnnotation(hierarchy);
            annotations = List.of(fullImage);
        } else if (annotations.isEmpty()) {
            throw new IllegalArgumentException("You must give at least one annotation on which to compute the detections");
        }
        Map<String, ?> params = config.build(channel);
        // TODO: check if the given annotations overlap. If they do, throw an error as that would duplicate detections
        List<PathAnnotationObject> containers = annotations.stream().map(annotation -> {
            annotation.setLocked(true);
            PathAnnotationObject container = this.createContainer(annotation, true);
            return ChannelDetections.compute(container, params);
        }).toList();

        this.fireUpdate();
    }

    /**
     * Computes the detections using {@link qupath.imagej.detect.cells.WatershedCellDetection} algorithm inside the given annotations
     * @param channel the channel to which the detections are linked to
     * @param annotation the annotation inside of which to compute the detections. If null, it will compute them on the whole image
     * @param config parameters to give to the {@link qupath.imagej.detect.cells.WatershedCellDetection}
     * @param hierarchy where to compute the detections
     * @throws NoCellContainersFoundException
     * @see #getFullImageDetectionAnnotation(PathObjectHierarchy)
     */
    public ChannelDetections(ImageChannelTools channel,
                             PathAnnotationObject annotation,
                             WatershedCellDetectionConfig config,
                             PathObjectHierarchy hierarchy) throws NoCellContainersFoundException {
        this(channel, annotation != null ? List.of(annotation) : null, config, hierarchy);
    }

    private static PathAnnotationObject compute(PathAnnotationObject container, Map<String,?> params) {
        QP.selectObjects(container);
        try {
            QP.runPlugin("qupath.imagej.detect.cells.WatershedCellDetection", params);
            ChannelDetections.getChildrenDetections(container).forEach(detection -> detection.setPathClass(container.getPathClass()));
            return container;
        } catch (InterruptedException e) {
            BraiAnExtension.logger.warn("Watershed cell detection interrupted. Returning empty list of detections for "+container+"!");
            return container;
        }
    }

    @Override
    public String getContainersName() {
        return this.getId()+" cells";
    }
}
