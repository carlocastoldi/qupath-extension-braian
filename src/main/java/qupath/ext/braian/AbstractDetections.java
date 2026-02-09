// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package qupath.ext.braian;

import org.locationtech.jts.geom.Geometry;
import qupath.ext.braian.utils.BraiAn;
import qupath.lib.classifiers.object.ObjectClassifier;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.images.ImageData;
import qupath.lib.objects.*;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.projects.Project;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.GeometryTools;
import qupath.lib.roi.interfaces.ROI;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This abstract class defines the interface to handle groups of different cell detections groups.
 * It works by using on "container" annotations, inside which all the cells are grouped.
 * By default, {@link AbstractDetections} allows to classify its detections by applying {@link PartialClassifier}s.
 */
public abstract class AbstractDetections {
    private static final int BBH_MAX_DEPTH = 6;

    /**
     * returns the detections inside the given annotation
     * @param annotation where to search the detections
     * @param hierarchy where to find the detections
     * @return a stream of detections found inside annotations
     */
    protected static Stream<PathDetectionObject> getDetectionsInside(PathAnnotationObject annotation, PathObjectHierarchy hierarchy) {
        return getDetectionsInside(annotation.getROI(), hierarchy);
    }

    /**
     * returns the detections inside the given {@link ROI}
     * @param roi where to search the detections
     * @param hierarchy where to find the detections
     * @return a stream of detections found inside annotations
     */
    protected static Stream<PathDetectionObject> getDetectionsInside(ROI roi, PathObjectHierarchy hierarchy) {
        return hierarchy.getObjectsForROI(PathDetectionObject.class, roi)
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
    private final Project<?> project;
    private final QuPathGUI qupath;
    private List<PathAnnotationObject> containers = new ArrayList<>();
    private BoundingBoxHierarchy bbh;

    /**
     * Constructs an object that groups together detections of the same kind.
     * To do so, it searches for container annotations of detections having a name compatible with {@link #getContainersName()}
     * @param id identification of this group of detections
     * @param detectionClasses classifications used to identify the detections
     * @param hierarchy where to find the detections
     * @throws NoCellContainersFoundException if there is no compatible container in the hierarchy
     * @see #getContainersName()
     * @see #getContainersPathClass()
     */
    public AbstractDetections(String id,
                              Collection<PathClass> detectionClasses,
                              PathObjectHierarchy hierarchy,
                              Project<?> project,
                              QuPathGUI qupath) throws NoCellContainersFoundException {
        this.hierarchy = hierarchy;
        this.id = id;
        this.detectionClasses = detectionClasses.stream().toList();
        this.project = project;
        this.qupath = qupath;
        if (project != null) {
            BraiAn.populatePathClassGUI(project, qupath, this.detectionClasses.toArray(new PathClass[0]));
        }
        this.fireUpdate();
    }

    /**
     * Updates the internal representation to the current state.
     * If a new container of detections is added and overlaps the old containers, it updates the old detections with the new ones.
     * Additionally, it makes sure that the newer containers don't overlap with the old ones.
     * <br>
     * If container annotations or detections are touched outside of BraiAn, it's better to call this method.
     * @throws NoCellContainersFoundException if there is no compatible container in the hierarchy
     */
    public void fireUpdate() throws NoCellContainersFoundException {
        List<PathAnnotationObject> allContainers = this.searchContainers();
        if (allContainers.isEmpty()) {
            this.containers = allContainers;
            this.bbh = new BoundingBoxHierarchy(new ArrayList<>(), BBH_MAX_DEPTH);
            return;
        }
        List<PathAnnotationObject> oldContainers = allContainers.stream()
                .filter(c -> this.containers.contains(c)).toList();
        List<PathAnnotationObject> newContainers = allContainers.stream()
                .filter(c -> !this.containers.contains(c)).toList();
        for (PathAnnotationObject oldContainer: oldContainers)
            updateContainer(oldContainer, newContainers); // may shrink the overlapping newContainers
        this.removeEmptyContainers(allContainers);
        this.containers = allContainers;
        List<PathDetectionObject> cells = this.getContainersDetections(false); // throw NoCellContainersFoundException
        this.bbh = new BoundingBoxHierarchy(cells, BBH_MAX_DEPTH);
    }

    private void updateContainer(PathAnnotationObject oldContainer, List<PathAnnotationObject> newContainers) {
        ROI oldROI = oldContainer.getROI();
        Geometry oldGeom = oldROI.getGeometry();
        ImagePlane oldPlane = oldROI.getImagePlane();
        for (PathAnnotationObject newContainer: newContainers) {
            Geometry newGeom = newContainer.getROI().getGeometry();
            if (oldGeom.intersects(newGeom)) {
                ROI intersection = GeometryTools.geometryToROI(oldGeom.intersection(newGeom), oldPlane);
                List<PathDetectionObject> newDetections = getChildrenDetections(newContainer).toList(); //collect(Collectors.toSet());
                this.removeOldDetections(intersection, newDetections);
                this.addUpdatedDetections(oldContainer, newDetections);
                ROI newDiffOld = GeometryTools.geometryToROI(newGeom.difference(oldGeom), oldPlane);
                newContainer.setROI(newDiffOld);
            }
        }
    }

    private void addUpdatedDetections(PathAnnotationObject container, List<PathDetectionObject> newDetections) {
        newDetections.stream()
                .filter( newDetection -> container.getROI().contains(newDetection.getROI().getCentroidX(), newDetection.getROI().getCentroidY()))
                .forEach( newDetection -> this.hierarchy.addObjectBelowParent(container, newDetection, false) );
    }

    private void removeOldDetections(ROI area, List<PathDetectionObject> newDetections) {
        BoundingBoxHierarchy newDetectionsBBH = new BoundingBoxHierarchy(newDetections, BBH_MAX_DEPTH);
        List<PathDetectionObject> oldDetections = AbstractDetections.getDetectionsInside(area, hierarchy)   // it's a detection inside a new container
                .filter(oldDetection -> this.isChannelDetection(oldDetection, true) && !newDetectionsBBH.contains(oldDetection))
                .toList();
        hierarchy.removeObjects(oldDetections, false);
    }

    private void removeEmptyContainers(List<PathAnnotationObject> containers) {
        ListIterator<PathAnnotationObject> iterator = containers.listIterator();
        while (iterator.hasNext()) {
            PathAnnotationObject c = iterator.next();
            if (c.getROI().isEmpty()) {
                this.hierarchy.removeObject(c, false);
                iterator.remove();
            }
        }
        this.hierarchy.fireHierarchyChangedEvent(this);
    }

    /**
     * If there are no detections within the current instance.
     * <br>
     * If the state was changed outside of this extension, you might need to call {@link #fireUpdate()} first.
     * @return True, if no detections are found within the current state. False otherwise.
     * @see #fireUpdate()
     */
    public boolean isEmpty() {
        try {
            return this.containers.isEmpty() || this.getContainersDetections(false).isEmpty();
        } catch (NoCellContainersFoundException e) {
            return true;
        }
    }

    // TODO: allow to search for containers WITHIN a list of given annotations (useful with 'classForDetections' from ProjectsConfig)
    private List<PathAnnotationObject> searchContainers() {
        return this.hierarchy.getAnnotationObjects().stream()
                .filter(this::isContainer)
                .map(a -> (PathAnnotationObject) a)
                .collect(Collectors.toList());  // mutable list
                // .toList();                   // immutable list
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
     * checks whether the given object is a detection belonging to the instance kind
     * @param o the object to test
     * @param all if true, considers discarded detections also as belonging to the instance kind
     * @return true if the given object belongs to the instance kind
     */
    public boolean isChannelDetection(PathObject o, boolean all) {
        return o.isDetection() && this.hasDetectionClass(o, all);
    }

    /**
     * returns the list of detections of this instance found only in the containers
     */
    private List<PathDetectionObject> getContainersDetections(boolean all) throws NoCellContainersFoundException {
        if(this.containers.isEmpty())
            throw new NoCellContainersFoundException(this.getClass());
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
     * Creates a duplicate child annotation to be used as container
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
        this.hierarchy.addObjectBelowParent(containerParent, duplicate, true);
        duplicate.setLocked(true);
        return duplicate;
    }

    /**
     * applies a list of classifiers in sequence to the detections of the instance kind.
     * The order of the classifiers is important. If they work on overlapping annotations, the intersection is classified using the latter classifier.
     * <br>
     * If a classifier's output is not compatible with the instance, the corresponding {@link PartialClassifier} will be skipped.
     * @param classifiers the sequence of partial classifiers to apply
     * @param imageData the imageData used by the classifiers
     * @see AbstractDetections#getDetectionsPathClasses()
     * @see AbstractDetections#getDiscardedDetectionsPathClass()
     */
    public <T> void applyClassifiers(List<PartialClassifier<T>> classifiers, ImageData<T> imageData) {
        classifiers = removeUselessClassifiers(classifiers);
        List<PathDetectionObject> cells = new ArrayList<>();
        try {
            for (PartialClassifier<T> partialClassifier : classifiers) {
                ObjectClassifier<T> classifier = partialClassifier.classifier();
                Collection<PathAnnotationObject> toClassify = partialClassifier.annotations();
                try {
                    cells.addAll(this.classifyInside(classifier, toClassify, imageData));
                } catch (IncompatibleClassifier e) {
                    BraiAnExtension.logger.warn("Skipping {}...\n\t{}", classifier, e.getMessage().replace("\n", "\n\t"));
                    return;
                }
            }
            this.bbh = new BoundingBoxHierarchy(cells, BBH_MAX_DEPTH);
        } catch (NoCellContainersFoundException e) {
            BraiAnExtension.getLogger().warn("No containers of '{}' detections found. No classification made", getContainersName());
        }
    }

    private static <T> List<PartialClassifier<T>> removeUselessClassifiers(List<PartialClassifier<T>> partialClassifiers) {
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

    private <T> List<PathDetectionObject> classifyInside(ObjectClassifier<T> classifier,
                                                         Collection<PathAnnotationObject> annotations,
                                                         ImageData<T> imageData) throws IncompatibleClassifier, NoCellContainersFoundException {
        if(!this.isCompatibleClassifier(classifier) &&
                !(classifier instanceof SingleClassifier &&
                        this.getDetectionsPathClasses().contains(classifier.getPathClasses().toArray()[0])))
            throw new IncompatibleClassifier(classifier.getPathClasses(), this.getDetectionsPathClasses(), this.getDiscardedDetectionsPathClass());
        List<PathDetectionObject> cells;
        if(annotations == null)
            // get ALL detections. Even those there were discarded. Can't use this.toStream()
            cells = this.getContainersDetections(true); // throws NoCellContainersFoundException
        else
            cells = annotations.stream()
                    .flatMap(a -> AbstractDetections.getDetectionsInside(a, this.hierarchy))
                    .filter(detection -> this.hasDetectionClass(detection, true))
                    .toList();
        if (classifier.classifyObjects(imageData, cells, true) > 0)
            imageData.getHierarchy().fireObjectClassificationsChangedEvent(classifier, cells);
        PathClass discardedPC = this.getDiscardedDetectionsPathClass();
        BraiAn.populatePathClassGUI(project, qupath, discardedPC);
        return cells.stream().filter(d -> this.hasDetectionClass(d, false)).toList();
    }

    protected <T> boolean isCompatibleClassifier(ObjectClassifier<T> classifier) {
        Collection<PathClass> outputClasses = classifier.getPathClasses();
        if(outputClasses.size() != 2)
            return false;
        return outputClasses.containsAll(this.getDetectionsPathClasses()) &&
                outputClasses.contains(this.getDiscardedDetectionsPathClass());
    }

    /**
     * Check whether two {@code AbstractDetections} are compatible.
     * They are compatible if they work on the same hierarchy, they have the same {@link #getId()},
     * they work on the same type of containers and on the same {@link #getDetectionsPathClasses()}
     * @param other an instance of the detections to check against the current one
     * @return true, if compatible. False otherwise.
     * @see #getId()
     * @see #getContainersName()
     * @see #getDetectionsPathClasses()
     */
    public boolean isCompatibleWith(AbstractDetections other) {
        return other != null
                && this.id.equals(other.id)
                && this.getContainersName().equals(other.getContainersName())
                && this.hierarchy.equals(other.hierarchy)
                && new HashSet<>(this.detectionClasses).equals(new HashSet<>(other.detectionClasses));
    }

    /**
     * Two detection objects are functionally the same if they are compatible with each other and they have the same containers
     * @param obj
     * @return True, if they are functionally the same. False otherwise
     * @see #isCompatibleWith(AbstractDetections)
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == null || !this.getClass().equals(obj.getClass()))
            return false;
        AbstractDetections other = (AbstractDetections) obj;
        return this.isCompatibleWith(other) && new HashSet<>(this.containers).equals(new HashSet<>(other.containers));
    }
}

class IncompatibleClassifier extends Exception {
    public IncompatibleClassifier(Collection<PathClass> classifierOutputs, List<PathClass> detectionClasses, PathClass discardedChannelClass) {
        super("The provided classifier is incompatibile.\n" +
                "Expected: ["+ BraiAn.join(detectionClasses, ", ")+", "+discardedChannelClass+"]\n" +
                "Got: "+classifierOutputs.toString());
    }
}

class IncompatibleDetections extends Exception {
    public IncompatibleDetections(AbstractDetections d1, AbstractDetections d2) {
        super("The provided detections are incompatibile: " + d1 + " and " + d2);
    }
}
