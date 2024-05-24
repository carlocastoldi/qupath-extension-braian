// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package qupath.ext.braian;

import qupath.ext.braian.utils.BraiAn;
import qupath.lib.classifiers.object.ObjectClassifier;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.scripting.QP;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

class EmptyDetectionsException extends RuntimeException {
    public EmptyDetectionsException(Class<? extends AbstractDetections> clazz) {
        super("No '"+clazz.getSimpleName()+" was pre-computed in the given image.");
    }
}

class IncompatibleClassifier extends Exception {
    public IncompatibleClassifier(Collection<PathClass> classifierOutputs, List<PathClass> detectionClasses, PathClass discardedChannelClass) {
        super("The provided classifier is incompatibile.\n" +
                "Expected: ["+BraiAn.join(detectionClasses, ", ")+", "+discardedChannelClass+"]\n" +
                "Got: "+classifierOutputs.toString());
    }
}

public abstract class AbstractDetections {
    private static final int BBH_MAX_DEPTH = 6;

    /**
     * returns the detections inside the given annotation
     * @param annotation
     * @param hierarchy
     * @return
     */
    protected static Stream<PathDetectionObject> getDetectionsInside(PathAnnotationObject annotation, PathObjectHierarchy hierarchy) {
        return hierarchy.getObjectsForROI(PathDetectionObject.class, annotation.getROI())
                .stream()
                .map(o -> (PathDetectionObject) o);
    }

    protected static Stream<PathDetectionObject> getChildrenDetections(PathAnnotationObject annotation) {
        return annotation.getChildObjects().stream()
                .filter(PathObject::isDetection)
                .map(object -> (PathDetectionObject) object);
    }

    private final String id;
    private final PathObjectHierarchy hierarchy;
    private final List<PathClass> detectionClasses;
    private List<PathAnnotationObject> containers;
    private BoundingBoxHierarchy bbh;

    // private ChannelDetections(UUID otherSecret, String id, PathObjectHierarchy hierarchy) {
    public AbstractDetections(String id, Collection<PathClass> detectionClasses, PathObjectHierarchy hierarchy) {
        // boolean all;
        this.hierarchy = hierarchy;
        this.id = id;
        this.detectionClasses = detectionClasses.stream().toList();
        this.fireUpdate();
    }

    public void fireUpdate() {
        this.containers = this.searchContainers();
        List<PathDetectionObject> cells = this.getContainersDetections(false);
        this.bbh = new BoundingBoxHierarchy(cells, BBH_MAX_DEPTH);
    }

    private List<PathAnnotationObject> searchContainers() {
        return this.hierarchy.getAnnotationObjects().stream()
                .filter(this::isContainer)
                .map(a -> (PathAnnotationObject) a)
                .toList();
    }

    public List<PathAnnotationObject> getContainers() {
        return this.containers;
    }

    public PathObjectHierarchy getHierarchy() {
        return hierarchy;
    }

    public String getId() {
        return id;
    }

    public Stream<PathDetectionObject> toStream() {
        return this.bbh.toStream().map(o -> (PathDetectionObject) o);
    }

    public Optional<PathObject> getOverlappingObjectIfPresent(PathDetectionObject cell) {
        return this.bbh.getOverlappingObjectIfPresent(cell);
    }

    abstract String getContainersName();

    public boolean isContainer(PathObject o) {
        return o instanceof PathAnnotationObject && this.getContainersName().equals(o.getName());
    }

    public PathClass getAnnotationsPathClass() {
        return PathClass.fromString(getId());
    }

    public PathClass getDiscardedDetectionsPathClass() {
        return PathClass.getInstance(PathClass.fromString("Other"), this.getId(), null);
        // return PathClass.fromString("Other: "+this.name);
    }

    public List<PathClass> getDetectionsPathClasses() {
        return this.detectionClasses;
    }

    boolean hasDetectionClass(PathObject o, boolean all) {
        return this.detectionClasses.contains(o.getPathClass()) ||
                (all && this.getDiscardedDetectionsPathClass().equals(o.getPathClass()));
    }

    public boolean isChannelDetection(PathObject o, boolean all) {
        return o.isDetection() && this.hasDetectionClass(o, all);
    }

    private List<PathDetectionObject> getContainersDetections(boolean all) {
        if(containers.isEmpty())
            throw new EmptyDetectionsException(this.getClass());
        return this.containers.stream()
                .flatMap(container -> AbstractDetections.getChildrenDetections(container)
                        .filter(object -> this.isChannelDetection(object, all)))
                .toList();
    }

    private boolean isDerivedContainer(PathObject object, PathClass classification, String name, PathObject containerParent) {
        return classification.equals(object.getPathClass())
                && name.equals(object.getName())
                && object instanceof PathAnnotationObject
                && containerParent.getROI().getGeometry().equals(object.getROI().getGeometry());
    }

    /**
     * Creates a duplicate child annotation to and sets it tags it as a detectionContainer for the current channel
     * @return
     */
    protected PathAnnotationObject createContainer(PathAnnotationObject containerParent, boolean overwrite) {
        String name = this.getContainersName();
        PathClass classification = this.getAnnotationsPathClass();
        if(overwrite) {
            Optional<PathAnnotationObject> oldContainer = containerParent.getChildObjects().stream()
                    .filter(o -> isDerivedContainer(o, classification, name, containerParent))
                    .map(o -> (PathAnnotationObject) o)
                    .findFirst(); // TODO: overwring the first one without warning may cause disruptive behaviours
            if (oldContainer.isPresent()) {
                PathAnnotationObject container = oldContainer.get();
                this.hierarchy.removeObjects(container.getChildObjects(), false);
                return container;
            }
        }
        PathAnnotationObject duplicate = (PathAnnotationObject) PathObjectTools.transformObject(containerParent,null, true, true);
        duplicate.setName(name);
        duplicate.setPathClass(classification);
        BraiAn.populatePathClassGUI(classification);
        this.hierarchy.addObjectBelowParent(containerParent, duplicate, true);
        duplicate.setLocked(true);
        return duplicate;
    }

    public <U> void applyClassifiers(List<PartialClassifier> classifiers, ImageData<U> imageData) {
        classifiers = removeUselessClassifiers(classifiers);
        List<PathDetectionObject> cells = new ArrayList<>();
        for (PartialClassifier partialClassifier: classifiers) {
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

    private static List<PartialClassifier> removeUselessClassifiers(List<PartialClassifier> partialClassifiers) {
        int lastFullClassifier = -1;
        int n = partialClassifiers.size();
        for (int i = n-1; i >= 0; i--) {
            lastFullClassifier = i;
            if (partialClassifiers.get(i).coversFullImage())
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
                    .flatMap(a -> AbstractDetections.getDetectionsInside(a, this.hierarchy))
                    .filter(detection -> this.hasDetectionClass(detection, true))
                    .toList();
        if (classifier.classifyObjects(imageData, cells, true) > 0)
            imageData.getHierarchy().fireObjectClassificationsChangedEvent(classifier, cells);
        PathClass discardedPC = this.getDiscardedDetectionsPathClass();
        BraiAn.populatePathClassGUI(discardedPC);
        return cells.stream().filter(d -> this.hasDetectionClass(d, false)).toList();
    }

    public <T> boolean isCompatibleClassifier(ObjectClassifier<T> classifier) {
        Collection<PathClass> outputClasses = classifier.getPathClasses();
        if(outputClasses.size() != 2)
            return false;
        return outputClasses.containsAll(this.getDetectionsPathClasses()) &&
                outputClasses.contains(this.getDiscardedDetectionsPathClass());
    }
}
