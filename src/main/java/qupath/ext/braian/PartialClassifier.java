// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package qupath.ext.braian;

import qupath.lib.classifiers.object.ObjectClassifier;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathAnnotationObject;

import java.util.Collection;
import java.util.List;

/**
 * Creates a classifier to apply to an instance of {@link AbstractDetections}
 * @param classifier an object classifier
 * @param annotations the annotations to which contained detection solely apply the <code>classifier</code>.
 *                    If <code>null</code>, it applies it to the whole image.
 * @see AbstractDetections#applyClassifiers(List, ImageData)
 */
public record PartialClassifier(ObjectClassifier<?> classifier, Collection<PathAnnotationObject> annotations) {
    /**
     * @return true, if the partial classifier covers the whole whole image, thus causing all detections to be classified
     */
    public boolean coversFullImage() {
        return this.annotations == null;
    }
}
