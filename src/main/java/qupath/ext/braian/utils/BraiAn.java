// SPDX-FileCopyrightText: 2024 Carlo Castoldi <carlo.castoldi@outlook.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package qupath.ext.braian.utils;

import qupath.fx.utils.FXUtils;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.projects.Project;
import qupath.lib.projects.Projects;

import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class BraiAn {
    public static Optional<Path> resolvePathIfPresent(Project<?> project, String fileName) {
        if (project == null) {
            return Optional.empty();
        }
        Path projectPath = Projects.getBaseDirectory(project).toPath();
        Path projectParentDirectoryPath = projectPath.getParent();
        Path[] resolutionOrder = { projectPath, projectParentDirectoryPath };
        for (Path path : resolutionOrder) {
            if (path == null)
                continue;
            Path filePath = path.resolve(fileName);
            if (Files.exists(filePath))
                return Optional.of(filePath);
        }
        return Optional.empty();
    }

    /**
     * It searches for a file accordingly to BraiAn specifics: it first searches it
     * in the project's path;
     * if it is not present, it searches it in the parent directory, were supposedly
     * other QuPath projects of the
     * same experiment reside.
     * 
     * @param fileName the name of the file to search accordingly to BraiAn
     * @return the complete path to <code>fileName</code>.
     * @throws FileNotFoundException if no file named <code>fileName</code> was
     *                               found.
     */
    public static Path resolvePath(Project<?> project, String fileName) throws FileNotFoundException {
        return resolvePathIfPresent(project, fileName)
                .orElseThrow(() -> new FileNotFoundException("Can't find the specified file: '" + fileName + "'"));
    }

    public static void populatePathClassGUI(Project<?> project, QuPathGUI qupath, PathClass... toAdd) {
        if (project == null || toAdd == null || toAdd.length == 0) {
            return;
        }
        List<PathClass> visibleClasses = new ArrayList<>(project.getPathClasses());
        List<PathClass> missingClasses = Arrays.stream(toAdd)
                .filter(classification -> !visibleClasses.contains(classification))
                .toList();
        if (missingClasses.isEmpty()) {
            return;
        }
        visibleClasses.addAll(missingClasses);
        project.setPathClasses(visibleClasses);
        if (qupath != null) {
            FXUtils.runOnApplicationThread(() -> qupath.getAvailablePathClasses().setAll(visibleClasses));
        }
    }

    public static <T> String join(Collection<T> c, String delimiter) {
        if (c.isEmpty())
            return "";
        StringBuilder classesStr = new StringBuilder();
        List<T> l = c instanceof List ? (List<T>) c : new ArrayList<>(c);
        for (int i = 0; i < l.size() - 1; i++) {
            T o = l.get(i);
            classesStr.append(o).append(delimiter);
        }
        classesStr.append(l.get(l.size() - 1));
        return classesStr.toString();
    }
}
