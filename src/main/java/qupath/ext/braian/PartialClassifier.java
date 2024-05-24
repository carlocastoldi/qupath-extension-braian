// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package qupath.ext.braian;

import qupath.lib.classifiers.object.ObjectClassifier;
import qupath.lib.objects.PathAnnotationObject;

import java.util.Collection;

public record PartialClassifier(ObjectClassifier<?> classifier, Collection<PathAnnotationObject> annotations) {
    public boolean coversFullImage() {
        return this.annotations == null;
    }
}
