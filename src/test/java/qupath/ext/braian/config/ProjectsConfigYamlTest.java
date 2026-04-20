//
// SPDX-License-Identifier: AGPL-3.0-or-later

package qupath.ext.braian.config;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ProjectsConfigYamlTest {

    @Test
    void shouldLoadPixelClassifiersPerChannel() {
        String yaml = String.join("\n",
                "classForDetections: null",
                "channelDetections:",
                "  - name: \"DAPI\"",
                "    pixelClassifiers:",
                "      - classifierName: \"MyClassifier\"",
                "        measurementId: \"MyMeasurement\"",
                "        regionFilter: [\"Isocortex\", \"OLF\"]",
                ""
        );

        ProjectsConfig config = new Yaml(new Constructor(ProjectsConfig.class, new LoaderOptions())).load(yaml);

        assertNotNull(config);
        assertNotNull(config.getChannelDetections());
        assertEquals(1, config.getChannelDetections().size());

        ChannelDetectionsConfig channel = config.getChannelDetections().get(0);
        assertEquals("DAPI", channel.getName());

        List<PixelClassifierConfig> pixelClassifiers = channel.getPixelClassifiers();
        assertNotNull(pixelClassifiers);
        assertEquals(1, pixelClassifiers.size());

        PixelClassifierConfig pc = pixelClassifiers.get(0);
        assertEquals("MyClassifier", pc.getClassifierName());
        assertEquals("MyMeasurement", pc.getMeasurementId());
        assertEquals(List.of("Isocortex", "OLF"), pc.getRegionFilter());
    }

    @Test
    void missingPixelClassifiersDefaultsToEmptyList() {
        String yaml = String.join("\n",
                "classForDetections: null",
                "channelDetections:",
                "  - name: \"DAPI\"",
                ""
        );

        ProjectsConfig config = new Yaml(new Constructor(ProjectsConfig.class, new LoaderOptions())).load(yaml);

        assertNotNull(config);
        assertNotNull(config.getChannelDetections());
        assertEquals(1, config.getChannelDetections().size());

        ChannelDetectionsConfig channel = config.getChannelDetections().get(0);
        assertNotNull(channel.getPixelClassifiers());
        assertTrue(channel.getPixelClassifiers().isEmpty());
    }
}
