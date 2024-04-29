package qupath.ext.braian;

import qupath.ext.braian.utils.BraiAn;
import qupath.lib.classifiers.object.ObjectClassifier;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.roi.interfaces.ROI;
import qupath.lib.scripting.QP;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static qupath.lib.scripting.QP.*;
import static qupath.lib.scripting.QP.getPathClass;

class EmptyDetectionsException extends RuntimeException {
    public EmptyDetectionsException() {
        super("No detection was pre-computed in the given image! You first need to call ChannelDetections.compute()");
    }
}

public class ChannelDetections {
    // private static Map<ImageChannelTools, ChannelDetections> existingDetections = new ConcurrentHashMap<ImageChannelTools, ChannelDetections>();
    // private static final UUID secret = UUID.randomUUID();
    private static final int BBH_MAX_DEPTH = 6;
    private static final String FULL_IMAGE_DETECTIONS_NAME = "AllDetections";
    private static final String OVERLAP_DELIMITER = "~";

    /*
    public static ChannelDetections getInstance(String id, PathObjectHierarchy hierarchy) {
        ChannelDetections detections = existingDetections.getOrDefault(id, null);
        if (detections != null)
            return detections;
        synchronized (existingDetections) {
            return existingDetections.computeIfAbsent(id, name -> new ChannelDetections(secret, name, hierarchy));
        }
    }
    */

    public static PathAnnotationObject getFullImageDetectionAnnotation(PathObjectHierarchy hierarchy) {
        List<PathObject> fullImageAnnotations = hierarchy.getAnnotationObjects().stream()
                .filter(a -> FULL_IMAGE_DETECTIONS_NAME.equals(a.getName())).toList();
        switch (fullImageAnnotations.size()) {
            case 0:
                PathAnnotationObject fullImageAnnotation = (PathAnnotationObject) createFullImageAnnotation(true);
                fullImageAnnotation.setName(FULL_IMAGE_DETECTIONS_NAME);
                return fullImageAnnotation;
            case 1:
                return (PathAnnotationObject) fullImageAnnotations.get(0);
            default:
                throw new RuntimeException("There are multiple annotations called '"+FULL_IMAGE_DETECTIONS_NAME+"'. Delete them!");
        }
    }

    public static ChannelDetections compute(ImageChannelTools channel, WatershedCellDetectionParameters params, PathAnnotationObject annotation, PathObjectHierarchy hierarchy) {
        return ChannelDetections.compute(channel, params, annotation != null ? List.of(annotation) : null, hierarchy);
    }

    public static ChannelDetections compute(ImageChannelTools channel, WatershedCellDetectionParameters params, Collection<PathAnnotationObject> annotations, PathObjectHierarchy hierarchy) {
        if(annotations == null || annotations.isEmpty()) {
            // throw new IllegalArgumentException("You must give at least one annotation on which to compute the detections");
            PathAnnotationObject fullImage = ChannelDetections.getFullImageDetectionAnnotation(hierarchy);
            annotations = List.of(fullImage);
        }
        // TODO: check if the given annotations overlap. If they do, throw an error as that would duplicate annotations
        List<PathAnnotationObject> containers = annotations.stream().map(ann -> ChannelDetections.computeInside(channel, ann, params, hierarchy)).toList();
        return new ChannelDetections(channel, hierarchy);
    }

    /**
     * Computes the detections computed in "annotation".
     * @param params
     * @param annotations
     * @return
     * @throws InterruptedException
     */
    private static PathAnnotationObject computeInside(ImageChannelTools channel, PathAnnotationObject annotation, WatershedCellDetectionParameters params, PathObjectHierarchy hierarchy) {
        annotation.setLocked(true);
        // TODO: add the commented lines to a function calling getDetections() on all ImageChannelTools
        // BraiAnExtension.logger.info("The annotation selected for the detections is: "+annotation);
        // if(annotation.hasChildObjects())
        //     this.hierarchy.removeObjects(annotation.getChildObjects(), false);
        PathAnnotationObject container = ChannelDetections.createBasicContainer(annotation, channel, hierarchy);
        selectObjects(container);
        try {
            params.setDetectionImage(channel.getName());
            QP.runPlugin("qupath.imagej.detect.cells.WatershedCellDetection", params.toMap());
            ChannelDetections.getChildrenDetections(container).forEach(detection -> detection.setPathClass(container.getPathClass()));
            return container;
        } catch (InterruptedException e) {
            BraiAnExtension.logger.warn("Watershed cell detection interrupted. Returning empty list of detections for "+annotation+"!");
            return container;
        }
    }

