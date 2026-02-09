// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package qupath.ext.braian;

import qupath.ext.braian.config.WatershedCellDetectionConfig;
import qupath.lib.io.GsonTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.plugins.CommandLineTaskRunner;
import qupath.lib.plugins.PathPlugin;
import qupath.lib.projects.Project;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;

import java.lang.reflect.Constructor;
import java.util.*;

/**
 * This class allows to manage detections computed with {@link qupath.imagej.detect.cells.WatershedCellDetection}
 * on a given image channel. It does so by leveraging {@link AbstractDetections} interface
 */
public class ChannelDetections extends AbstractDetections {
    public static final String FULL_IMAGE_DETECTIONS_NAME = "AllDetections";

    /**
     * Returns the annotation used when working with detections on the whole image
     * @param hierarchy where to find/compute the detections
     * @return the full image detection named as {@link ChannelDetections#FULL_IMAGE_DETECTIONS_NAME}
     */
    public static PathAnnotationObject getFullImageDetectionAnnotation(ImageData<?> imageData,
                                                                       PathObjectHierarchy hierarchy) {
        if (imageData == null) {
            throw new IllegalArgumentException("ImageData is required to create a full image annotation");
        }
        List<PathObject> fullImageAnnotations = hierarchy.getAnnotationObjects().stream()
                .filter(a -> FULL_IMAGE_DETECTIONS_NAME.equals(a.getName())).toList();
        switch (fullImageAnnotations.size()) {
            case 0:
                PathAnnotationObject fullImageAnnotation = createFullImageAnnotation(imageData, hierarchy, true);
                fullImageAnnotation.setName(FULL_IMAGE_DETECTIONS_NAME);
                return fullImageAnnotation;
            case 1:
                return (PathAnnotationObject) fullImageAnnotations.get(0);
            default:
                throw new RuntimeException("There are multiple annotations called '"+FULL_IMAGE_DETECTIONS_NAME+"'. Delete them!");
        }
    }

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
    public ChannelDetections(String channelName,
                             PathObjectHierarchy hierarchy,
                             Project<?> project,
                             QuPathGUI qupath) throws NoCellContainersFoundException {
        super(channelName, List.of(createClassification(channelName)), hierarchy, project, qupath);
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
    public ChannelDetections(ImageChannelTools channel,
                             PathObjectHierarchy hierarchy,
                             Project<?> project,
                             QuPathGUI qupath) throws NoCellContainersFoundException {
        this(channel.getName(), hierarchy, project, qupath);
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
                             PathObjectHierarchy hierarchy,
                             ImageData<?> imageData,
                             Project<?> project,
                             QuPathGUI qupath) throws NoCellContainersFoundException {
        this(channel, hierarchy, project, qupath);

        if(annotations == null) {
            PathAnnotationObject fullImage = ChannelDetections.getFullImageDetectionAnnotation(imageData, hierarchy);
            annotations = List.of(fullImage);
        } else if (annotations.isEmpty()) {
            throw new IllegalArgumentException("You must give at least one annotation on which to compute the detections");
        }
        Map<String, ?> params = config.build(channel);
        // TODO: check if the given annotations overlap. If they do, throw an error as that would duplicate detections
        List<PathAnnotationObject> containers = annotations.stream().map(annotation -> {
            annotation.setLocked(true);
            PathAnnotationObject container = this.createContainer(annotation, true);
            return ChannelDetections.compute(container, params, imageData);
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
                             PathObjectHierarchy hierarchy,
                             ImageData<?> imageData,
                             Project<?> project,
                             QuPathGUI qupath) throws NoCellContainersFoundException {
        this(channel,
                annotation != null ? List.of(annotation) : null,
                config,
                hierarchy,
                imageData,
                project,
                qupath);
    }

    private static PathAnnotationObject compute(PathAnnotationObject container,
                                                Map<String,?> params,
                                                ImageData<?> imageData) {
        if (imageData == null) {
            throw new IllegalArgumentException("ImageData is required to run cell detection");
        }
        imageData.getHierarchy().getSelectionModel().setSelectedObject(container);
        try {
            runPlugin("qupath.imagej.detect.cells.WatershedCellDetection", imageData, params);
            ChannelDetections.getChildrenDetections(container).forEach(detection -> detection.setPathClass(container.getPathClass()));
            return container;
        } catch (InterruptedException e) {
            BraiAnExtension.logger.warn("Watershed cell detection interrupted. Returning empty list of detections for "+container+"!");
            return container;
        }
    }

    private static PathAnnotationObject createFullImageAnnotation(ImageData<?> imageData,
                                                                  PathObjectHierarchy hierarchy,
                                                                  boolean setSelected) {
        ImageServer<?> server = imageData.getServer();
        PathObject pathObject = PathObjects.createAnnotationObject(
                ROIs.createRectangleROI(0, 0, server.getWidth(), server.getHeight(), ImagePlane.getPlane(0, 0))
        );
        hierarchy.addObject(pathObject);
        if (setSelected) {
            hierarchy.getSelectionModel().setSelectedObject(pathObject);
        }
        return (PathAnnotationObject) pathObject;
    }

    private static boolean runPlugin(final String className,
                                     final ImageData<?> imageData,
                                     final Map<String, ?> args) throws InterruptedException {
        var json = args == null ? "" : GsonTools.getInstance().toJson(args);
        return runPlugin(className, imageData, json);
    }

    private static boolean runPlugin(final String className,
                                     final ImageData<?> imageData,
                                     final String args) throws InterruptedException {
        if (imageData == null)
            return false;

        try {
            Class<?> cPlugin = ChannelDetections.class.getClassLoader().loadClass(className);
            Constructor<?> cons = cPlugin.getConstructor();
            final PathPlugin plugin = (PathPlugin) cons.newInstance();
            return plugin.runPlugin(new CommandLineTaskRunner(), imageData, args);
        } catch (Exception e) {
            BraiAnExtension.logger.error("Unable to run plugin {}", className, e);
            return false;
        }
    }

    @Override
    public String getContainersName() {
        return this.getId()+" cells";
    }
}
