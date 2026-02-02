// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package qupath.ext.braian;

import org.locationtech.jts.geom.Geometry;
import qupath.lib.objects.PathObject;
import qupath.lib.roi.interfaces.ROI;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.*;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

interface BoundingBox {
    Rectangle2D getBox();
    int getDepth();
    Stream<PathObject> toStream();
    Optional<PathObject> getOverlappingObjectIfPresent(PathObject object);
    boolean contains(PathObject object);
}

class BVHNode implements BoundingBox {
    private final PathObject po;
    private final double centroidX;
    private final double centroidY;
    private final Rectangle2D.Double bbox;

    /**
     * Wraps a {@link PathObject} into a leaf node.
     *
     * @param po the object to wrap
     */
    public BVHNode(PathObject po) {
        this.po = po;
        ROI roi = po.getROI();
        centroidX = roi.getCentroidX();
        centroidY = roi.getCentroidY();
        this.bbox = new Rectangle2D.Double(roi.getBoundsX(), roi.getBoundsY(), roi.getBoundsWidth(), roi.getBoundsHeight());
    }

    /**
     * @return a stream containing only the wrapped {@link PathObject}
     */
    @Override
    public Stream<PathObject> toStream() {
        return Stream.of(this.po);
    }

    /**
     * Checks whether the centroid of this node is inside the specified object.
     *
     * @param object the object to test
     * @return an {@link Optional} containing the wrapped object if overlapping
     */
    @Override
    public Optional<PathObject> getOverlappingObjectIfPresent(PathObject object) {
        // if ROI.contains() results in being buggy, in the future we could rely on:
        // ROI.getGeometry().covers(c) || ROI.getGeometry().intersects(c)
        ROI roiObject = object.getROI();
        double x = roiObject.getBoundsX();
        double y = roiObject.getBoundsY();
        if (roiObject.isPoint() || roiObject.isEmpty())
            if (x == centroidX && y == centroidY)
                return Optional.of(this.po);
            else
                return Optional.empty();
        double w = roiObject.getBoundsWidth();
        double h = roiObject.getBoundsHeight();
        if(!this.bbox.intersects(x, y, w, h) && !this.bbox.isEmpty())
            return Optional.empty();
        if(roiObject.contains(centroidX, centroidY))
            return Optional.of(this.po);

        return Optional.empty();
    }

    /**
     * @param other the object to check
     * @return true if {@code other} is the wrapped object
     */
    @Override
    public boolean contains(PathObject other) {
        return this.po == other;
    }

    /**
     * @return the bounding box of the wrapped object
     */
    @Override
    public Rectangle2D getBox() {
        return this.bbox;
    }

    /**
     * @return the depth of this node (always 0 for leaves)
     */
    @Override
    public int getDepth() {
        return 0;
    }
}

/**
 * The class <code>BoundingBoxHierarchy</code> is data structure that helps in searching for specific
 * {@link PathObject} based on their shape and in logarithmic time to the total number
 * of object stored. The cost is paid at construction time, but it should be worth it if the look-up
 * operation is performed at least two times.
 * <p>
 * <code>BoundingBoxHierarchy</code> was build targeting images with lots (1000+) of
 * {@link qupath.lib.objects.PathDetectionObject}, but works with all {@link PathObject}.
 * <p>
 * For more information check <a href="https://en.wikipedia.org/wiki/Bounding_volume_hierarchy">Bounding volume hierarchy</a>.
 */
public class BoundingBoxHierarchy implements BoundingBox {
    private final Rectangle2D.Double bbox;
    private final Collection<BoundingBox> children;

    /**
     * Builds a top-down <a href="https://en.wikipedia.org/wiki/Bounding_volume_hierarchy">BVH</a> of maximum 6 levels
     * of hierarchy.
     * @param objects the given objects to insert into the hierarchy
     */
    public BoundingBoxHierarchy(Collection<? extends PathObject> objects) {
        this(objects, 6);
    }

