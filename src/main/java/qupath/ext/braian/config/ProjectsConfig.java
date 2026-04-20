// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package qupath.ext.braian.config;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.DumperOptions;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
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
    public static ProjectsConfig read(String yamlFileName) throws IOException, YAMLException {
        Path filePath = BraiAn.resolvePath(yamlFileName);
        return read(filePath);
    }

    public static ProjectsConfig read(Path filePath) throws IOException, YAMLException {
        getLogger().info("using '{}' configuration file.", filePath);
        String configStream = Files.readString(filePath, StandardCharsets.UTF_8);

        try {
            Constructor c = new Constructor(ProjectsConfig.class, new LoaderOptions());
            return new Yaml(c).load(configStream);
        } catch (YAMLException e) {
            getLogger().error("Could not interpret the file '{}'. Please check that it is correctly formatted!",
                    filePath);
            throw e;
        }
    }

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

    public String getClassForDetections() {
        return classForDetections;
    }

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

    public String getAtlasName() {
        return atlasName;
    }

    public void setAtlasName(String atlasName) {
        this.atlasName = atlasName;
    }

    public DetectionsCheckConfig getDetectionsCheck() {
        return detectionsCheck;
    }

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

    public List<ChannelDetectionsConfig> getChannelDetections() {
        return channelDetections;
    }

    public void setChannelDetections(List<ChannelDetectionsConfig> channelDetections) {
        this.channelDetections = channelDetections;
    }
}
