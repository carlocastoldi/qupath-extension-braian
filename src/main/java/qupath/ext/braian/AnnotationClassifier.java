// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package qupath.ext.braian;

import qupath.lib.classifiers.object.ObjectClassifier;
import qupath.lib.objects.PathAnnotationObject;

import java.util.Collection;

public class AnnotationClassifier {
    private final ObjectClassifier classifier;
    private final Collection<PathAnnotationObject> annotations;

    public AnnotationClassifier(ObjectClassifier classifier, Collection<PathAnnotationObject> annotations) {
        this.classifier = classifier;
        this.annotations = annotations;
    }

    public boolean isPartial() {
        return this.annotations != null;
    }

    public ObjectClassifier getClassifier() {
        return classifier;
    }

    public Collection<PathAnnotationObject> getAnnotations() {
        return annotations;
    }
}