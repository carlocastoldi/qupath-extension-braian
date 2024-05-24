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

public class ChannelDetections extends AbstractDetections {
    private static final String FULL_IMAGE_DETECTIONS_NAME = "AllDetections";

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

    public ChannelDetections(String id, PathObjectHierarchy hierarchy) {
        super(id, List.of(PathClass.fromString(id)), hierarchy);
    }

    public ChannelDetections(ImageChannelTools channel, PathObjectHierarchy hierarchy) {
        this(channel.getName(), hierarchy);
    }

    /**
     * Computes the detections in "annotation".
     * @param channel
     * @param annotations
     * @param config
     * @param hierarchy
     * @return
     */
    public ChannelDetections(ImageChannelTools channel, Collection<PathAnnotationObject> annotations, WatershedCellDetectionConfig config, PathObjectHierarchy hierarchy) {
        this(channel, hierarchy);

        if(annotations == null || annotations.isEmpty()) {
            // throw new IllegalArgumentException("You must give at least one annotation on which to compute the detections");
            PathAnnotationObject fullImage = ChannelDetections.getFullImageDetectionAnnotation(hierarchy);
            annotations = List.of(fullImage);
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

    public ChannelDetections(ImageChannelTools channel, PathAnnotationObject annotation, WatershedCellDetectionConfig config, PathObjectHierarchy hierarchy) {
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
