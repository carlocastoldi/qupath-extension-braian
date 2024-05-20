// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package qupath.ext.braian;

import qupath.ext.braian.config.WatershedCellDetectionConfig;
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

import static qupath.lib.scripting.QP.createFullImageAnnotation;
import static qupath.lib.scripting.QP.selectObjects;

class EmptyDetectionsException extends RuntimeException {
    public EmptyDetectionsException() {
        super("No detection was pre-computed in the given image! You first need to call ChannelDetections.compute()");
    }
}

class IncompatibleClassifier extends Exception {
    public IncompatibleClassifier(Collection<PathClass> classifierOutputs, List<PathClass> detectionClasses, PathClass discardedChannelClass) {
        super("The provided classifier is incompatibile.\n" +
                "Expected: ["+join(detectionClasses, ", ")+discardedChannelClass+"]\n" +
                "Got: "+classifierOutputs.toString());
    }

    private static <T> String join(List<T>l, String delim) {
        StringBuilder classesStr = new StringBuilder();
        for (T o: l)
            classesStr.append(o).append(delim);
        return classesStr.toString();
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

    public static ChannelDetections compute(ImageChannelTools channel, WatershedCellDetectionConfig params, PathAnnotationObject annotation, PathObjectHierarchy hierarchy) {
        return ChannelDetections.compute(channel, params, annotation != null ? List.of(annotation) : null, hierarchy);
    }

    public static ChannelDetections compute(ImageChannelTools channel, WatershedCellDetectionConfig config, Collection<PathAnnotationObject> annotations, PathObjectHierarchy hierarchy) {
        Map<String, ?> params = config.toParameters(channel);
        if(annotations == null || annotations.isEmpty()) {
            // throw new IllegalArgumentException("You must give at least one annotation on which to compute the detections");
            PathAnnotationObject fullImage = ChannelDetections.getFullImageDetectionAnnotation(hierarchy);
            annotations = List.of(fullImage);
        }
        // TODO: check if the given annotations overlap. If they do, throw an error as that would duplicate detections
        List<PathAnnotationObject> containers = annotations.stream().map(ann -> ChannelDetections.computeInside(channel, ann, params, hierarchy)).toList();
        return new ChannelDetections(channel, hierarchy);
    }

    /**
     * Computes the detections in "annotation".
     * @param channel
     * @param annotation
     * @param params
     * @param hierarchy
     * @return
     */
    private static PathAnnotationObject computeInside(ImageChannelTools channel, PathAnnotationObject annotation, Map<String,?> params, PathObjectHierarchy hierarchy) {
        annotation.setLocked(true);
        PathAnnotationObject container = ChannelDetections.createBasicContainer(annotation, channel, hierarchy);
        selectObjects(container);
        try {
            QP.runPlugin("qupath.imagej.detect.cells.WatershedCellDetection", params);
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
        return createContainer(annotation, ChannelDetections.getBasicContainersName(name), ChannelDetections.getAnnotatiosPathClass(name), hierarchy, true);
    }

    private static PathAnnotationObject createContainer(PathAnnotationObject containerParent, String name, PathClass classification, PathObjectHierarchy hierarchy, boolean overwrite) {
        if(overwrite) {
            Optional<PathAnnotationObject> oldContainer = containerParent.getChildObjects().stream()
                    .filter(o -> classification.equals(o.getPathClass()) && name.equals(o.getName()) && o instanceof PathAnnotationObject)
                    .map(o -> (PathAnnotationObject) o)
                    .findFirst();
            if (oldContainer.isPresent()) {
                PathAnnotationObject container = oldContainer.get();
                hierarchy.removeObjects(container.getChildObjects(), false);
                return container;
            }
        }
        QP.setSelectedObject(containerParent);
        QP.duplicateSelectedAnnotations();
        PathAnnotationObject duplicate = (PathAnnotationObject) QP.getSelectedObject();
        duplicate.setName(name);
        duplicate.setPathClass(classification);
        BraiAn.populatePathClassGUI(classification);
        hierarchy.addObjectBelowParent(containerParent, duplicate, true);
        duplicate.setLocked(true);
        return duplicate;
    }

    private static String getBasicContainersName(String id) {
        return id+" cells";
    }

    private static String getOverlapContainersName(String id) {
        return id+" overlaps";
    }

    private static PathClass getAnnotatiosPathClass(String id) {
        return PathClass.fromString(id);
    }

    /**
     * returns the detections inside the given annotation
     * @param annotation
     * @param hierarchy
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

    private static String createOverlappingClassName(String primary, List<String> others) {
        return Stream.concat(Stream.of(primary), others.stream())
                .collect(Collectors.joining(ChannelDetections.OVERLAP_DELIMITER));
    }

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
                        othersOverlappingClasses.stream().map(postfix -> first+ChannelDetections.OVERLAP_DELIMITER+postfix))
        ).toList();
    }


    private final PathObjectHierarchy hierarchy;
    private final String id;
    private final Function<String,String> id2containerName;
    private final List<PathClass> detectionClasses;
    private List<PathAnnotationObject> containers;
    private BoundingBoxHierarchy bbh;

    public ChannelDetections(ImageChannelTools channel, PathObjectHierarchy hierarchy) {
        this(channel.getName(), ChannelDetections::getBasicContainersName, hierarchy);
    }

    // private ChannelDetections(UUID otherSecret, String id, PathObjectHierarchy hierarchy) {
    private ChannelDetections(String id, Function<String,String> id2containerName, PathObjectHierarchy hierarchy) {
        this(id, id2containerName, hierarchy, List.of(PathClass.fromString(id)));
    }

    // private ChannelDetections(UUID otherSecret, String id, PathObjectHierarchy hierarchy) {
    private ChannelDetections(String id, Function<String,String> id2containerName, PathObjectHierarchy hierarchy, Collection<PathClass> detectionClasses) {
        // boolean all;
        this.hierarchy = hierarchy;
        this.id = id;
        this.id2containerName = id2containerName;
        this.detectionClasses = detectionClasses.stream().toList();
        fireUpdate();
    }

    public void fireUpdate() {
        this.containers = this.searchContainers();
        List<PathDetectionObject> cells = this.getContainersDetections(false);
        this.bbh = new BoundingBoxHierarchy(cells, BBH_MAX_DEPTH);
    }

    public <U> void applyClassifiers(List<AnnotationClassifier> classifiers, ImageData<U> imageData) {
        classifiers = removeUselessClassifiers(classifiers);
        List<PathDetectionObject> cells = new ArrayList<>();
        for (AnnotationClassifier partialClassifier: classifiers) {
            ObjectClassifier classifier = partialClassifier.getClassifier();
            Collection<PathAnnotationObject> toClassify = partialClassifier.getAnnotations();
            try {
                cells.addAll(this.classifyInside(classifier, toClassify, imageData));
            } catch (IncompatibleClassifier e) {
                BraiAnExtension.logger.warn("Skipping {}...\n\t{}", classifier, e.getMessage().replace("\n", "\n\t"));
                return;
            }
        }
        this.bbh = new BoundingBoxHierarchy(cells, BBH_MAX_DEPTH);
    }

    private static List<AnnotationClassifier> removeUselessClassifiers(List<AnnotationClassifier> partialClassifiers) {
        int lastFullClassifier = -1;
        int n = partialClassifiers.size();
        for (int i = n-1; i >= 0; i--) {
            lastFullClassifier = i;
            if (!partialClassifiers.get(i).isPartial())
                break;
        }
        if(lastFullClassifier > 0)
            partialClassifiers = partialClassifiers.subList(lastFullClassifier, n);
        return partialClassifiers;
    }

    private <T> List<PathDetectionObject> classifyInside(ObjectClassifier<T> classifier, Collection<PathAnnotationObject> annotations, ImageData<T> imageData) throws IncompatibleClassifier {
        if(!this.isCompatibleClassifier(classifier))
            throw new IncompatibleClassifier(classifier.getPathClasses(), this.getDetectionsPathClasses(), this.getDiscardedDetectionsPathClass());
        List<PathDetectionObject> cells;
        if(annotations == null)
            cells = this.getContainersDetections(true); // get ALL detections. Even those there were discarded
        else
            cells = annotations.stream()
                    .flatMap(a -> ChannelDetections.getDetectionsInside(a, this.hierarchy))
                    .filter(detection -> this.hasDetectionClass(detection, true))
                    .toList();
        if (classifier.classifyObjects(imageData, cells, true) > 0)
            imageData.getHierarchy().fireObjectClassificationsChangedEvent(classifier, cells);
        PathClass discardedPC = this.getDiscardedDetectionsPathClass();
        BraiAn.populatePathClassGUI(discardedPC);
        return cells.stream().filter(d -> this.hasDetectionClass(d, false)).toList();
    }

    public ChannelDetections overlap(List<ChannelDetections> otherDetections) {
        List<PathObject> overlaps = this.bbh.toStream().flatMap( cell -> {
            List<String> idWithOverlaps = otherDetections.stream()
                    .filter(other -> other.bbh.getOverlappingObjectIfPresent(cell).isPresent())
                    .map(ChannelDetections::getId)
                    .toList();
            if(idWithOverlaps.isEmpty())
                return Stream.empty();
            String className = createOverlappingClassName(this.getId(), idWithOverlaps);
            PathClass overlapClass = PathClass.fromString(className);
            PathObject cellCopy = PathObjects.createDetectionObject(cell.getROI(), overlapClass);
            return Stream.of(cellCopy);
        }).toList();
        this.hierarchy.addObjects(overlaps);
        PathClass channelClass = this.getAnnotatiosPathClass();
        // add all duplicated overlapping cells to a new annotation
        for (PathAnnotationObject container : this.containers) {
            String overlapContainerName = ChannelDetections.getOverlapContainersName(this.id);
            PathAnnotationObject containerParent = (PathAnnotationObject) container.getParent();
            PathAnnotationObject overlapsContainer = ChannelDetections.createContainer(containerParent, overlapContainerName, channelClass, this.hierarchy, true);
            ROI containerRoi = overlapsContainer.getROI();
            overlaps.stream()
                    .filter(overlap -> containerRoi.contains(overlap.getROI().getCentroidX(), overlap.getROI().getCentroidY()))
                    .forEach(overlap -> this.hierarchy.addObjectBelowParent(overlapsContainer, overlap, false));
        }
        Collection<PathClass> overlapallPathClasses = ChannelDetections.createAllOverlappingClassNames(
                    otherDetections.stream()
                        .map(ChannelDetections::getId).toList())
                .stream()
                .map(name -> this.getId()+ChannelDetections.OVERLAP_DELIMITER+name)
                .map(PathClass::fromString)
                .toList();
        return new ChannelDetections(this.getId(), ChannelDetections::getOverlapContainersName, this.hierarchy, overlapallPathClasses);
    }

    private List<PathAnnotationObject> searchContainers() {
        return this.hierarchy.getAnnotationObjects().stream()
                .filter(this::isContainer)
                .map(a -> (PathAnnotationObject) a)
                .toList();
    }

    public String getId() {
        return id;
    }

    public String getContainersName() {
        return this.id2containerName.apply(this.id);
    }

    public PathClass getAnnotatiosPathClass() {
        return getAnnotatiosPathClass(this.id);
    }

    public List<PathClass> getDetectionsPathClasses() {
        return this.detectionClasses;
    }

    public PathClass getDiscardedDetectionsPathClass() {
        return PathClass.getInstance(PathClass.fromString("Other"), this.id, null);
        // return PathClass.fromString("Other: "+this.name);
    }

    public boolean isChannelDetection(PathObject o, boolean all) {
        return o.isDetection() && this.hasDetectionClass(o, all);
    }

    private boolean hasDetectionClass(PathObject o, boolean all) {
        return this.detectionClasses.contains(o.getPathClass()) ||
                (all && this.getDiscardedDetectionsPathClass().equals(o.getPathClass()));
    }

    public boolean isContainer(PathObject o) {
        return o instanceof PathAnnotationObject && this.getContainersName().equals(o.getName());
    }

    public <T> boolean isCompatibleClassifier(ObjectClassifier<T> classifier) {
        Collection<PathClass> outputClasses = classifier.getPathClasses();
        if(outputClasses.size() != 2)
            return false;
        return outputClasses.containsAll(this.getDetectionsPathClasses()) &&
                outputClasses.contains(this.getDiscardedDetectionsPathClass());
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
