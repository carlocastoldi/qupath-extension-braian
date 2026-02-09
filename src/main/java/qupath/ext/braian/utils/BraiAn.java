// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package qupath.ext.braian.utils;

import qupath.fx.utils.FXUtils;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.projects.Projects;

import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static qupath.lib.scripting.QP.getProject;

/**
 * Utility helpers shared across BraiAn scripts, config loading, and UI workflows.
 */
public class BraiAn {
    /**
     * Resolves a file by checking project directory first, then parent directory.
     *
     * @param fileName file name to resolve
     * @return optional resolved path if file exists
     */
    public static Optional<Path> resolvePathIfPresent(String fileName) {
        Path projectPath = Projects.getBaseDirectory(getProject()).toPath();
        Path projectParentDirectoryPath = projectPath.getParent();
        Path[] resolutionOrder = {projectPath, projectParentDirectoryPath};
        for (Path path: resolutionOrder) {
            Path filePath = path.resolve(fileName);
            if (Files.exists(filePath))
                return Optional.of(filePath);
        }
        return Optional.empty();
    }

    /**
     * It searches for a file accordingly to BraiAn specifics: it first searches it in the project's path;
     * if it is not present, it searches it in the parent directory, were supposedly other QuPath projects of the
     * same experiment reside.
     * @param fileName the name of the file to search accordingly to BraiAn
     * @return the complete path to <code>fileName</code>.
     * @throws FileNotFoundException if no file named <code>fileName</code> was found.
     */
    public static Path resolvePath(String fileName) throws FileNotFoundException {
        return resolvePathIfPresent(fileName)
                .orElseThrow(() -> new FileNotFoundException("Can't find the specified file: '"+fileName+"'"));
    }

    /**
     * Ensures custom {@link PathClass} entries are visible in the QuPath class list UI.
     *
     * @param toAdd classes to add when missing
     */
    public static void populatePathClassGUI(PathClass... toAdd) {
        List<PathClass> visibleClasses = new ArrayList<>(getProject().getPathClasses());
        List<PathClass> missingClasses = Arrays.stream(toAdd)
                .filter(classification -> !visibleClasses.contains(classification))
                .toList();
        visibleClasses.addAll(missingClasses);
        var qupathGUI = QuPathGUI.getInstance();
        if (qupathGUI != null)
            FXUtils.runOnApplicationThread(() ->
                qupathGUI.getAvailablePathClasses().setAll(visibleClasses)
            );
    }

    /**
     * Joins any collection into a delimiter-separated string.
     *
     * @param c collection to stringify
     * @param delimiter separator between elements
     * @return joined string, or empty string for empty input
     * @param <T> element type
     */
    public static <T> String join(Collection<T> c, String delimiter) {
        if (c.isEmpty())
            return "";
        StringBuilder classesStr = new StringBuilder();
        List<T> l = c instanceof List ? (List<T>) c : new ArrayList<>(c);
        for (int i = 0; i < l.size()-1; i++) {
            T o = l.get(i);
            classesStr.append(o).append(delimiter);
        }
        classesStr.append(l.get(l.size()-1));
        return classesStr.toString();
    }
}
