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

/**
 * This abstract class defines the interface to handle groups of different cell detections groups.
 * It works by using on "container" annotations, in which all the cells are grouped inside.
 * By default, {@link AbstractDetections} allows to classify its detections by applying {@link PartialClassifier}s.
 */
public abstract class AbstractDetections {
    private static final int BBH_MAX_DEPTH = 6;

    /**
     * returns the detections inside the given annotation
     * @param annotation where the to search the detection inside
     * @param hierarchy where to find the detections
     * @return a stream of detections found inside annotations
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

    /**
     * Constructs an object that groups together detections of the same kind
     * @param id identification of this group of detections
     * @param detectionClasses classifications used to identify the detections
     * @param hierarchy where to find the detections
     */
    public AbstractDetections(String id, Collection<PathClass> detectionClasses, PathObjectHierarchy hierarchy) {
        this.hierarchy = hierarchy;
        this.id = id;
        this.detectionClasses = detectionClasses.stream().toList();
        this.fireUpdate();
    }

    /**
     * used to update the internal representation to the current state.
     * If container annotations or detections are touched outside of BraiAn, it's better to call this method.
     */
    public void fireUpdate() {
        this.containers = this.searchContainers();
        List<PathDetectionObject> cells = this.getContainersDetections(false);
        this.bbh = new BoundingBoxHierarchy(cells, BBH_MAX_DEPTH);
    }

    // TODO: allow to search for containers WITHIN a list of given annotations (useful with 'classForDetections' from ProjectsConfig)
    private List<PathAnnotationObject> searchContainers() {
        return this.hierarchy.getAnnotationObjects().stream()
                .filter(this::isContainer)
                .map(a -> (PathAnnotationObject) a)
                .toList();
    }

    /**
     * @return the container annotations
     */
    public List<PathAnnotationObject> getContainers() {
        return this.containers;
    }

    protected PathObjectHierarchy getHierarchy() {
        return hierarchy;
    }

    /**
     * @return a string often used to identify detections and their container annotations
     */
    public String getId() {
        return id;
    }

    /**
     * @return a stream of the given detections, in no particular order
     */
    public Stream<PathDetectionObject> toStream() {
        return this.bbh.toStream().map(o -> (PathDetectionObject) o);
    }

    /**
     * @param o the object to search an overlapping detection for
     * @return the detection that overlaps the given object
     * @see BoundingBoxHierarchy#getOverlappingObjectIfPresent(PathObject)
     */
    public Optional<PathObject> getOverlappingObjectIfPresent(PathObject o) {
        return this.bbh.getOverlappingObjectIfPresent(o);
    }

    /**
     * @return the name used by the containers of detections of the instance kind
     */
    public abstract String getContainersName();

    /**
     * @param o the object to test
     * @return true if the given object is a container of these detections. False otherwise
     */
    public boolean isContainer(PathObject o) {
        return o instanceof PathAnnotationObject && this.getContainersName().equals(o.getName());
    }

    /**
     * @return the classification used to indentify containers of detections of the instance kind
     */
    public PathClass getContainersPathClass() {
        return PathClass.fromString(getId());
    }

    /**
     * @return the classification used to identify detections of the instance kind that were discarded
     */
    public PathClass getDiscardedDetectionsPathClass() {
        // TODO: should be multiple classifications, so that it's possible to retrieve the initial classifications of all detections
        return PathClass.getInstance(PathClass.fromString("Other"), this.getId(), null);
    }

    /**
     * @return the classification<b>s</b> used to identify the detections of the instance kind
     */
    public List<PathClass> getDetectionsPathClasses() {
        return this.detectionClasses;
    }

    private boolean hasDetectionClass(PathObject o, boolean all) {
        return this.detectionClasses.contains(o.getPathClass()) ||
                (all && this.getDiscardedDetectionsPathClass().equals(o.getPathClass()));
    }

    /**
     * checks whether the given object is a detections belonging to the instance kind or not
     * @param o the object to test
     * @param all if true, considers discarded detections also as belonging to the instance kind
     * @return true if the given object belongs to the instance kind
     */
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

    private boolean isDerivedContainer(PathObject object, PathObject containerParent, PathClass classification, String name) {
        return classification.equals(object.getPathClass())
                && name.equals(object.getName())
                && object instanceof PathAnnotationObject
                && containerParent.getROI().getGeometry().equals(object.getROI().getGeometry());
    }

    /**
     * Creates a duplicate child annotation to use as container
     * @param containerParent the annotation to use as a model for the container
     * @param overwrite if true, deletes all previously created containers, if any
     * @return the new container, as child of <code>containerParent</code>
     */
    protected PathAnnotationObject createContainer(PathAnnotationObject containerParent, boolean overwrite) {
        String name = this.getContainersName();
        PathClass classification = this.getContainersPathClass();
        if(overwrite) {
            Optional<PathAnnotationObject> oldContainer = containerParent.getChildObjects().stream()
                    .filter(o -> isDerivedContainer(o, containerParent, classification, name))
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

    /**
     * applies a list of classifiers in sequence ot the detections of the instance kind.
     * The order of the classifiers is important. If they work on overlapping annotations, the intersection is classified using the latter classifier
     * @param classifiers the sequence of partial classifiers to apply
     * @param imageData the imageData used by the classifiers
     */
    public void applyClassifiers(List<PartialClassifier> classifiers, ImageData<?> imageData) {
        classifiers = removeUselessClassifiers(classifiers);
        List<PathDetectionObject> cells = new ArrayList<>();
        for (PartialClassifier partialClassifier: classifiers) {
            ObjectClassifier classifier = partialClassifier.classifier();
            Collection<PathAnnotationObject> toClassify = partialClassifier.annotations();
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

    protected <T> boolean isCompatibleClassifier(ObjectClassifier<T> classifier) {
        Collection<PathClass> outputClasses = classifier.getPathClasses();
        if(outputClasses.size() != 2)
            return false;
        return outputClasses.containsAll(this.getDetectionsPathClasses()) &&
                outputClasses.contains(this.getDiscardedDetectionsPathClass());
    }
}
