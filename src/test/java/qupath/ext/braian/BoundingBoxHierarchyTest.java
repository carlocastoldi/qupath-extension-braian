// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package qupath.ext.braian;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import qupath.lib.objects.*;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.*;
import qupath.lib.roi.interfaces.ROI;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BoundingBoxHierarchyTest {
    private static PathObject createObject(double x, double y, double w, double h) {
        ROI roi = ROIs.createRectangleROI(x, y, w, h, ImagePlane.getDefaultPlane());
        return createObject(PathDetectionObject.class, roi);
    }

    private static <T extends PathObject> PathObject createObject(Class<T> clazz, ROI roi) {
        if (clazz.equals(PathAnnotationObject.class))
            return PathObjects.createAnnotationObject(roi);
        else if (clazz.equals(PathDetectionObject.class))
            return PathObjects.createDetectionObject(roi);
        else if (clazz.equals(PathCellObject.class)) {
            ROI roiNucleus = roi.scale(.75, .75, roi.getCentroidX(), roi.getCentroidY());
            return PathObjects.createCellObject(roi, roiNucleus);
        } else if (clazz.equals(PathTileObject.class))
            return PathObjects.createTileObject(roi);
        // TMACoreObject.class
        throw new IllegalArgumentException("Unsupported "+clazz.getName());
    }

    private static IntStream range(int start, int end, int step) {
        return IntStream.range(0, (end-start)/step).map(x -> start+x*step);
    }

    private static Stream<PathObject> createObjectGrid(int width, int height, int size) {
        Stream<Integer> xs = range(0,width,size).boxed();
        return xs.flatMap(x -> range(0,height,size).mapToObj(y -> createObject(x,y,size,size)));
    }

    @Test
    void negativeDepthBBH() {
        // SETUP
        List<PathObject> objects = List.of(mock(PathObject.class));
        // EXERCISE & VERIFY
        Throwable e = assertThrows(IllegalArgumentException.class,
                () -> new BoundingBoxHierarchy(objects, -1));
        assertEquals("maxDepth must be >1. Instead got maxDepth=-1", e.getMessage());
    }

    @Test
    void zeroDepthBBH() {
        List<PathObject> objects = List.of(mock(PathObject.class));
        Throwable e = assertThrows(IllegalArgumentException.class,
                () -> new BoundingBoxHierarchy(objects, 0));
        assertEquals("maxDepth must be >1. Instead got maxDepth=0", e.getMessage());
    }

    @Test
    void emptyObjectsBBH() {
        Throwable e = assertThrows(IllegalArgumentException.class,
                () -> new BoundingBoxHierarchy(new ArrayList<>(), 6));
        assertEquals("BoundingBoxHierarchy cannot store zero objects", e.getMessage());
    }

    @Test
    void multiplePointsObjectsBBH() {
        ROI pointROI = ROIs.createPointsROI(new double[]{1, 2}, new double[]{1, 2}, ImagePlane.getDefaultPlane());
        PathObject points = createObject(PathAnnotationObject.class, pointROI);
        List<PathObject> objects = List.of(
                createObject(42, 12, 2, 2),
                points,
                createObject(144, 62, 2, 2));
        Throwable e = assertThrows(IllegalArgumentException.class,
                () -> new BoundingBoxHierarchy(objects, 6));
        assertEquals("BoundingBoxHierarchy cannot handle PointsROI objects with multiple points", e.getMessage());
    }

    @Test
    void getDepthOneObject() {
        List<PathObject> objects = Collections.singletonList(createObject(1., 1, 2, 2));
        assertEquals(1, new BoundingBoxHierarchy(objects).getDepth());
        assertEquals(1, new BoundingBoxHierarchy(objects, 1).getDepth());
        assertEquals(1, new BoundingBoxHierarchy(objects, 10).getDepth());
    }

    @Test
    void oneEmptyObject() {
        ROI pointROI = ROIs.createPointsROI(1, 1, ImagePlane.getDefaultPlane());
        PathObject empty = createObject(PathAnnotationObject.class, pointROI);
        List<PathObject> objects = Collections.singletonList(empty);
        BoundingBoxHierarchy bbh = new BoundingBoxHierarchy(objects);

        PathObject cover = createObject(0, 0, 2, 2);
        assertEquals(empty, bbh.getOverlappingObject(cover));
        PathObject bottomEdge = createObject(0, 1, 2, 2);
        assertEquals(empty, bbh.getOverlappingObject(bottomEdge));
        PathObject leftEdge = createObject(1, 0, 2, 2);
        assertEquals(empty, bbh.getOverlappingObject(leftEdge));
        PathObject rightEdge = createObject(-1, 0, 2, 2);
        assertNull(bbh.getOverlappingObject(rightEdge));
        PathObject topEdge = createObject(0, -2, 2, 2);
        assertNull(bbh.getOverlappingObject(topEdge));
        PathObject emptyAreaButOverlapping = createObject(1, 1, 0, 0);
        assertEquals(empty, bbh.getOverlappingObject(emptyAreaButOverlapping));
        PathObject singlePointButOverlapping = createObject(PathAnnotationObject.class, ROIs.createPointsROI(1, 1, ImagePlane.getDefaultPlane()));
        assertEquals(empty, bbh.getOverlappingObject(singlePointButOverlapping));
    }

    @Test
    void twoNearNonOverlappingObjects() {
        PathObject leftPO = spy(createObject(-10, -10, 10, 10));
        PathObject rightPO = spy(createObject(0, -10, 10, 10));
        Collection<PathObject> objects = List.of(leftPO, rightPO);
        BoundingBoxHierarchy bbh = new BoundingBoxHierarchy(objects, 10);

        objects.forEach(o -> verify(o, atLeast(1)).getROI());
        assertEquals(new Rectangle(-10, -10, 20, 10), bbh.getBox());
        assertEquals(1, bbh.getDepth());
        assertEquals(new HashSet<>(objects), bbh.toStream().collect(Collectors.toSet()));
        PathObject noContainsCentroid = createObject(-2, -4, 4, 2);
        assertNull(bbh.getOverlappingObject(noContainsCentroid));
        PathObject closerToLeftCentroid = createObject(-5.5, -10, 10.9, 10);
        assertEquals(leftPO, bbh.getOverlappingObject(closerToLeftCentroid));
        PathObject equidistant = createObject(-10, -10, 20, 20);
        assertTrue(List.of(leftPO, rightPO).contains(bbh.getOverlappingObject(equidistant)));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9})
    void squareObjectGrid(int depth) {
        int n = (int) Math.pow(2, depth);
        int size = 1;
        // creates up to 262144 objects
        Collection<PathObject> objects = createObjectGrid(n, n, size).toList();
        BoundingBoxHierarchy bbh = new BoundingBoxHierarchy(objects, 10);

        assertEquals(depth, bbh.getDepth());
        assertEquals(new Rectangle(0, 0, n*size, n*size), bbh.getBox());
        assertEquals(new HashSet<>(objects), bbh.toStream().collect(Collectors.toSet()));
        ROI bottomLeftCorner = bbh.getOverlappingObject(createObject(0,0, size, size)).getROI();
        assertEquals(bottomLeftCorner.getCentroidX(), (double) size /2);
        assertEquals(bottomLeftCorner.getCentroidY(), (double) size /2);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9})
    void rowObjectGrid(int depth) {
        int n = (int) Math.pow(2, depth);
        int size = 1;
        // creates up to 262144 objects
        Collection<PathObject> objects = createObjectGrid(n, 1, size).toList();
        BoundingBoxHierarchy bbh = new BoundingBoxHierarchy(objects, 10);

        assertEquals(depth, bbh.getDepth());
        assertEquals(new Rectangle(0, 0, n*size, size), bbh.getBox());
        assertEquals(new HashSet<>(objects), bbh.toStream().collect(Collectors.toSet()));
        ROI bottomLeftCorner = bbh.getOverlappingObject(createObject(0,0, size, size)).getROI();
        assertEquals(bottomLeftCorner.getCentroidX(), (double) size /2);
        assertEquals(bottomLeftCorner.getCentroidY(), (double) size /2);
    }

    @Test
    void aboveMaxDepth() {
        int maxDepth = 6;
        int n = (int) Math.pow(2, 10);
        int size = 1;
        // creates up to 262144 objects
        Collection<PathObject> objects = createObjectGrid(n, 1, size).toList();
        BoundingBoxHierarchy bbh = new BoundingBoxHierarchy(objects, maxDepth);

        assertEquals(maxDepth, bbh.getDepth());
        assertEquals(new Rectangle(0, 0, n*size, size), bbh.getBox());
        assertEquals(new HashSet<>(objects), bbh.toStream().collect(Collectors.toSet()));
    }
}