    /**
     * Builds a top-down <a href="https://en.wikipedia.org/wiki/Bounding_volume_hierarchy">BVH</a>
     * @param objects the given objects to insert into the hierarchy
     * @param maxDepth the maximum depth that that hierarchy can have.
     *                 Below maxDepth, the recursive structure stops and lists all the remaining objects
     */
    public BoundingBoxHierarchy(Collection<? extends PathObject> objects, int maxDepth) {
        // top-down construction
        if (maxDepth < 1)
            throw new IllegalArgumentException("maxDepth must be >1. Instead got maxDepth="+maxDepth);
        if (objects.isEmpty()) {
            this.bbox = new Rectangle2D.Double();
            this.children = Arrays.asList();
            return;
        }
        if (objects.stream().anyMatch(o -> {ROI roi = o.getROI(); return roi.isPoint() && roi.getNumPoints() > 1;}))
            throw new IllegalArgumentException("BoundingBoxHierarchy cannot handle PointsROI objects with multiple points");
        double minX = objects.stream().map(object -> object.getROI().getBoundsX()).min(Double::compare).get();
        double minY = objects.stream().map(object -> object.getROI().getBoundsY()).min(Double::compare).get();
        double maxX = objects.stream().map(object -> object.getROI().getBoundsX() + object.getROI().getBoundsWidth()).max(Double::compare).get();
        double maxY = objects.stream().map(object -> object.getROI().getBoundsY() + object.getROI().getBoundsHeight()).max(Double::compare).get();
        this.bbox = new Rectangle2D.Double(minX, minY, maxX - minX, maxY - minY);
        if (maxDepth == 1 || this.bbox.isEmpty())
            this.children = objects.stream().map(object -> (BoundingBox) new BVHNode(object)).toList();
        else
            this.children = splitSpace() // split space in equally sized squares
                    .map(subArea -> findObjectsInside(objects, subArea)) // uses java.awt.Shape definition of insideness.
                    // This ensures that an object in objects is not inside multiple areas
                    .filter(Predicate.not(Collection::isEmpty))
                    .map(insideSubArea -> createChildBoundingBox(insideSubArea, maxDepth))
                    .toList();
    }

    private Stream<Rectangle2D> splitSpace() {
        // divides the space in 4 squares
        double length = Math.max(this.bbox.getWidth(), this.bbox.getHeight()) / 2;
        double minX = this.bbox.getX();
        double minY = this.bbox.getY();
        double[][] asd = {{minX, minY}, {minX + length, minY}, {minX, minY + length}, {minX + length, minY + length}};
        return Arrays.stream(asd).map(pos -> new Rectangle2D.Double(pos[0], pos[1], length, length));
    }

    private List<? extends PathObject> findObjectsInside(Collection<? extends PathObject> objects, Shape area) {
        return objects.stream()
                .filter(o -> area.contains(o.getROI().getCentroidX(), o.getROI().getCentroidY()))
                .toList();
    }

    private BoundingBox createChildBoundingBox(List<? extends PathObject> objectsInside, int nLevels) {
        assert !objectsInside.isEmpty();
        if (objectsInside.size() == 1)
            return new BVHNode(objectsInside.get(0));
        else
            return new BoundingBoxHierarchy(objectsInside, nLevels - 1);
    }

    /**
     * Retrieves the object in the hierarchy whose centroid:
     * <ul>
     *   <li>is inside the specified <code>object</code></li>
     * 	 <li>is the closest to the specified <code>object</code>'s centroid</li>
     * </ul>
     * It follows {@link qupath.lib.roi.interfaces.ROI#contains(double, double)} definition of <i>insideness</i> for
     * determining the overlap. Which, in turn, relies on {@link org.locationtech.jts.geom.Geometry#contains(Geometry)}
     * and <a href="https://en.wikipedia.org/wiki/DE-9IM">DE-9IM</a> intersection matrix.
     * @param object the object to search the overlap for
     * @return the closest PathObject in the hierarchy, or null if there is no overlap
     * @see qupath.lib.roi.interfaces.ROI#getCentroidX() ROI.getCentroidX()
     * @see qupath.lib.roi.interfaces.ROI#getCentroidY() ROI.getCentroidY()
     * @see BoundingBoxHierarchy#getOverlappingObjectIfPresent(PathObject)
     */
    public PathObject getOverlappingObject(PathObject object) {
        // TODO: rename to getObjectOverlappingCentroid
        return this.getOverlappingObjectIfPresent(object).orElse(null);
    }