    /**
     * Creates a duplicate child annotation to and sets it tags it as a detectionContainer for the current channel
     * @param annotation
     * @return
     */
    private static PathAnnotationObject createBasicContainer(PathAnnotationObject annotation, ImageChannelTools ch, PathObjectHierarchy hierarchy) {
        String name = ch.getName();
        return createContainer(annotation, name, ChannelDetections.getPathClass(name), hierarchy);
    }

    private static PathAnnotationObject createContainer(PathAnnotationObject annotation, String name, PathClass classification, PathObjectHierarchy hierarchy) {
        QP.setSelectedObject(annotation);
        QP.duplicateSelectedAnnotations();
        PathAnnotationObject duplicate = (PathAnnotationObject) QP.getSelectedObject();
        duplicate.setName(name);
        duplicate.setPathClass(classification);
        BraiAn.populatePathClassGUI(classification);
        hierarchy.addObjectBelowParent(annotation, duplicate, true);
        duplicate.setLocked(true);
        return duplicate;
    }

    private static String getBasicContainersName(String id) {
        return id+" cells";
    }

    private static String getOverlapContainersName(String id) {
        return id+" overlaps";
    }

    private static PathClass getPathClass(String id) {
        return PathClass.fromString(id);
    }

    /**
     * returns the detections inside the given annotation
     * @param annotation
     * @param all
     * @return
     */
    private static Stream<PathDetectionObject> getDetectionsInside(PathAnnotationObject annotation, PathObjectHierarchy hierarchy) {
        return hierarchy.getObjectsForROI(PathDetectionObject.class, annotation.getROI())
                .stream()
                .map(o -> (PathDetectionObject) o);
    }

    private static Stream<PathDetectionObject> getChildrenDetections(PathAnnotationObject annotation) {
        return annotation.getChildObjects().stream()
                .filter(PathObject::isDetection)
                .map(object -> (PathDetectionObject) object);
    }


    private final PathObjectHierarchy hierarchy;
    private final String id;
    private final Function<String,String> id2containerName;
    private List<PathAnnotationObject> containers;
    private BoundingBoxHierarchy bbh;

    public ChannelDetections(ImageChannelTools channel, PathObjectHierarchy hierarchy) {
        this(channel.getName(), ChannelDetections::getOverlapContainersName, hierarchy);
    }

    // private ChannelDetections(UUID otherSecret, String id, PathObjectHierarchy hierarchy) {
    private ChannelDetections(String id, Function<String,String> id2containerName, PathObjectHierarchy hierarchy) {
        // boolean all;
        this.hierarchy = hierarchy;
        this.id = id;
        this.id2containerName = id2containerName;
        fireUpdate();
    }

    public void fireUpdate() {
        this.containers = this.searchContainers();
        List<PathDetectionObject> cells = this.getContainersDetections(false);
        this.bbh = new BoundingBoxHierarchy(cells, this.BBH_MAX_DEPTH);
    }

    public void applyClassifiers(Map<ObjectClassifier, List<PathAnnotationObject>> partialClassifiers, ImageData imageData) {
        List<PathDetectionObject> cells = new ArrayList<>();
        partialClassifiers.forEach((classifier, annotations) -> {
            cells.addAll(this.classifyInside(classifier, annotations, imageData));
        });
        this.bbh = new BoundingBoxHierarchy(cells, this.BBH_MAX_DEPTH);
    }

