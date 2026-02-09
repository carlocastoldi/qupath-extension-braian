// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
// SPDX-FileCopyrightText: 2025 Nash Baughman <nfbaughman@gmail.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package qupath.ext.braian.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.projects.ProjectIO;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Utilities for discovering QuPath project files on disk.
 */
public final class ProjectDiscoveryService {
    private static final Logger logger = LoggerFactory.getLogger(ProjectDiscoveryService.class);

    private ProjectDiscoveryService() {
    }

    /**
     * Discover QuPath project files in the immediate subdirectories of
     * {@code rootPath}.
     *
     * <p>
     * Discovery rule: include {@code <subdir>/project.<ext>} where {@code <ext>} is
     * {@link ProjectIO#DEFAULT_PROJECT_EXTENSION}.
     *
     * @param rootPath root directory to search
     * @return list of discovered project files, sorted by directory name
     */
    public static List<Path> discoverProjectFiles(Path rootPath) {
        if (rootPath == null || !Files.isDirectory(rootPath)) {
            return List.of();
        }

        String extension = ProjectIO.DEFAULT_PROJECT_EXTENSION;
        List<Path> results = new ArrayList<>();

        try (var dirStream = Files.list(rootPath)) {
            dirStream
                    .filter(Files::isDirectory)
                    .forEach(dir -> {
                        Path projectFile = dir.resolve("project." + extension);
                        if (Files.exists(projectFile)) {
                            results.add(projectFile);
                        }
                    });
        } catch (IOException e) {
            logger.warn("Failed to discover projects under {}", rootPath, e);
            return List.of();
        }

        results.sort(Comparator.comparing(path -> path.getParent().getFileName().toString()));
        return results;
    }
}
