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
 * Minimal object classifier that assigns a single {@link PathClass} to all input detections.
 *
 * @param <T> image pixel type used by QuPath
 */
public class SingleClassifier<T> extends AbstractObjectClassifier<T> {
    private final PathClass pathClass;
    private final List<PathClass> pathClasses;

    /**
     * Creates a classifier that assigns (or merges) the provided class.
     *
     * @param pathClass class to apply to detections
     */
    public SingleClassifier(PathClass pathClass) {
        super(PathObjectFilter.DETECTIONS_ALL);
        this.pathClass = pathClass;
        this.pathClasses = Collections.unmodifiableList(List.of(pathClass));
    }

    @Override
    public Collection<PathClass> getPathClasses() {
        return pathClasses;
    }

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

    @Override
    public Map<String, Integer> getMissingFeatures(ImageData<T> imageData, Collection<? extends PathObject> pathObjects) {
        return Collections.emptyMap();
    }
}
