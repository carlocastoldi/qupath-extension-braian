// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package qupath.ext.braian.config;

import qupath.ext.braian.PartialClassifier;
import qupath.ext.braian.utils.BraiAn;
import qupath.lib.classifiers.object.ObjectClassifier;
import qupath.lib.classifiers.object.ObjectClassifiers;
import qupath.lib.io.UriResource;
import qupath.lib.io.UriUpdater;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

import static qupath.lib.scripting.QP.getProject;

public class ChannelClassifierConfig {
    private String name;
    private List<String> annotationsToClassify; // names of the annotations to classify

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getAnnotationsToClassify() {
        return annotationsToClassify;
    }

    public void setAnnotationsToClassify(List<String> annotationsToClassify) {
        this.annotationsToClassify = annotationsToClassify;
    }

    public ObjectClassifier loadClassifier() throws IOException {
        Path classifierPath = BraiAn.resolvePath(this.getName()+".json");
        // inspired by QP.loadObjectClassifier()
        ObjectClassifier classifier = null;
        Exception exception = null;

        try {
            classifier = ObjectClassifiers.readClassifier(classifierPath);
        } catch (Exception e) { exception = e; }
        if (classifier == null)
            throw new IOException("Unable to find object classifier " + classifierPath, exception);

        // Try to fix URIs, if we can
        if (classifier instanceof UriResource)
            UriUpdater.fixUris((UriResource)classifier, getProject());

        return classifier;
    }

    public Collection<PathAnnotationObject> getAnnotationsToClassify(PathObjectHierarchy hierarchy) {
        if(annotationsToClassify == null)
            return null;
        return hierarchy.getAnnotationObjects()
                .stream()
                .filter(annotation -> annotationsToClassify.contains(annotation.getName()))
                .map(annotation -> (PathAnnotationObject) annotation)
                .toList();
    }

    public PartialClassifier toPartialClassifier(PathObjectHierarchy hierarchy) throws IOException {
        return new PartialClassifier(this.loadClassifier(), this.getAnnotationsToClassify(hierarchy));
    }
}