    private List<PathDetectionObject> classifyInside(ObjectClassifier classifier, Collection<PathAnnotationObject> annotations, ImageData imageData) {
        List<PathDetectionObject> cells;
        if(annotations == null || annotations.isEmpty())
            cells = this.getContainersDetections(true); // get ALL detections. Even those there were discarded
        else
            cells = annotations.stream()
                    .flatMap(a -> ChannelDetections.getDetectionsInside(a, this.hierarchy))
                    .filter(detection -> this.hasChannelClass(detection, true))
                    .toList();
        // if cells is empty, nothing bad should happen
        if (classifier.classifyObjects(imageData, cells, true) > 0)
            imageData.getHierarchy().fireObjectClassificationsChangedEvent(classifier, cells);
        PathClass discardedPC = this.getDiscardedDetectionsPathClass();
        BraiAn.populatePathClassGUI(discardedPC);
        return cells.stream().filter(d -> this.hasChannelClass(d, false)).toList();
    }

    public ChannelDetections overlap(List<ChannelDetections> otherDetections) {
        List<PathObject> overlaps = this.bbh.toStream().flatMap( cell -> {
            List<String> idWithOverlaps = otherDetections.stream()
                    .filter(other -> other.bbh.getOverlappingObjectIfPresent(cell).isPresent())
                    .map(other -> other.getId())
                    .toList();
            if(idWithOverlaps.size() == 0)
                return Stream.empty();
            String className = Stream.concat(Stream.of(this.getId()), idWithOverlaps.stream())
                    .collect(Collectors.joining(ChannelDetections.OVERLAP_DELIMITER));
            PathObject cellCopy = PathObjects.createDetectionObject(cell.getROI(), getPathClass(className));
            return Stream.of(cellCopy);
        }).toList();
        this.hierarchy.addObjects(overlaps);
        PathClass channelClass = this.getPathClass();
        // add all duplicated overlapping cells to a new annotation
        for (PathAnnotationObject container : this.containers) {
            String overlapContainerName = ChannelDetections.getOverlapContainersName(this.id);
            PathAnnotationObject containerParent = (PathAnnotationObject) container.getParent();
            List<PathObject> oldOverlaps = containerParent.getChildObjects().stream()
                    .filter(o -> channelClass.equals(o.getPathClass()) || overlapContainerName.equals(o.getName()))
                    .toList();
            hierarchy.removeObjects(oldOverlaps, false);
            PathAnnotationObject overlapsContainer = ChannelDetections.createContainer(containerParent, overlapContainerName, channelClass, this.hierarchy);
            ROI containerRoi = overlapsContainer.getROI();
            overlaps.stream()
                    .filter(overlap -> containerRoi.contains(overlap.getROI().getCentroidX(), overlap.getROI().getCentroidY()))
                    .forEach(overlap -> this.hierarchy.addObjectBelowParent(overlapsContainer, overlap, false));
        }
        return new ChannelDetections(this.getId(), ChannelDetections::getOverlapContainersName, this.hierarchy);
    }

    private List<PathAnnotationObject> searchContainers() {
        return this.hierarchy.getAnnotationObjects().stream()
                .filter(a -> this.isContainer(a))
                .map(a -> (PathAnnotationObject) a)
                .toList();
    }

    public String getId() {
        return id;
    }

    public String getContainersName() {
        return this.id2containerName.apply(this.id);
    }

    public PathClass getPathClass() {
        return this.getPathClass(this.id);
    }

    public PathClass getDiscardedDetectionsPathClass() {
        return PathClass.getInstance(PathClass.fromString("Other"), this.id, null);
        // return PathClass.fromString("Other: "+this.name);
    }

    public boolean isChannelDetection(PathObject o, boolean all) {
        return o.isDetection() && this.hasChannelClass(o, all);
    }

    private boolean hasChannelClass(PathObject o, boolean all) {
        return this.getPathClass().equals(o.getPathClass()) ||
                (all && this.getDiscardedDetectionsPathClass().equals(o.getPathClass()));
    }

    public boolean isContainer(PathObject o) {
        return o instanceof PathAnnotationObject && this.getContainersName().equals(o.getName());
    }

    private List<PathDetectionObject> getContainersDetections(boolean all) {
        if(containers.isEmpty())
            throw new EmptyDetectionsException();
        return this.containers.stream()
                .flatMap(container -> ChannelDetections.getChildrenDetections(container)
                        .filter(object -> this.isChannelDetection(object, all)))
                .toList();
    }
}