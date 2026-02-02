// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
// SPDX-FileCopyrightText: 2025 Nash Baughman <nfbaughman@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package qupath.ext.braian.config;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.introspector.BeanAccess;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.introspector.PropertyUtils;
import org.yaml.snakeyaml.representer.Representer;
import qupath.ext.braian.utils.BraiAn;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.projects.Project;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static qupath.ext.braian.BraiAnExtension.getLogger;

/**
 * This class reads a YAML file and can be used to apply the given parameters to
 * the computation offered by BraiAn extension
 * For more information, read <a href=
 * "https://github.com/carlocastoldi/qupath-extension-braian/blob/master/BraiAn.yml">this
 * config file example</a>.
 */
public class ProjectsConfig {
    /**
     * Reads a BraiAn configuration file
     * 
     * @param project      the current QuPath project
     * @param yamlFileName the name of the file, not the path.
     *                     It will then search it first into the project's directory
     *                     and, if it wasn't there, in its parent directory.
     *                     If it cannot still find it, it throws
     *                     {@link java.io.FileNotFoundException}
     * @return an instance of <code>ProjectsConfig</code>
     * @throws IOException   if it found the config file, but it had problems while
     *                       reading it.
     * @throws YAMLException if it found and read the config file, but it was badly
     *                       formatted.
     */
    public static ProjectsConfig read(Project<?> project, String yamlFileName) throws IOException, YAMLException {
        Path filePath = BraiAn.resolvePath(project, yamlFileName);
        return read(filePath);
    }

    /**
     * Reads a BraiAn configuration file from a full path.
     *
     * @param filePath the path to the YAML configuration file
     * @return an instance of {@link ProjectsConfig}
     * @throws IOException   if it found the config file, but it had problems while
     *                       reading it
     * @throws YAMLException if it found and read the config file, but it was badly
     *                       formatted
     * @see #read(Project, String)
     */
    public static ProjectsConfig read(Path filePath) throws IOException, YAMLException {
        getLogger().info("using '{}' configuration file.", filePath);
        String configStream = Files.readString(filePath, StandardCharsets.UTF_8);

        try {
            Constructor constructor = new Constructor(ProjectsConfig.class, new LoaderOptions());
            PropertyUtils propertyUtils = new PropertyUtils() {
                @Override
                public Property getProperty(Class<?> type, String name, BeanAccess bAccess) {
                    boolean known = true;
                    try {
                        Map<String, Property> properties = getPropertiesMap(type, bAccess);
                        known = properties.containsKey(name);
                    } catch (Exception e) {
                        known = true;
                    }
                    if (!known) {
                        getLogger().warn("Skipping unknown YAML property '{}' for {}", name, type.getSimpleName());
                    }
                    return super.getProperty(type, name, bAccess);
                }
            };
            propertyUtils.setSkipMissingProperties(true);
            constructor.setPropertyUtils(propertyUtils);
            return new Yaml(constructor).load(configStream);
        } catch (YAMLException e) {
            String message = "Could not interpret " + filePath.getFileName()
                    + ". The format may be outdated. Please DELETE or RENAME this file to allow it to be regenerated.";
            getLogger().error(message, e);
            throw new YAMLException(message, e);
        }
    }

    /**
     * Serializes a {@link ProjectsConfig} instance as YAML.
     *
     * @param config the configuration to serialize
     * @return a YAML representation of the configuration
     */
    public static String toYaml(ProjectsConfig config) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        options.setIndicatorIndent(0);
        options.setDefaultScalarStyle(DumperOptions.ScalarStyle.PLAIN);

        Representer representer = new Representer(options);
        PropertyUtils propertyUtils = new PropertyUtils() {
            @Override
            protected java.util.Set<Property> createPropertySet(Class<?> type, BeanAccess bAccess) {
                var properties = super.createPropertySet(type, bAccess);
                if (type == WatershedCellDetectionConfig.class) {
                    properties.removeIf(property -> "detectionImage".equals(property.getName()));
                }
                return properties;
            }
        };
        propertyUtils.setSkipMissingProperties(true);
        representer.setPropertyUtils(propertyUtils);
        representer.getPropertyUtils().setSkipMissingProperties(true);

        Yaml yaml = new Yaml(representer, options);
        return yaml.dumpAsMap(config);
    }

    private String classForDetections = null;
    private String atlasName = "allen_mouse_10um_java";
    private DetectionsCheckConfig detectionsCheck = new DetectionsCheckConfig();
    private List<ChannelDetectionsConfig> channelDetections = List.of();

    /**
     * @return the {@link qupath.lib.objects.classes.PathClass} name used to select
     *         annotations for running detections
     */
    public String getClassForDetections() {
        return classForDetections;
    }

    /**
     * @param classForDetections the {@link qupath.lib.objects.classes.PathClass}
     *                           name used to select annotations for running
     *                           detections
     */
    public void setClassForDetections(String classForDetections) {
        this.classForDetections = classForDetections;
    }

    /**
     * Finds (or creates) the annotations chosen for computing the detections,
     * accordingly to the configuration file.
     * It reads the value of 'classForDetections' in the YAML and searches all
     * annotations having the appointed classification
     * 
     * @param hierarchy where to search the annotations in
     * @return the annotations to be used for computing the detections
     * @see qupath.ext.braian.ChannelDetections
     */
    public Collection<PathAnnotationObject> getAnnotationsForDetections(PathObjectHierarchy hierarchy) {
        String classForDetections = this.getClassForDetections();
        if (classForDetections == null)
            return null;
        return hierarchy.getAnnotationObjects()
                .stream()
                .filter(annotation -> annotation.getPathClass() != null
                        && classForDetections.equals(annotation.getPathClass().getName()))
                // .filter(annotation -> classForDetections.equals(annotation.getName()))
                .map(annotation -> (PathAnnotationObject) annotation)
                .toList();
    }

    /**
     * @return the ABBA atlas identifier used during atlas import
     */
    public String getAtlasName() {
        return atlasName;
    }

    /**
     * @param atlasName the ABBA atlas identifier used during atlas import
     */
    public void setAtlasName(String atlasName) {
        this.atlasName = atlasName;
    }

    /**
     * @return configuration for overlap checks across channels
     */
    public DetectionsCheckConfig getDetectionsCheck() {
        return detectionsCheck;
    }

    /**
     * @param detectionsCheck configuration for overlap checks across channels
     */
    public void setDetectionsCheck(DetectionsCheckConfig detectionsCheck) {
        this.detectionsCheck = detectionsCheck;
    }

    /**
     * Retrieves the name of the channel to be used as control in the overlapping
     * 
     * @return an empty optional if no overlapping is desired or if there there is
     *         only one image channel to compute the detections for
     * @see qupath.ext.braian.OverlappingDetections
     */
    public Optional<String> getControlChannel() {
        if (!this.detectionsCheck.getApply() || this.channelDetections.size() < 2) // if there is only one channel with
                                                                                   // detections, it is useless to have
                                                                                   // a controlChannel
            return Optional.empty();
        String name = this.detectionsCheck.getControlChannel();
        if (name == null)
            name = this.channelDetections.get(0).getName();
        return Optional.of(name);
    }

    /**
     * @return the per-channel configurations
     */
    public List<ChannelDetectionsConfig> getChannelDetections() {
        return channelDetections;
    }

    /**
     * @param channelDetections the per-channel configurations
     */
    public void setChannelDetections(List<ChannelDetectionsConfig> channelDetections) {
        this.channelDetections = channelDetections;
    }

}
