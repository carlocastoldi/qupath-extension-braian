// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package qupath.ext.braian.config;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.error.YAMLException;
import qupath.ext.braian.BraiAnExtension;
import qupath.ext.braian.utils.BraiAn;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class ProjectsConfigFile {
    public static ProjectsConfigFile read(String yamlFileName) throws IOException {
        Path filePath = BraiAn.resolvePath(yamlFileName);
        BraiAnExtension.getLogger().info("using '"+filePath+"' configuration file.");
        String configStream = Files.readString(filePath, StandardCharsets.UTF_8);

        try {
            Constructor c = new Constructor(ProjectsConfigFile.class, new LoaderOptions());
            return new Yaml(c).load(configStream);
        } catch (YAMLException e) {
            throw new RuntimeException("Could not parse the file '"+filePath+"'.\n" +
                    "Please check that it is correctly formatted!\n\n" +
                    e.getMessage());
        }
    }

    private String classForDetections;
    private Map<String,?> detectionsCheck = Map.of("apply", false);
    private List<ChannelDetectionsConfig> channelDetections;

    public String getClassForDetections() {
        return classForDetections;
    }

    public Collection<PathAnnotationObject> getAnnotationsForDetections(PathObjectHierarchy hierarchy) {
        String classForDetections = this.getClassForDetections();
        if(classForDetections == null)
            return null;
        return hierarchy.getAnnotationObjects()
                .stream()
                .filter(annotation -> classForDetections.equals(annotation.getPathClass().getName()))
                //.filter(annotation -> classForDetections.equals(annotation.getName()))
                .map(annotation -> (PathAnnotationObject) annotation)
                .toList();
    }

    public void setClassForDetections(String classForDetections) {
        this.classForDetections = classForDetections;
    }

    public Map<String,?> getDetectionsCheck() {
        return detectionsCheck;
    }

    public void setDetectionsCheck(Map<String,?> detectionsCheck) {
        this.detectionsCheck = detectionsCheck;
    }

    public List<ChannelDetectionsConfig> getChannelDetections() {
        return channelDetections;
    }

    public void setChannelDetections(List<ChannelDetectionsConfig> channelDetections) {
        this.channelDetections = channelDetections;
    }
}
