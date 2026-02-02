// SPDX-FileCopyrightText: 2018 - 2020 QuPath developers, The University of Edinburgh
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package qupath.ext.braian;

import qupath.lib.classifiers.object.AbstractObjectClassifier;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectFilter;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassTools;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Simple {@link qupath.lib.classifiers.object.ObjectClassifier} that assigns a single {@link PathClass}.
 * <p>
 * If an object already has a class, the new class is merged using {@link PathClassTools#mergeClasses(PathClass, PathClass)}.
 *
 * @param <T> the pixel type handled by the associated {@link ImageData}
 */
public class SingleClassifier<T> extends AbstractObjectClassifier<T> {
    private final PathClass pathClass;
    private final List<PathClass> pathClasses;

    /**
     * Creates a classifier that assigns (or merges) a single {@link PathClass}.
     *
     * @param pathClass the {@link PathClass} to assign
     */
    public SingleClassifier(PathClass pathClass) {
        super(PathObjectFilter.DETECTIONS_ALL);
        this.pathClass = pathClass;
        this.pathClasses = Collections.unmodifiableList(List.of(pathClass));
    }

    /**
     * @return the (single) {@link PathClass} this classifier can assign
     */
    @Override
    public Collection<PathClass> getPathClasses() {
        return pathClasses;
    }

    /**
     * Assigns the configured {@link PathClass} to every object.
     *
     * @param imageData the image data containing the objects
     * @param pathObjects objects to classify
     * @param resetExistingClass if true, clears existing classifications before applying the new one
     * @return the number of objects whose class changed
     */
    @Override
    public int classifyObjects(ImageData<T> imageData, Collection<? extends PathObject> pathObjects, boolean resetExistingClass) {
        int n = 0;
        for (var pathObject : pathObjects) {
            var previousClass = pathObject.getPathClass();
            if (resetExistingClass)
                pathObject.resetPathClass();

            if (this.pathClass != null) {
                var currentClass = pathObject.getPathClass();
                if (currentClass == null)
                    pathObject.setPathClass(this.pathClass);
                else
                    pathObject.setPathClass(
                            PathClassTools.mergeClasses(currentClass, this.pathClass)
                    );
            }
            if (previousClass != pathObject.getPathClass())
                n++;
        }
        return n;
    }

    /**
     * @return an empty map since this classifier does not require any features
     */
    @Override
    public Map<String, Integer> getMissingFeatures(ImageData<T> imageData, Collection<? extends PathObject> pathObjects) {
        return Collections.emptyMap();
    }
}
