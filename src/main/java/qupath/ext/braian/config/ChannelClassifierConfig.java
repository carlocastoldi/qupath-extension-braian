// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
// SPDX-FileCopyrightText: 2025 Nash Baughman <nfbaughman@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package qupath.ext.braian.config;

import qupath.ext.braian.ChannelDetections;
import qupath.ext.braian.PartialClassifier;
import qupath.ext.braian.SingleClassifier;
import qupath.ext.braian.utils.BraiAn;
import qupath.lib.classifiers.object.ObjectClassifier;
import qupath.lib.classifiers.object.ObjectClassifiers;
import qupath.lib.io.UriResource;
import qupath.lib.io.UriUpdater;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.projects.Project;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

/**
 * Configuration for applying an object classifier to detections for a specific
 * image channel.
 * <p>
 * This configuration can optionally restrict classification to detections
 * inside annotations whose names
 * are listed in {@link #getAnnotationsToClassify()}.
 *
 * @see PartialClassifier
 * @see qupath.ext.braian.AbstractDetections#applyClassifiers(List,
 *      qupath.lib.images.ImageData)
 */
public class ChannelClassifierConfig {
    private String channel;
    private String name;
    private List<String> annotationsToClassify; // names of the annotations to classify

    /**
     * @return the image channel name this classifier applies to
     */
    public String getChannel() {
        return this.channel;
    }

    /**
     * @param channel the image channel name this classifier applies to
     */
    public void setChannel(String channel) {
        if (channel == null)
            throw new IllegalArgumentException("'channel' must be non-null value.");
        this.channel = channel;
    }

    /**
     * @return the classifier name or identifier (e.g. {@code ALL} or a classifier
     *         filename without extension)
     */
    public String getName() {
        return name;
    }

    /**
     * @param name the classifier name or identifier (e.g. {@code ALL} or a
     *             classifier filename without extension)
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return the names of annotations to restrict classification to; may be null
     *         to classify the full image
     */
    public List<String> getAnnotationsToClassify() {
        return annotationsToClassify;
    }

    /**
     * @param annotationsToClassify the names of annotations to restrict
     *                              classification to; null to classify the full
     *                              image
     */
    public void setAnnotationsToClassify(List<String> annotationsToClassify) {
        this.annotationsToClassify = annotationsToClassify;
    }

    /**
     * Loads the object classifier specified in the configuration file.
     * If the specified name is equals to <code>"ALL"</code>, it returns a
     * {@link SingleClassifier}
     * that classifies all detections as part positive cells from this
     * configuration's image channel.
     * Else, it searches for the a JSON file named after the specified string, as
     * described by
     * {@link BraiAn#resolvePath(String)} and reads it.
     * 
     * @param project the current QuPath project (used to resolve URIs and locate
     *                classifier files)
     * @return an instance of <code>ObjectClassifier</code> loaded based on the
     *         configuration file
     * @throws IOException if any problem raises when reading the JSON file,
     *                     supposedly corresponding to a QuPath
     *                     object classifier.
     */
    public <T> ObjectClassifier<T> loadClassifier(Project<?> project) throws IOException {
        if (this.getName().equalsIgnoreCase("all"))
            return new SingleClassifier<>(ChannelDetections.createClassification(this.channel));
        Path classifierPath = BraiAn.resolvePath(project, this.getName() + ".json");
        // inspired by QP.loadObjectClassifier()
        ObjectClassifier<T> classifier = null;
        Exception exception = null;

        try {
            classifier = ObjectClassifiers.readClassifier(classifierPath);
        } catch (Exception e) {
            exception = e;
        }
        if (classifier == null)
            throw new IOException("Unable to find object classifier " + classifierPath, exception);

        // Try to fix URIs, if we can
        if (classifier instanceof UriResource && project != null)
            UriUpdater.fixUris((UriResource) classifier, project);

        return classifier;
    }

    /**
     * It searches all annotations specified by their name in the configuration
     * file. If a name does not correspond
     * to any annotation in the current hierarchy, it silently skips it.
     * 
     * @param hierarchy where to search the annotations in
     * @return a collection of annotations, if they were specified in the
     *         configuration file.
     *         Otherwise, <code>null</code>.
     */
    public Collection<PathAnnotationObject> getAnnotationsToClassify(PathObjectHierarchy hierarchy) {
        if (annotationsToClassify == null)
            return null;
        return hierarchy.getAnnotationObjects()
                .stream()
                .filter(annotation -> annotationsToClassify.contains(annotation.getName()))
                .map(annotation -> (PathAnnotationObject) annotation)
                .toList();
    }

    /**
     * Loads the classifier and associates it to the annotations whose name is
     * listed in the configuration file.
     *
     * @param hierarchy where to search the annotations in
     * @param project   the current QuPath project (used to resolve URIs and locate
     *                  classifier files)
     * @return an instance of {@link PartialClassifier}
     * @throws IOException if the classifier cannot be loaded
     * @see #loadClassifier(Project)
     * @see #getAnnotationsToClassify(PathObjectHierarchy)
     */
    public <T> PartialClassifier<T> toPartialClassifier(PathObjectHierarchy hierarchy, Project<?> project)
            throws IOException {
        ObjectClassifier<T> classifier = this.loadClassifier(project);
        return new PartialClassifier<>(classifier, this.getAnnotationsToClassify(hierarchy));
    }
}