    /**
     * @param object the object to search
     * @return true if {@code object} is present in the hierarchy
     */
    @Override
    public boolean contains(PathObject object) {
        if(this.isEmpty()) // if the BBH is empty
            return false;
        ROI objectRoi = object.getROI();
        if (!objectRoi.isPoint() && !objectRoi.isEmpty()) {
            double poX = objectRoi.getBoundsX();
            double poY = objectRoi.getBoundsY();
            double poW = objectRoi.getBoundsWidth();
            double poH = objectRoi.getBoundsHeight();
            if (!this.bbox.intersects(poX, poY, poW, poH) && !this.bbox.isEmpty())
                return false;
        }
        return this.children.stream().anyMatch(bvh -> bvh.contains(object));
    }

    /**
     * Retrieves the object in the hierarchy whose centroid:
     * <ul>
     *   <li>is inside the specified <code>object</code></li>
     * 	 <li>is the closest to the specified <code>object</code>'s centroid</li>
     * </ul>
     * It follows {@link qupath.lib.roi.interfaces.ROI#contains(double, double)} definition of <i>insideness</i> for
     * determining the overlap. Which, in turn, relies on {@link org.locationtech.jts.geom.Geometry#contains(Geometry)}
     * and <a href="https://en.wikipedia.org/wiki/DE-9IM">DE-9IM</a> intersection matrix.
     * @param object the object to search the overlap for
     * @return the closest PathObject in the hierarchy as an {@link java.util.Optional Optional}
     * @see qupath.lib.roi.interfaces.ROI#getCentroidX() ROI.getCentroidX()
     * @see qupath.lib.roi.interfaces.ROI#getCentroidY() ROI.getCentroidY()
     * @see BoundingBoxHierarchy#getOverlappingObject(PathObject)
     */
    @Override
    public Optional<PathObject> getOverlappingObjectIfPresent(PathObject object) {
        if(this.isEmpty()) // if the BBH is empty
            return Optional.empty();
        ROI objectRoi = object.getROI();
        if (!objectRoi.isPoint() && !objectRoi.isEmpty()) {
            double poX = objectRoi.getBoundsX();
            double poY = objectRoi.getBoundsY();
            double poW = objectRoi.getBoundsWidth();
            double poH = objectRoi.getBoundsHeight();
            if (!this.bbox.intersects(poX, poY, poW, poH) && !this.bbox.isEmpty())
                return Optional.empty();
        }
        List<PathObject> overlaps = this.children.stream()
                .map(bvh -> bvh.getOverlappingObjectIfPresent(object))
                .flatMap(Optional::stream)
                .toList();
        if (overlaps.size() < 2)
            return overlaps.stream().findFirst();
        // there are more than one object overlapping
        return Optional.of(getClosestOverlap(overlaps, object));
    }

    /**
     * @return true if there are no {@link PathObject} inside
     */
    public boolean isEmpty() {
        // if the bbox is empty, it could still mean that there is one object with PointsROI or something similar
        return this.children.isEmpty();
    }

    private PathObject getClosestOverlap(List<PathObject> overlaps, PathObject object) {
        assert overlaps.size() >= 2;
        List<Double> distances =  overlaps.stream()
                .map(overlap -> {
                    double x1 = object.getROI().getCentroidX();
                    double y1 = object.getROI().getCentroidY();
                    double x2 = overlap.getROI().getCentroidX();
                    double y2 = overlap.getROI().getCentroidY();
                    return Math.hypot(x1-x2, y1-y2);
                })
                .toList();
        int argMin = distances.indexOf(distances.stream().min(Double::compare).get()); // can't fail on get() as the stream is at least 2-elements long
        return overlaps.get(argMin);
    }

    /**
     * Visits each element of the hierarchy and outputs all the objects contained as {@link java.util.stream.Stream}.
     * @return the stream of all saved objects
     */
    @Override
    public Stream<PathObject> toStream() {
        return this.children.stream().flatMap(BoundingBox::toStream);
    }

    /**
     * Returns a rectangle in which all objects' ROI are inside
     * @return the bounding box of all objects
     */
    @Override
    public Rectangle2D getBox() {
        return this.bbox;
    }

    /**
     * Compute the BoundingBoxHierarchy's maximum depth
     * @return the maximum depth of the hierarchy. Returns -1 if the BoundingBoxHierarchy is empty.
     */
    @Override
    public int getDepth() {
        return this.children.stream()
                .map(BoundingBox::getDepth)
                .max(Integer::compare)
                .orElseGet(() -> -2)+1;
    }
}
